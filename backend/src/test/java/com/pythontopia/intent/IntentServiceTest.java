package com.pythontopia.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.IntentRouteResponse;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link IntentService} 的画像注入：仅学生角色把 {@link PassiveProfileService#profileBrief(Long)}
 * 透传给 graph client；非学生/取数异常一律降级空串（画像可用性绝不阻塞路由）。
 */
class IntentServiceTest {

  private final IntentGraphClient graphClient = mock(IntentGraphClient.class);
  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final IntentService service = new IntentService(graphClient, passiveProfileService);

  private static IntentRouteRequest request() {
    return new IntentRouteRequest(
        "讲讲 for 循环", List.of(), List.of(), null, List.of(), null, null, null, null, null);
  }

  private static final IntentRouteResponse RESPONSE =
      new IntentRouteResponse("ASK", List.of(), "for 循环", 0.8, "ok", "", null);

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
}
