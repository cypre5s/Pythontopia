package com.pythontopia.browse.service;

import com.pythontopia.browse.config.BrowseProperties;
import com.pythontopia.browse.dto.BrowseCitationDto;
import com.pythontopia.browse.dto.BrowseRunCreateRequest;
import com.pythontopia.browse.dto.BrowseRunDetailDto;
import com.pythontopia.browse.dto.BrowseRunDto;
import com.pythontopia.browse.dto.BrowseStepDto;
import com.pythontopia.browse.entity.BrowseRunEntity;
import com.pythontopia.browse.entity.BrowseRunStatus;
import com.pythontopia.browse.entity.BrowseStepEntity;
import com.pythontopia.browse.graph.BrowseGraphClient;
import com.pythontopia.browse.graph.BrowseGraphResult;
import com.pythontopia.browse.graph.BrowseGraphStep;
import com.pythontopia.browse.repository.BrowseRunRepository;
import com.pythontopia.browse.repository.BrowseStepRepository;
import com.pythontopia.common.error.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 联网查证落库服务：服务端注入白名单 → 调 graph BROWSE → 落 {@code browse_run} + {@code browse_step}（V170）。
 *
 * <p>错误语义分层（绝不 500）：
 *
 * <ul>
 *   <li>白名单为空（配置异常）→ {@code 503}，不调 graph。
 *   <li>graph 传输失败 → {@link BrowseGraphClient} 抛 {@code 502}。
 *   <li>graph 返回非法步骤动作（违约）→ {@code 502}。
 *   <li>graph 业务 fail-fast（{@code error} 非空）→ 正常落库为 {@code FAILED} 并连同已走步骤返回（§8.5）。
 * </ul>
 *
 * <p>外部命中 url 统一作为「外部内容，非课件」呈现，绝不进课件证据链。
 */
@Service
public class BrowseRunService {

  /** {@code browse_run.error} 列宽（V170）。 */
  private static final int MAX_ERROR_LENGTH = 512;

  /** {@code browse_step.url} 列宽（V170）。 */
  private static final int MAX_URL_LENGTH = 1024;

  /** {@code browse_step.title} 列宽（V192）。 */
  private static final int MAX_TITLE_LENGTH = 512;

  /** 合法只读动作（对齐 V170 CHECK 与 contract Literal）。 */
  private static final Set<String> ALLOWED_ACTIONS = Set.of("navigate", "read", "search", "stop");

  private final BrowseProperties properties;
  private final BrowseGraphClient graphClient;
  private final BrowseRunRepository runRepository;
  private final BrowseStepRepository stepRepository;

  public BrowseRunService(
      BrowseProperties properties,
      BrowseGraphClient graphClient,
      BrowseRunRepository runRepository,
      BrowseStepRepository stepRepository) {
    this.properties = properties;
    this.graphClient = graphClient;
    this.runRepository = runRepository;
    this.stepRepository = stepRepository;
  }

  @Transactional
  public BrowseRunDto create(long userId, BrowseRunCreateRequest request) {
    int maxSteps =
        request.maxSteps() == null ? BrowseProperties.DEFAULT_MAX_STEPS : request.maxSteps();

    // 服务端注入白名单（不信任前端）；冻结默认保证非空，空=配置异常 fail-fast。
    List<String> allowlist = properties.allowedDomains();
    if (allowlist == null || allowlist.isEmpty()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE, "BROWSE_ALLOWLIST_EMPTY", "联网查证白名单未配置，暂不可用");
    }

    BrowseGraphResult result = graphClient.browse(request.goal(), allowlist, maxSteps, userId);

    BrowseRunStatus status = deriveStatus(result);
    // 即使 graph 违约（非法动作）也先校验，避免坏数据落库；校验通过再 insert，保持事务一致。
    List<BrowseStepEntity> steps = toStepEntities(result.steps());

    BrowseRunEntity run =
        runRepository.insert(
            userId,
            request.goal(),
            status,
            result.finalSummary(),
            result.truncated(),
            blankToNull(truncate(result.error(), MAX_ERROR_LENGTH)));

    stepRepository.insertAll(run.id(), withRunId(run.id(), steps));
    return BrowseRunDto.from(run);
  }

  @Transactional(readOnly = true)
  public BrowseRunDetailDto detail(long userId, long id) {
    BrowseRunEntity run =
        runRepository
            .findByIdForUser(userId, id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "BROWSE_RUN_NOT_FOUND", "联网查证会话不存在"));
    List<BrowseStepEntity> steps = stepRepository.findByRunId(id);
    List<BrowseStepDto> stepDtos = steps.stream().map(BrowseStepDto::from).toList();
    List<BrowseCitationDto> citations = deriveCitations(steps);
    return BrowseRunDetailDto.from(run, stepDtos, citations);
  }

  private static BrowseRunStatus deriveStatus(BrowseGraphResult result) {
    if (!result.error().isBlank()) {
      return BrowseRunStatus.FAILED;
    }
    if (result.truncated()) {
      return BrowseRunStatus.TRUNCATED;
    }
    return BrowseRunStatus.SUCCESS;
  }

  /** 校验并规整 graph 步骤为待落库实体（runId 暂置 null，insert 前补齐）。 */
  private static List<BrowseStepEntity> toStepEntities(List<BrowseGraphStep> steps) {
    List<BrowseStepEntity> entities = new ArrayList<>(steps.size());
    for (BrowseGraphStep step : steps) {
      String action = step.action() == null ? "" : step.action();
      if (!ALLOWED_ACTIONS.contains(action)) {
        // graph 违约（返回未知动作）→ 统一 502，绝不让 DB CHECK 冒泡成 500。
        throw new BusinessException(
            HttpStatus.BAD_GATEWAY, "BROWSE_GRAPH_BAD_RESPONSE", "联网查证返回了非法步骤动作");
      }
      entities.add(
          new BrowseStepEntity(
              null,
              null,
              step.index(),
              action,
              truncate(step.url() == null ? "" : step.url(), MAX_URL_LENGTH),
              step.summary() == null ? "" : step.summary(),
              truncate(step.title() == null ? "" : step.title(), MAX_TITLE_LENGTH),
              step.snippet() == null || step.snippet().isBlank()
                  ? (step.summary() == null ? "" : step.summary())
                  : step.snippet()));
    }
    return entities;
  }

  private static List<BrowseStepEntity> withRunId(long runId, List<BrowseStepEntity> steps) {
    return steps.stream()
        .map(
            s ->
                new BrowseStepEntity(
                    null,
                    runId,
                    s.idx(),
                    s.action(),
                    s.url(),
                    s.summary(),
                    s.title(),
                    s.snippet()))
        .toList();
  }

  /** 外部命中引用 = 步骤中出现过的非空 url（去重、保序），统一标注「外部内容，非课件」。 */
  private static List<BrowseCitationDto> deriveCitations(List<BrowseStepEntity> steps) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    for (BrowseStepEntity step : steps) {
      String url = step.url();
      if (url != null && !url.isBlank()) {
        urls.add(url);
      }
    }
    return urls.stream().map(BrowseCitationDto::external).toList();
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
