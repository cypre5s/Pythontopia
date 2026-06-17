package com.pythontopia.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.browse.config.BrowseProperties;
import com.pythontopia.browse.dto.BrowseCitationDto;
import com.pythontopia.browse.dto.BrowseRunCreateRequest;
import com.pythontopia.browse.dto.BrowseRunDetailDto;
import com.pythontopia.browse.dto.BrowseRunDto;
import com.pythontopia.browse.entity.BrowseRunEntity;
import com.pythontopia.browse.entity.BrowseRunStatus;
import com.pythontopia.browse.entity.BrowseStepEntity;
import com.pythontopia.browse.graph.BrowseGraphClient;
import com.pythontopia.browse.graph.BrowseGraphResult;
import com.pythontopia.browse.graph.BrowseGraphStep;
import com.pythontopia.browse.repository.BrowseRunRepository;
import com.pythontopia.browse.repository.BrowseStepRepository;
import com.pythontopia.browse.service.BrowseRunService;
import com.pythontopia.common.error.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * 联网查证落库服务单测。覆盖契约要求矩阵：①创建落库 ②detail 含 steps/citations ④graph 失败→统一错误
 * 不 500 ⑤allowlist 服务端注入；外加：默认/自定义 max_steps、截断态、graph fail-fast 仍存已走步骤、
 * graph 违约非法动作→502、白名单空→503、明细 404。全程零外网（graph 客户端 mock）。
 */
class BrowseRunServiceTest {

  private BrowseProperties properties;
  private BrowseGraphClient graphClient;
  private BrowseRunRepository runRepository;
  private BrowseStepRepository stepRepository;
  private BrowseRunService service;

  @BeforeEach
  void setUp() {
    // 真实 properties：冻结默认 + 一个自定义判题站点 host（演示服务端注入会并入配置项）。
    properties = new BrowseProperties(List.of("judge.pythontopia.local"));
    graphClient = mock(BrowseGraphClient.class);
    runRepository = mock(BrowseRunRepository.class);
    stepRepository = mock(BrowseStepRepository.class);
    service = new BrowseRunService(properties, graphClient, runRepository, stepRepository);
  }

  private BrowseRunEntity savedRun(BrowseRunStatus status, boolean truncated, String error) {
    return new BrowseRunEntity(
        42L, 1L, "去 python 官网查 sorted 的稳定性", status, "sorted 是稳定排序", truncated, error,
        OffsetDateTime.parse("2026-06-17T10:00:00Z"));
  }

  private BrowseGraphResult successResult() {
    return new BrowseGraphResult(
        List.of(
            new BrowseGraphStep(0, "navigate", "https://docs.python.org/3/howto/sorting.html", "打开排序指南"),
            new BrowseGraphStep(1, "read", "https://docs.python.org/3/howto/sorting.html", "确认 sorted 稳定")),
        "sorted 是稳定排序",
        List.of("https://docs.python.org/3/howto/sorting.html"),
        false,
        "");
  }

  // ① 创建 run 落库（含步骤），返回会话头 DTO。
  @Test
  void createPersistsRunAndStepsWithSuccessStatus() {
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(successResult());
    when(runRepository.insert(anyLong(), any(), any(), any(), eq(false), isNull()))
        .thenReturn(savedRun(BrowseRunStatus.SUCCESS, false, null));

    BrowseRunDto dto =
        service.create(1L, new BrowseRunCreateRequest("去 python 官网查 sorted 的稳定性", null));

    assertThat(dto.id()).isEqualTo(42L);
    assertThat(dto.status()).isEqualTo(BrowseRunStatus.SUCCESS);
    verify(runRepository)
        .insert(eq(1L), eq("去 python 官网查 sorted 的稳定性"), eq(BrowseRunStatus.SUCCESS), eq("sorted 是稳定排序"), eq(false), isNull());

    ArgumentCaptor<List<BrowseStepEntity>> stepsCaptor = listCaptor();
    verify(stepRepository).insertAll(eq(42L), stepsCaptor.capture());
    List<BrowseStepEntity> persisted = stepsCaptor.getValue();
    assertThat(persisted).hasSize(2);
    assertThat(persisted.get(0).runId()).isEqualTo(42L);
    assertThat(persisted.get(0).action()).isEqualTo("navigate");
    assertThat(persisted.get(1).action()).isEqualTo("read");
  }

