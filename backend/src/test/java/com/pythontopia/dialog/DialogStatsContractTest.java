package com.pythontopia.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 跨模块一致性守卫：{@code GET /api/dialog/context} 产出的 {@code stats.*} 集合必须与
 * {@code data/npcs/&#42;/dialog.json} 模板真正引用的 {@code {{stats.*}}} 集合对齐。
 *
 * <ul>
 *   <li><b>正向</b>：模板引用的每个 key 都必须有产出，否则 UI 会渲染出兜底 '—'（或占位符泄露）；
 *   <li><b>反向</b>：产出的每个 key 都必须被模板引用（少数「非模板消费」的 key 见 {@link #NON_TEMPLATE_KEYS}），
 *       否则就是白跑 SQL——历史上这里多产出 27 个无人消费的 key（商店周限购 / 作物枯萎成熟 /
 *       每日题 / 能量 / 同伴对比等 R-14 已删玩法残留），每次对话都在做无效查询。
 * </ul>
 */
class DialogStatsContractTest {

  /** 不由 dialog.json 模板消费、但仍需下发的 key（DTO 顶层字段的 stats 镜像 / 前端文档示例）。 */
  private static final Set<String> NON_TEMPLATE_KEYS =
      Set.of("profileType", "cognitiveStyle", "currentChapterOrder");

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*stats\\.([A-Za-z0-9_]+)");

  private final DialogContextService service =
      new DialogContextService(
          mock(DialogContextRepository.class),
          mock(ProfileRepository.class),
          mock(UserRepository.class));

  @Test
  @DisplayName("模板引用的每个 stats key 都有服务端产出（无悬空占位符）")
  void everyTemplatePlaceholderIsProduced() {
    Set<String> referenced = referencedKeys();
    Assumptions.assumeFalse(referenced.isEmpty(), "data/npcs 不可达，跳过契约比对");

    assertThat(producedKeys()).containsAll(referenced);
  }

  @Test
  @DisplayName("服务端不产出任何无人消费的 stats key（不白跑 SQL）")
  void everyProducedKeyIsConsumed() {
    Set<String> referenced = referencedKeys();
    Assumptions.assumeFalse(referenced.isEmpty(), "data/npcs 不可达，跳过契约比对");

    Set<String> orphans = new TreeSet<>(producedKeys());
    orphans.removeAll(referenced);
    orphans.removeAll(NON_TEMPLATE_KEYS);

    assertThat(orphans)
        .as("这些 key 没有任何 dialog.json 模板引用；若确需保留请连同理由加入 NON_TEMPLATE_KEYS")
        .isEmpty();
  }

  /** 用全 mock 仓储跑一遍聚合，拿到实际产出的 key 集合（值走兜底分支，不影响 key 集合）。 */
  private Set<String> producedKeys() {
    return service.buildContext(1L, "tester").stats().keySet();
  }

  private Set<String> referencedKeys() {
    Path root = npcRoot();
    if (root == null) {
      return Set.of();
    }
    try (var stream = Files.list(root)) {
      List<Path> dialogs =
          stream.map(dir -> dir.resolve("dialog.json")).filter(Files::isRegularFile).toList();
      return dialogs.stream()
          .map(DialogStatsContractTest::readString)
          .flatMap(text -> PLACEHOLDER.matcher(text).results().map(DialogStatsContractTest::group1))
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static String group1(java.util.regex.MatchResult result) {
    return result.group(1);
  }

  private static String readString(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** 与 {@code NpcPersonaService.resolveNpcRoot} 同口径：先试 CWD，再试仓库根（mvn 从 backend/ 起）。 */
  private static Path npcRoot() {
    for (Path candidate : List.of(Path.of("data", "npcs"), Path.of("..", "data", "npcs"))) {
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    return null;
  }
}
