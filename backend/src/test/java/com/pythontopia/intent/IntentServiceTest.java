package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.service.CoursePackService;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link IntentService} 的画像注入：仅学生角色把 {@link PassiveProfileService#profileBrief(Long)}
 * 透传给 graph client；非学生/取数异常一律降级空串（画像可用性绝不阻塞路由）。
 */
class IntentServiceTest {

  private final IntentGraphClient graphClient = mock(IntentGraphClient.class);
  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final PracticeSubmissionRepository submissionRepository =
      mock(PracticeSubmissionRepository.class);
  private final IntentService service =
      new IntentService(graphClient, passiveProfileService, submissionRepository);

  private static IntentRouteRequest request() {
    return new IntentRouteRequest(
        "讲讲 for 循环",
        List.of(),
        List.of(),
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static final IntentRouteResponse RESPONSE =
      new IntentRouteResponse(
          "ASK",
          List.of(),
          "for 循环",
          0.8,
          "ok",
          "",
          null,
          "ask",
          List.of("ASK"),
          false,
          "",
          "generative",
          "answer",
          "contextual_match",
          null);

  @Test
  void studentRouteInjectsProfileBrief() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest req = request();
    when(passiveProfileService.profileBrief(7L)).thenReturn("认知风格: visual; 情绪: 需鼓励");
    when(graphClient.route(eq(req), eq("认知风格: visual; 情绪: 需鼓励"))).thenReturn(RESPONSE);

    assertThat(service.route(student, req)).isSameAs(RESPONSE);
    verify(graphClient).route(req, "认知风格: visual; 情绪: 需鼓励");
  }

  @Test
  void nonStudentRouteSkipsProfileLookup() {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "tea", Role.TEACHER);
    IntentRouteRequest req = request();
    when(graphClient.route(eq(req), eq(""))).thenReturn(RESPONSE);

    assertThat(service.route(teacher, req)).isSameAs(RESPONSE);
    verifyNoInteractions(passiveProfileService);
  }

  @Test
  void profileFailureDegradesToEmptyBrief() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest req = request();
    when(passiveProfileService.profileBrief(7L)).thenThrow(new IllegalStateException("db down"));
    when(graphClient.route(eq(req), eq(""))).thenReturn(RESPONSE);

    assertThat(service.route(student, req)).isSameAs(RESPONSE);
    verify(graphClient).route(req, "");
  }

  @Test
  void failedSubmissionReferenceIsReplacedWithAuthoritativeDebugContext() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest req =
        new IntentRouteRequest(
            "帮我定位这次 WA",
            List.of(),
            List.of(),
            null,
            List.of(),
            null,
            null,
            null,
            null,
            "default",
            "slash_command",
            null,
            "FIX_BUG",
            new IntentRouteRequest.InteractionContext(
                "s1", "town_hub", "practice", 999L, "伪造代码", 901L, List.of(), "伪造错误", "debug", null));
    SubmissionDto submission =
        new SubmissionDto(
            901L,
            7L,
            41L,
            null,
            SubmissionSource.TRAINING,
            "PYTHON_3_11",
            "print(1 / 0)",
            OffsetDateTime.now(),
            SubmissionStatus.FAILED,
            3,
            128);
    JudgeResultDto judge =
        new JudgeResultDto(
            1L,
            901L,
            Verdict.RE,
            0,
            1,
            "ZeroDivisionError",
            null,
            OffsetDateTime.now());
    when(submissionRepository.findSubmission(7L, 901L)).thenReturn(Optional.of(submission));
    when(submissionRepository.findJudgeResult(901L)).thenReturn(Optional.of(judge));
    when(passiveProfileService.profileBrief(7L)).thenReturn("");
    when(graphClient.route(org.mockito.ArgumentMatchers.any(), eq(""))).thenReturn(RESPONSE);

    assertThat(service.route(student, req)).isSameAs(RESPONSE);

    ArgumentCaptor<IntentRouteRequest> captor = ArgumentCaptor.forClass(IntentRouteRequest.class);
    verify(graphClient).route(captor.capture(), eq(""));
    IntentRouteRequest.InteractionContext interaction = captor.getValue().interaction();
    assertThat(interaction.latestSubmissionId()).isEqualTo(901L);
    assertThat(interaction.currentProblemId()).isEqualTo(41L);
    assertThat(interaction.currentCode()).isEqualTo("print(1 / 0)");
    assertThat(interaction.errorText()).isEqualTo("RE: ZeroDivisionError");
  }

  @Test
  void unownedSubmissionReferenceIsStrippedBeforeGraphCall() {
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest base = request();
    IntentRouteRequest req =
        new IntentRouteRequest(
            base.message(),
            base.manualKcCodes(),
            base.courseKcs(),
            base.currentChapter(),
            base.recentKcCodes(),
            base.recentTurns(),
            base.lastIntent(),
            base.lastKcCodes(),
            base.sessionCorrections(),
            base.scope(),
            base.source(),
            base.capabilityNamespace(),
            "FIX_BUG",
            new IntentRouteRequest.InteractionContext(
                "s1", "town_hub", "practice", null, "print(1)", 901L, List.of(), "WA", "debug", null));
    when(submissionRepository.findSubmission(7L, 901L)).thenReturn(Optional.empty());
    when(passiveProfileService.profileBrief(7L)).thenReturn("");
    when(graphClient.route(org.mockito.ArgumentMatchers.any(), eq(""))).thenReturn(RESPONSE);

    service.route(student, req);

    ArgumentCaptor<IntentRouteRequest> captor = ArgumentCaptor.forClass(IntentRouteRequest.class);
    verify(graphClient).route(captor.capture(), eq(""));
    assertThat(captor.getValue().interaction().latestSubmissionId()).isNull();
  }

  @Test
  void routeDecisionIsReturnedWithUserBoundExecutionToken() {
    InteractionContextVerifier verifier = mock(InteractionContextVerifier.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    IntentService signedService =
        new IntentService(graphClient, passiveProfileService, verifier, signer);
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest req = request();
    IntentRouteRequest.InteractionContext hydratedInteraction =
        new IntentRouteRequest.InteractionContext(
            "s1", "town_hub", "practice", 41L, "print(1 / 0)", 901L, List.of(), "RE", "debug", null);
    var decision =
        RouteExecutionTokenSignerTest.multiDecision("route_0123456789abcdef");
    IntentRouteResponse routed =
        new IntentRouteResponse(
            "PLAN",
            List.of("PY43-KC-012"),
            "总结再练习",
            0.94,
            "compound",
            "",
            "PRACTICE",
            "plan",
            List.of("PLAN", "PRACTICE"),
            false,
            "",
            "generative",
            "compound",
            "compound_task",
            decision);
    when(verifier.verify(7L, req.message(), req.interaction())).thenReturn(hydratedInteraction);
    when(passiveProfileService.profileBrief(7L)).thenReturn("");
    when(graphClient.route(org.mockito.ArgumentMatchers.any(), eq(""))).thenReturn(routed);
    when(signer.issue(7L, decision, req.message(), req.interaction()))
        .thenReturn("route-token");

    IntentRouteResponse response = signedService.route(student, req);

    assertThat(response.routeExecutionToken()).isEqualTo("route-token");
    // Graph 只收到服务端补全的权威上下文，但令牌必须绑定前端实际能回传的原始 null。
    ArgumentCaptor<IntentRouteRequest> safeRequest =
        ArgumentCaptor.forClass(IntentRouteRequest.class);
    verify(graphClient).route(safeRequest.capture(), eq(""));
    assertThat(safeRequest.getValue().interaction()).isEqualTo(hydratedInteraction);
    verify(signer).issue(7L, decision, req.message(), req.interaction());
  }

  @Test
  void productionRouteUsesServerLearningContextAndRecordsObservation() {
    InteractionContextVerifier verifier = mock(InteractionContextVerifier.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    WorkshopLearningContextService contextService = mock(WorkshopLearningContextService.class);
    IntentService personalizedService =
        new IntentService(
            graphClient, passiveProfileService, verifier, signer, contextService);
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest req = request();
    LearnerRoutingContext context =
        LearnerRoutingContext.empty();
    context =
        new LearnerRoutingContext(
            "认知风格: visual",
            context.kcMastery(),
            context.kcConfidence(),
            context.weakKcs(),
            context.recentErrorKcs(),
            context.cognitiveLoad(),
            context.frustrationLevel(),
            "visual",
            List.of("图解"),
            context.profileMajor(),
            context.pythonLevel(),
            context.recentResources(),
            context.recentSubmissions(),
            context.strugglePatterns(),
            context.recentWins(),
            context.interventionHints(),
            context.asOf());
    when(verifier.verify(7L, req.message(), req.interaction())).thenReturn(null);
    when(contextService.build(7L, req.message())).thenReturn(context);
    when(graphClient.route(org.mockito.ArgumentMatchers.any(), eq("认知风格: visual"), eq(context)))
        .thenReturn(RESPONSE);

    assertThat(personalizedService.route(student, req)).isSameAs(RESPONSE);

    verify(graphClient)
        .route(org.mockito.ArgumentMatchers.any(), eq("认知风格: visual"), eq(context));
    verify(contextService).recordRoute(7L, RESPONSE, context);
    verifyNoInteractions(passiveProfileService);
  }

  @Test
  void productionRouteReplacesEmptyClientCatalogWithAuthoritativePack43Kcs() {
    InteractionContextVerifier verifier = mock(InteractionContextVerifier.class);
    RouteExecutionTokenSigner signer = mock(RouteExecutionTokenSigner.class);
    WorkshopLearningContextService contextService = mock(WorkshopLearningContextService.class);
    CoursePackService coursePackService = mock(CoursePackService.class);
    IntentService authoritativeService =
        new IntentService(
            graphClient,
            passiveProfileService,
            verifier,
            signer,
            contextService,
            coursePackService);
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    IntentRouteRequest req = request();
    LearnerRoutingContext context = LearnerRoutingContext.empty();
    when(verifier.verify(7L, req.message(), req.interaction())).thenReturn(null);
    when(contextService.build(7L, req.message())).thenReturn(context);
    when(coursePackService.listKcsByChapter(null))
        .thenReturn(
            List.of(
                new CourseKc(
                    1797L,
                    CoursePackService.ACTIVE_PACK_ID,
                    "PY43-KC-031",
                    "for循环与遍历",
                    182L,
                    4,
                    "basic",
                    "for 语句与 range")));
    when(graphClient.route(
            org.mockito.ArgumentMatchers.any(), eq(""), eq(context)))
        .thenReturn(RESPONSE);

    authoritativeService.route(student, req);

    ArgumentCaptor<IntentRouteRequest> safeRequest =
        ArgumentCaptor.forClass(IntentRouteRequest.class);
    verify(graphClient).route(safeRequest.capture(), eq(""), eq(context));
    assertThat(safeRequest.getValue().courseKcs())
        .containsExactly(new IntentRouteRequest.CourseKc("PY43-KC-031", "for循环与遍历"));
  }
}
