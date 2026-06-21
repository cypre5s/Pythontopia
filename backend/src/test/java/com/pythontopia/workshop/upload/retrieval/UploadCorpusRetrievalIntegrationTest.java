package com.pythontopia.workshop.upload.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.learner.memory.EmbeddingClient;
import com.pythontopia.workshop.upload.retrieval.UploadChunkRepository.ChunkRow;
import com.pythontopia.workshop.upload.retrieval.dto.RetrievalHit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 个人语料检索的真库（pgvector）集成测试，参照 {@code MemoryRetrievalSemanticIntegrationTest} 范式。
 *
 * <p>本测试<strong>独立于全量 Flyway</strong>：B 通路解析表 {@code workshop_upload}（V162，由 B-backend-parse
 * 落地）此刻可能尚未存在，故这里仅手工建 {@code users} + 最小 {@code workshop_upload}（列名按契约 §3.1 冻结），
 * 再应用真实的 {@code V163} 迁移 SQL，验证迁移 DDL 与仓储/服务一体可用，且不依赖他人产物即可自测跑绿。
 *
 * <p>覆盖：余弦排序与 user scoped、uploadIds 过滤、ILIKE 字面回退（含通配符转义）、端到端「分块→向量化→入库
 * →检索」、向量化离线 fail-soft 回退、重复触发幂等。
 */
@Testcontainers
class UploadCorpusRetrievalIntegrationTest {

  private static final int DIM = 2048;
  private static final String MODEL = "pythontopia-rag/embed";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static JdbcUploadChunkRepository repository;

  @BeforeAll
  static void schema() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setUrl(POSTGRES.getJdbcUrl());
    ds.setUsername(POSTGRES.getUsername());
    ds.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(ds);

    jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
    // 最小 users（V163 user_id FK 目标）。
    jdbc.execute("CREATE TABLE IF NOT EXISTS users (id BIGSERIAL PRIMARY KEY, username TEXT)");
    // 最小 workshop_upload（列名按契约 §3.1 冻结；真实 V162 由 B-backend-parse 落地）。
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS workshop_upload ("
            + "id BIGSERIAL PRIMARY KEY, user_id BIGINT, filename TEXT, ext TEXT, "
            + "byte_size BIGINT, extracted_text TEXT, char_count INT, line_count INT, "
            + "status TEXT, error TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now())");
    jdbc.execute("CREATE TABLE IF NOT EXISTS learning_event_log (id BIGSERIAL PRIMARY KEY)");
    // 应用真实向量相关迁移 SQL（顺带验证迁移 DDL 正确）。
    jdbc.execute(readMigration("db/migration/V141__memory_embedding.sql"));
    jdbc.execute(readMigration("db/migration/V163__workshop_upload_chunk.sql"));
    jdbc.execute(readMigration("db/migration/V175__embedding_halfvec_2048.sql"));

