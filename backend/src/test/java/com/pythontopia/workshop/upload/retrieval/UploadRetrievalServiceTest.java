package com.pythontopia.workshop.upload.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.memory.EmbeddingClient;
import com.pythontopia.workshop.upload.retrieval.dto.RetrievalHit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link UploadRetrievalService} 单测：语义优先 + 字面回退、user scoped 透传、topK clamp、向量维度护栏。
 */
class UploadRetrievalServiceTest {

  private static final int DIM = 2048;

  private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
  private final UploadChunkRepository repository = mock(UploadChunkRepository.class);
  private final UploadRetrievalService service =
      new UploadRetrievalService(embeddingClient, repository);

  private static RetrievalHit hit(double score) {
    return new RetrievalHit(1L, "a.py", 0, "for 循环", score);
  }

  private static float[] vec2048() {
    float[] v = new float[DIM];
    v[0] = 1f;
    return v;
  }

  @Test
  void blankQueryShortCircuitsWithoutTouchingDependencies() {
    assertThat(service.retrieve(1L, "   ", null, 5)).isEmpty();

    verifyNoInteractions(embeddingClient, repository);
  }

  @Test
  void usesCosineWhenQueryVectorAvailable() {
    when(embeddingClient.embed("for循环")).thenReturn(Optional.of(vec2048()));
    when(repository.searchByCosine(eq(9L), any(float[].class), eq(List.of()), eq(5)))
        .thenReturn(List.of(hit(0.92)));

    List<RetrievalHit> result = service.retrieve(9L, "for循环", null, null);

    assertThat(result).extracting(RetrievalHit::score).containsExactly(0.92);
    verify(repository).searchByCosine(eq(9L), any(float[].class), eq(List.of()), eq(5));
    verify(repository, never()).searchByLiteral(anyLong(), any(), any(), anyInt());
  }

  @Test
  void fallsBackToLiteralWhenEmbeddingUnavailable() {
    when(embeddingClient.embed("列表")).thenReturn(Optional.empty());
    when(repository.searchByLiteral(eq(9L), eq("列表"), eq(List.of()), eq(5)))
        .thenReturn(List.of(hit(0.0)));

    List<RetrievalHit> result = service.retrieve(9L, "列表", null, null);

    assertThat(result).extracting(RetrievalHit::score).containsExactly(0.0);
    verify(repository, never()).searchByCosine(anyLong(), any(float[].class), any(), anyInt());
    verify(repository).searchByLiteral(eq(9L), eq("列表"), eq(List.of()), eq(5));
  }

  @Test
  void fallsBackToLiteralWhenCosineHasNoHits() {
    when(embeddingClient.embed("字典")).thenReturn(Optional.of(vec2048()));
    when(repository.searchByCosine(eq(9L), any(float[].class), eq(List.of()), eq(5)))
        .thenReturn(List.of());
    when(repository.searchByLiteral(eq(9L), eq("字典"), eq(List.of()), eq(5)))
        .thenReturn(List.of(hit(0.0)));

    List<RetrievalHit> result = service.retrieve(9L, "字典", null, null);

    assertThat(result).hasSize(1);
    verify(repository).searchByCosine(eq(9L), any(float[].class), eq(List.of()), eq(5));
    verify(repository).searchByLiteral(eq(9L), eq("字典"), eq(List.of()), eq(5));
  }

  @Test
  void wrongDimensionVectorSkipsCosineAndUsesLiteral() {
    when(embeddingClient.embed("元组")).thenReturn(Optional.of(new float[] {1f, 0f}));
    when(repository.searchByLiteral(eq(9L), eq("元组"), eq(List.of()), eq(5)))
        .thenReturn(List.of(hit(0.0)));

    List<RetrievalHit> result = service.retrieve(9L, "元组", null, null);

    assertThat(result).hasSize(1);
    verify(repository, never()).searchByCosine(anyLong(), any(float[].class), any(), anyInt());
    verify(repository).searchByLiteral(eq(9L), eq("元组"), eq(List.of()), eq(5));
  }

  @Test
  void clampsTopKAndForwardsUploadIdsScope() {
    when(embeddingClient.embed("切片")).thenReturn(Optional.of(vec2048()));
    when(repository.searchByCosine(eq(9L), any(float[].class), eq(List.of(3L, 4L)), eq(20)))
        .thenReturn(List.of(hit(0.5)));

    List<RetrievalHit> result = service.retrieve(9L, "切片", List.of(3L, 4L), 999);

    assertThat(result).hasSize(1);
    // 999 → clamp 到 MAX(20)；uploadIds 原样透传作附加过滤。
    verify(repository).searchByCosine(eq(9L), any(float[].class), eq(List.of(3L, 4L)), eq(20));
  }

  @Test
  void embeddingAndRetrievalDoNotHoldDatabaseTransaction() {
    AtomicBoolean transactionDuringEmbedding = new AtomicBoolean(true);
    when(embeddingClient.embed("课程内容"))
        .thenAnswer(
            invocation -> {
              transactionDuringEmbedding.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return Optional.empty();
            });
    when(repository.searchByLiteral(9L, "课程内容", List.of(), 5)).thenReturn(List.of());

    PlatformTransactionManager transactionManager = new TestTransactionManager();
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(
        new TransactionInterceptor(
            transactionManager, new AnnotationTransactionAttributeSource()));
    UploadRetrievalService transactionalService =
        (UploadRetrievalService) proxyFactory.getProxy();
    transactionalService.retrieve(9L, "课程内容", null, null);

    assertThat(transactionDuringEmbedding).isFalse();
    verify(repository)
        .searchByLiteral(eq(9L), eq("课程内容"), eq(List.of()), eq(5));
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(
        Object transaction, org.springframework.transaction.TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
