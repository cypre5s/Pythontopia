package com.pythontopia.learner.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.mastery.review.ReviewState;
import com.pythontopia.learner.mastery.review.ReviewStateRepository;
import com.pythontopia.learner.memory.dto.MemoryBriefDto;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.memory.provider.AgentMemoryRefRepository;
import com.pythontopia.learner.memory.provider.LocalMemoryProvider;
import com.pythontopia.learner.memory.provider.Mem0MemoryProvider;
import com.pythontopia.learner.memory.provider.MemoryProviderProperties;
import com.pythontopia.learner.memory.provider.MemoryProviderRouter;
import com.pythontopia.learner.memory.provider.ZepMemoryProvider;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

/**
 * MemoryBriefService 单测（测试矩阵 ①local 召回 / ③外部 500→回退 local / ④brief 聚合含
 * weak_kcs/style_notes/as_of 时序 / ⑤画像缺失优雅）。零外网：外部 provider 用 {@link MockRestServiceServer}
 * 模拟，profile/review/recall 用 mock 注入。
 */
class MemoryBriefServiceTest {

  private static final long USER = 7L;
  private static final Instant FIXED = Instant.parse("2026-06-17T05:52:00Z");

  private final MemoryRetrievalService retrieval = mock(MemoryRetrievalService.class);
  private final PassiveProfileService profileService = mock(PassiveProfileService.class);
  private final ReviewStateRepository reviewStateRepository = mock(ReviewStateRepository.class);
  private final AgentMemoryRefRepository refs = mock(AgentMemoryRefRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

  private MemoryBriefService service(MemoryProviderProperties props, RestTemplate restTemplate) {
    LocalMemoryProvider local = new LocalMemoryProvider(retrieval);
    Mem0MemoryProvider mem0 = new Mem0MemoryProvider(restTemplate, objectMapper, props);
    ZepMemoryProvider zep = new ZepMemoryProvider(restTemplate, objectMapper, props);
    MemoryProviderRouter router = new MemoryProviderRouter(local, mem0, zep, props, refs);
    return new MemoryBriefService(router, profileService, reviewStateRepository, clock);
  }

  private static MemoryProviderProperties localProps() {
    return new MemoryProviderProperties("local", "", "", "", "", 2000, 4000);
  }

  private static MemoryItemDto item(long id, String type, String summary, String kc, String createdAt) {
    return new MemoryItemDto(
        id, type, summary, kc == null ? List.of() : List.of(kc), createdAt, 4, 1.0, 0.8, 1.0, 0.7);
  }

  private static ReviewState review(String kc, int lapses, OffsetDateTime dueAt) {
    return new ReviewState(1L, USER, kc, 2.0, 0.3, 1, lapses, null, dueAt, null, null);
  }

  private static ProfileDimensionDto dim(String key, double score, double confidence, List<String> tags) {
    return new ProfileDimensionDto(key, key, score, confidence, 5, false, tags);
  }

  // ── ① local 召回喂入 facts（并把受阻记忆 KC 纳入 weak_kcs）──
  @Test
  void localRecallFeedsFactsAndStruggleKcs() {
    when(retrieval.retrieve(eq(USER), isNull(), any(), anyInt()))
        .thenReturn(List.of(item(2L, "practice_failed", "练习失败：PY43-KC-07", "PY43-KC-07", "2026-06-12T00:00:00Z")));
    when(profileService.getDimensions(USER)).thenReturn(List.of());
    when(reviewStateRepository.findAll(USER)).thenReturn(List.of());

    MemoryBriefDto brief = service(localProps(), new RestTemplate()).buildBrief(USER, "for 循环");

    assertThat(brief.facts()).containsExactly("练习失败：PY43-KC-07");
    assertThat(brief.weakKcs()).containsExactly("PY43-KC-07");
    assertThat(brief.styleNotes()).isEmpty();
    assertThat(brief.asOf()).isEqualTo("2026-06-17T05:52:00Z");
  }

  // ── ④ brief 聚合：facts 近→远时序 + weak_kcs + style_notes + as_of ──
  @Test
  void aggregatesFactsWeakKcsStyleNotesWithTemporalOrder() {
    when(retrieval.retrieve(eq(USER), isNull(), any(), anyInt()))
        .thenReturn(
            List.of(
                item(1L, "resource_consumed", "学习资源：PY43-KC-01", "PY43-KC-01", "2026-06-10T00:00:00Z"),
                item(2L, "practice_failed", "练习失败：PY43-KC-07", "PY43-KC-07", "2026-06-12T00:00:00Z"),
                item(3L, "practice_succeeded", "练习通过：PY43-KC-03", "PY43-KC-03", "2026-06-08T00:00:00Z")));
    OffsetDateTime due = OffsetDateTime.ofInstant(FIXED.minus(Duration.ofDays(2)), ZoneOffset.UTC);
    OffsetDateTime notDue = OffsetDateTime.ofInstant(FIXED.plus(Duration.ofDays(5)), ZoneOffset.UTC);
    when(reviewStateRepository.findAll(USER))
        .thenReturn(List.of(review("PY43-KC-99", 3, due), review("PY43-KC-03", 0, notDue)));
    when(profileService.getDimensions(USER))
        .thenReturn(
            List.of(
                dim("cognitive_style", 0.6, 0.5, List.of("logic", "visual")),
                dim("learning_preference", 0.5, 0.4, List.of("video", "example")),
                dim("emotion_persistence", 0.4, 0.3, List.of())));

    MemoryBriefDto brief = service(localProps(), new RestTemplate()).buildBrief(USER, "列表");

    // facts 按发生时间近→远：06-12 → 06-10 → 06-08。
    assertThat(brief.facts())
        .containsExactly("练习失败：PY43-KC-07", "学习资源：PY43-KC-01", "练习通过：PY43-KC-03");
    // weak_kcs：召回中受阻 KC（KC-07）在前，到期/有 lapse 的复习 KC（KC-99）补后；未到期且无 lapse 的 KC-03 不计。
    assertThat(brief.weakKcs()).containsExactly("PY43-KC-07", "PY43-KC-99");
    assertThat(brief.weakKcs()).doesNotContain("PY43-KC-03");
    // style_notes 来自 8 维画像。
    assertThat(brief.styleNotes())
        .containsExactly("认知风格: visual", "偏好: video,example", "情绪: 需鼓励");
    assertThat(brief.stablePreferences()).contains("偏好: video,example");
    assertThat(brief.strugglePatterns()).contains("练习失败：PY43-KC-07");
    assertThat(brief.recentWins()).contains("练习通过：PY43-KC-03");
    assertThat(brief.interventionHints())
        .contains("短步骤+鼓励反馈：先给一个小目标，再用微练习确认。");
    // as_of 体现时序（生成时刻 ISO）。
    assertThat(brief.asOf()).isEqualTo("2026-06-17T05:52:00Z");
  }

  @Test
  void deepMemoryBriefSeparatesPreferencesStrugglesWinsAndInterventions() {
    when(retrieval.retrieve(eq(USER), isNull(), any(), anyInt()))
        .thenReturn(
            List.of(
                item(1L, "preference", "偏好图解讲解", null, "2026-06-13T00:00:00Z"),
                item(2L, "resource_check_failed", "微测未过：PY43-KC-016", "PY43-KC-016", "2026-06-12T00:00:00Z"),
                item(3L, "comeback_solved", "逆袭通过：PY43-KC-016", "PY43-KC-016", "2026-06-11T00:00:00Z"),
                item(4L, "practice_succeeded", "练习通过：PY43-KC-001", "PY43-KC-001", "2026-06-10T00:00:00Z")));
    when(reviewStateRepository.findAll(USER)).thenReturn(List.of(review("PY43-KC-016", 2, OffsetDateTime.ofInstant(FIXED.minus(Duration.ofDays(1)), ZoneOffset.UTC))));
    when(profileService.getDimensions(USER))
        .thenReturn(
            List.of(
                dim("cognitive_style", 0.6, 0.6, List.of("visual")),
                dim("learning_preference", 0.7, 0.7, List.of("diagram")),
                dim("emotion_persistence", 0.35, 0.4, List.of())));

    MemoryBriefDto brief = service(localProps(), new RestTemplate()).buildBrief(USER, "列表 append");

    assertThat(brief.stablePreferences()).contains("偏好图解讲解", "认知风格: visual", "偏好: diagram");
    assertThat(brief.strugglePatterns()).contains("微测未过：PY43-KC-016");
    assertThat(brief.recentWins()).contains("逆袭通过：PY43-KC-016", "练习通过：PY43-KC-001");
    assertThat(brief.interventionHints())
        .contains(
            "优先图解/结构化示例。",
            "围绕 PY43-KC-016 做一次短补救，再换题验证。",
            "短步骤+鼓励反馈：先给一个小目标，再用微练习确认。");
  }

  // ── ③ 外部 provider 500 → router fail-soft 回退 local，facts 仍出 ──
  @Test
  void externalServerErrorFallsBackToLocalFacts() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://mem0.test/v1/memories/search/"))
        .andRespond(MockRestResponseCreators.withServerError());
    when(retrieval.retrieve(eq(USER), isNull(), any(), anyInt()))
        .thenReturn(List.of(item(2L, "practice_failed", "练习失败：PY43-KC-07", "PY43-KC-07", "2026-06-12T00:00:00Z")));
    when(profileService.getDimensions(USER)).thenReturn(List.of());
    when(reviewStateRepository.findAll(USER)).thenReturn(List.of());

    MemoryProviderProperties mem0Props =
        new MemoryProviderProperties("mem0", "http://mem0.test", "k", "", "", 2000, 4000);

    MemoryBriefDto brief = service(mem0Props, restTemplate).buildBrief(USER, "列表");

    assertThat(brief.facts()).containsExactly("练习失败：PY43-KC-07");
    assertThat(brief.weakKcs()).containsExactly("PY43-KC-07");
    server.verify(Duration.ofSeconds(2));
  }

  // ── ⑤ 画像缺失 / 各来源故障 → 优雅降级，brief 仍返回（空列表 + as_of）──
  @Test
  void degradesGracefullyWhenProfileAndReviewMissing() {
    when(retrieval.retrieve(anyLong(), any(), any(), anyInt())).thenReturn(List.of());
    when(profileService.getDimensions(USER)).thenThrow(new RuntimeException("no profile row"));
    when(reviewStateRepository.findAll(USER)).thenThrow(new RuntimeException("kc_review_state offline"));

    MemoryBriefDto brief = service(localProps(), new RestTemplate()).buildBrief(USER, null);

    assertThat(brief.facts()).isEmpty();
    assertThat(brief.weakKcs()).isEmpty();
    assertThat(brief.styleNotes()).isEmpty();
    assertThat(brief.asOf()).isEqualTo("2026-06-17T05:52:00Z");
  }
}