    repository = new JdbcUploadChunkRepository(jdbc);
  }

  @BeforeEach
  void clean() {
    jdbc.update("TRUNCATE workshop_upload_chunk, workshop_upload, users RESTART IDENTITY CASCADE");
  }

  @Test
  void cosineRanksBySimilarityAndScopesByUser() {
    long userA = insertUser("a");
    long userB = insertUser("b");
    long uploadA = insertUpload(userA, "a.py", "for 循环笔记");
    long uploadB = insertUpload(userB, "b.py", "他人笔记");
    repository.replaceChunks(
        uploadA,
        List.of(
            new ChunkRow(uploadA, userA, 0, "loops chunk", axis(0), MODEL), // 与查询同向 → 1.0
            new ChunkRow(uploadA, userA, 1, "mixed chunk", mix(0.8, 0.6), MODEL))); // → 0.8
    repository.replaceChunks(
        uploadB, List.of(new ChunkRow(uploadB, userB, 0, "他人 chunk", axis(0), MODEL)));

    List<RetrievalHit> hits = repository.searchByCosine(userA, axis(0), List.of(), 10);

    assertThat(hits).extracting(RetrievalHit::chunkIndex).containsExactly(0, 1);
    assertThat(hits).extracting(RetrievalHit::uploadId).containsOnly(uploadA); // userB 被隔离
    assertThat(hits.get(0).filename()).isEqualTo("a.py"); // JOIN workshop_upload 取文件名
    assertThat(hits.get(0).score()).isCloseTo(1.0, Offset.offset(1e-4));
    assertThat(hits.get(1).score()).isCloseTo(0.8, Offset.offset(5e-4));
  }

  @Test
  void cosineRestrictsToRequestedUploadIds() {
    long userA = insertUser("a");
    long upload1 = insertUpload(userA, "f1.py", "x");
    long upload2 = insertUpload(userA, "f2.py", "y");
    repository.replaceChunks(
        upload1, List.of(new ChunkRow(upload1, userA, 0, "chunk one", axis(0), MODEL)));
    repository.replaceChunks(
        upload2, List.of(new ChunkRow(upload2, userA, 0, "chunk two", axis(0), MODEL)));

    List<RetrievalHit> hits = repository.searchByCosine(userA, axis(0), List.of(upload2), 10);

    assertThat(hits).extracting(RetrievalHit::uploadId).containsExactly(upload2);
  }

  @Test
  void literalFallbackMatchesSubstringScopedByUserWithWildcardEscape() {
    long userA = insertUser("a");
    long userB = insertUser("b");
    long uploadA = insertUpload(userA, "notes.md", "x");
    long uploadB = insertUpload(userB, "b.md", "x");
    // embedding 留空（向量化失败场景），仅能字面命中。
    repository.replaceChunks(
        uploadA,
        List.of(
            new ChunkRow(uploadA, userA, 0, "Python 列表推导式很好用", null, null),
            new ChunkRow(uploadA, userA, 1, "value a_b literal", null, null),
            new ChunkRow(uploadA, userA, 2, "value axb literal", null, null)));
    repository.replaceChunks(
        uploadB, List.of(new ChunkRow(uploadB, userB, 0, "他人也提到列表", null, null)));

    List<RetrievalHit> listHits = repository.searchByLiteral(userA, "列表", List.of(), 10);
    assertThat(listHits).hasSize(1);
    assertThat(listHits.get(0).uploadId()).isEqualTo(uploadA); // userB 被隔离
    assertThat(listHits.get(0).text()).contains("列表");
    assertThat(listHits.get(0).score()).isEqualTo(0.0); // 字面命中哨兵分

    // 下划线作为字面而非通配：只命中 "a_b"，不命中 "axb"。
    List<RetrievalHit> escaped = repository.searchByLiteral(userA, "a_b", List.of(), 10);
    assertThat(escaped).hasSize(1);
    assertThat(escaped.get(0).text()).contains("a_b");
  }

  @Test
  void indexThenRetrieveRoundTripsThroughPgvector() {
    EmbeddingClient stub = new DeterministicEmbeddingClient();
    UploadCorpusIndexingService indexer =
        new UploadCorpusIndexingService(jdbc, new UploadChunker(), repository, stub);
    UploadRetrievalService retriever = new UploadRetrievalService(stub, repository);

    long userA = insertUser("a");
    String text = "Python for 循环遍历列表"; // 短文 → 单块，块文本 == 该字符串
    long upload = insertUpload(userA, "guide.md", text);

    int written = indexer.indexUpload(upload);
    assertThat(written).isEqualTo(1);
    assertThat(repository.countByUploadId(upload)).isEqualTo(1);

    List<RetrievalHit> hits = retriever.retrieve(userA, text, null, 3);
    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).filename()).isEqualTo("guide.md");
    assertThat(hits.get(0).text()).isEqualTo(text);
    assertThat(hits.get(0).score()).isCloseTo(1.0, Offset.offset(1e-4)); // 同向量 → 余弦 1

    // 重复触发幂等：不产生重复块。
    indexer.indexUpload(upload);
    assertThat(repository.countByUploadId(upload)).isEqualTo(1);
  }

  @Test
  void indexIsFailSoftWhenEmbeddingOfflineAndRetrievalFallsBackToLiteral() {
    EmbeddingClient offline = new OfflineEmbeddingClient();
    UploadCorpusIndexingService indexer =
        new UploadCorpusIndexingService(jdbc, new UploadChunker(), repository, offline);
    UploadRetrievalService retriever = new UploadRetrievalService(offline, repository);

    long userA = insertUser("a");
    long upload = insertUpload(userA, "off.md", "异常处理 try except 入门");

    int written = indexer.indexUpload(upload);
    assertThat(written).isEqualTo(1); // 向量化失败仍入库（fail-soft）
    Integer withVector =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workshop_upload_chunk "
                + "WHERE upload_id = ? AND embedding IS NOT NULL",
            Integer.class,
            upload);
    assertThat(withVector).isZero(); // embedding 留空

    List<RetrievalHit> hits = retriever.retrieve(userA, "try except", null, 3);
    assertThat(hits).hasSize(1); // 余弦不可用 → 字面回退命中
    assertThat(hits.get(0).text()).contains("try except");
    assertThat(hits.get(0).score()).isEqualTo(0.0);
  }

  private long insertUser(String username) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO users (username) VALUES (?) RETURNING id",
            Long.class,
            "upload-corpus-" + username);
    return id == null ? 0L : id;
  }

  private long insertUpload(long userId, String filename, String extractedText) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO workshop_upload (user_id, filename, extracted_text, status) "
                + "VALUES (?, ?, ?, 'READY') RETURNING id",
            Long.class,
            userId,
            filename,
            extractedText);
    return id == null ? 0L : id;
  }

  private static float[] axis(int index) {
    float[] vector = new float[DIM];
    vector[index] = 1.0f;
    return vector;
  }

  /** 仅前两维非零（调用方保证 a0² + a1² = 1 以便断言余弦值）。 */
  private static float[] mix(double a0, double a1) {
    float[] vector = new float[DIM];
    vector[0] = (float) a0;
    vector[1] = (float) a1;
    return vector;
  }

  private static String readMigration(String classpath) {
    try {
      return new String(
          new ClassPathResource(classpath).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** 确定性 2048 维单位向量：同文本 → 同向量（余弦 1），用于验证端到端向量往返。 */
  private static final class DeterministicEmbeddingClient implements EmbeddingClient {
    @Override
    public Optional<float[]> embed(String text) {
      if (text == null || text.isBlank()) {
        return Optional.empty();
      }
      float[] vector = new float[DIM];
      for (int i = 0; i < text.length(); i++) {
        vector[Math.floorMod(text.charAt(i), DIM)] += 1f;
      }
      double norm = 0;
      for (float value : vector) {
        norm += value * value;
      }
      norm = Math.sqrt(norm);
      if (norm > 0) {
        for (int i = 0; i < DIM; i++) {
          vector[i] /= (float) norm;
        }
      }
      return Optional.of(vector);
    }

    @Override
    public List<Optional<float[]>> embedBatch(List<String> texts) {
      return texts.stream().map(this::embed).toList();
    }
  }

  /** 向量化离线：恒返回空（fail-soft 路径）。 */
  private static final class OfflineEmbeddingClient implements EmbeddingClient {
    @Override
    public Optional<float[]> embed(String text) {
      return Optional.empty();
    }

    @Override
    public List<Optional<float[]>> embedBatch(List<String> texts) {
      return texts.stream().map(text -> Optional.<float[]>empty()).toList();
    }
  }
}