  // ⑤ allowlist 由服务端注入（前端无法提供域）：注入的是 properties 白名单（冻结默认 + 配置项）。
  @Test
  void createInjectsServerSideAllowlistNotFromClient() {
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(successResult());
    when(runRepository.insert(anyLong(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(savedRun(BrowseRunStatus.SUCCESS, false, null));

    service.create(7L, new BrowseRunCreateRequest("看看这道题在官方文档怎么说", null));

    ArgumentCaptor<List<String>> domainsCaptor = listCaptor();
    verify(graphClient).browse(eq("看看这道题在官方文档怎么说"), domainsCaptor.capture(), anyInt(), eq(7L));
    assertThat(domainsCaptor.getValue())
        .containsExactlyInAnyOrderElementsOf(properties.allowedDomains())
        .contains("docs.python.org", "peps.python.org", "pypi.org", "judge.pythontopia.local");
  }

  // 默认 max_steps = 契约默认 6。
  @Test
  void createDefaultsMaxStepsToContractDefault() {
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(successResult());
    when(runRepository.insert(anyLong(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(savedRun(BrowseRunStatus.SUCCESS, false, null));

    service.create(1L, new BrowseRunCreateRequest("goal", null));

    verify(graphClient).browse(eq("goal"), any(), eq(BrowseProperties.DEFAULT_MAX_STEPS), eq(1L));
  }

  // 显式 max_steps 透传。
  @Test
  void createUsesRequestedMaxStepsWhenProvided() {
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(successResult());
    when(runRepository.insert(anyLong(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(savedRun(BrowseRunStatus.SUCCESS, false, null));

    service.create(1L, new BrowseRunCreateRequest("goal", 3));

    verify(graphClient).browse(eq("goal"), any(), eq(3), eq(1L));
  }

  // 触达上界 → TRUNCATED。
  @Test
  void createMarksTruncatedRun() {
    BrowseGraphResult truncated =
        new BrowseGraphResult(
            List.of(new BrowseGraphStep(0, "navigate", "https://pypi.org/", "首页")),
            "未完成", List.of("https://pypi.org/"), true, "");
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(truncated);
    when(runRepository.insert(anyLong(), any(), any(), any(), eq(true), isNull()))
        .thenReturn(savedRun(BrowseRunStatus.TRUNCATED, true, null));

    BrowseRunDto dto = service.create(1L, new BrowseRunCreateRequest("goal", 1));

    assertThat(dto.status()).isEqualTo(BrowseRunStatus.TRUNCATED);
    verify(runRepository).insert(eq(1L), eq("goal"), eq(BrowseRunStatus.TRUNCATED), eq("未完成"), eq(true), isNull());
  }

  // graph 业务 fail-fast：error 非空 → FAILED，但已走步骤仍落库（§8.5），不抛异常。
  @Test
  void createPersistsFailedRunWithPartialStepsOnGraphFailFast() {
    BrowseGraphResult failFast =
        new BrowseGraphResult(
            List.of(new BrowseGraphStep(0, "navigate", "https://docs.python.org/3/", "打开")),
            "", List.of(), false, "目标站点超时");
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(failFast);
    when(runRepository.insert(anyLong(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(savedRun(BrowseRunStatus.FAILED, false, "目标站点超时"));

    BrowseRunDto dto = service.create(1L, new BrowseRunCreateRequest("goal", null));

    assertThat(dto.status()).isEqualTo(BrowseRunStatus.FAILED);
    verify(runRepository).insert(eq(1L), eq("goal"), eq(BrowseRunStatus.FAILED), eq(""), eq(false), eq("目标站点超时"));
    ArgumentCaptor<List<BrowseStepEntity>> stepsCaptor = listCaptor();
    verify(stepRepository).insertAll(eq(42L), stepsCaptor.capture());
    assertThat(stepsCaptor.getValue()).hasSize(1);
  }

  // ④ graph 传输失败（客户端抛 502）→ 服务原样上抛 BusinessException(502)，绝不吞成 500；不落库。
  @Test
  void createPropagatesGraphUnavailableAsBadGatewayWithoutPersisting() {
    when(graphClient.browse(any(), any(), anyInt(), anyLong()))
        .thenThrow(
            new BusinessException(HttpStatus.BAD_GATEWAY, "BROWSE_GRAPH_UNAVAILABLE", "联网查证服务暂不可用"));

    assertThatThrownBy(() -> service.create(1L, new BrowseRunCreateRequest("goal", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    verify(runRepository, never()).insert(anyLong(), any(), any(), any(), anyBoolean(), any());
    verifyNoInteractions(stepRepository);
  }

  // graph 违约（返回非法动作）→ 统一 502，不让 DB CHECK 冒泡成 500；校验在 insert 前，故不落库。
  @Test
  void createRejectsIllegalGraphActionAsBadGateway() {
    BrowseGraphResult illegal =
        new BrowseGraphResult(
            List.of(new BrowseGraphStep(0, "click", "https://docs.python.org/3/", "点了一下")),
            "x", List.of(), false, "");
    when(graphClient.browse(any(), any(), anyInt(), anyLong())).thenReturn(illegal);

    assertThatThrownBy(() -> service.create(1L, new BrowseRunCreateRequest("goal", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    verify(runRepository, never()).insert(anyLong(), any(), any(), any(), anyBoolean(), any());
    verifyNoInteractions(stepRepository);
  }

  // 白名单空（配置异常）→ 503，且不调 graph（不信任前端，亦不裸奔）。
  @Test
  void createFailsWhenAllowlistEmpty() {
    BrowseProperties empty = mock(BrowseProperties.class);
    when(empty.allowedDomains()).thenReturn(List.of());
    BrowseRunService guarded =
        new BrowseRunService(empty, graphClient, runRepository, stepRepository);

    assertThatThrownBy(() -> guarded.create(1L, new BrowseRunCreateRequest("goal", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    verifyNoInteractions(graphClient);
  }

  // ② detail 含 steps[] 与 citations[]（外部命中 url 去重保序 + 统一「外部内容，非课件」标注）。
  @Test
  void detailReturnsStepsAndDerivedExternalCitations() {
    when(runRepository.findByIdForUser(1L, 42L))
        .thenReturn(Optional.of(savedRun(BrowseRunStatus.SUCCESS, false, null)));
    when(stepRepository.findByRunId(42L))
        .thenReturn(
            List.of(
                new BrowseStepEntity(1L, 42L, 0, "navigate", "https://docs.python.org/3/a", "a"),
                new BrowseStepEntity(2L, 42L, 1, "read", "https://docs.python.org/3/a", "再读 a"),
                new BrowseStepEntity(3L, 42L, 2, "search", "", "站内搜索"),
                new BrowseStepEntity(4L, 42L, 3, "read", "https://peps.python.org/pep-0008/", "PEP8")));

    BrowseRunDetailDto detail = service.detail(1L, 42L);

    assertThat(detail.id()).isEqualTo(42L);
    assertThat(detail.steps()).hasSize(4);
    assertThat(detail.steps().get(0).index()).isEqualTo(0);
    // 去重（两次同 url 只一条）+ 跳过空 url（search 步）+ 保序。
    assertThat(detail.citations()).hasSize(2);
    assertThat(detail.citations()).extracting(BrowseCitationDto::url)
        .containsExactly("https://docs.python.org/3/a", "https://peps.python.org/pep-0008/");
    assertThat(detail.citations()).extracting(BrowseCitationDto::label)
        .containsOnly("外部内容，非课件");
  }

  // 明细越权/不存在 → 404。
  @Test
  void detailNotFoundThrows404() {
    when(runRepository.findByIdForUser(1L, 999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.detail(1L, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @SuppressWarnings("unchecked")
  private static <T> ArgumentCaptor<List<T>> listCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }
}
