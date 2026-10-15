package com.pythontopia.npc;

import com.pythontopia.learner.event.LearningEventLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NPC 寒暄「每日预生成」任务（R-15 反馈）：把实时生成的延迟前移到每天定时批处理。
 *
 * <p>每天定时为「最近活跃（有学习事件）的学生 × 核心教育 NPC」预热寒暄写入
 * {@link NpcGreetingService} 缓存（TTL 覆盖全天），玩家白天进对话直接命中缓存、零等待，一天一更新。
 *
 * <p><b>稳健性</b>：仅覆盖最近 {@code active-days} 天活跃、至多 {@code max-users} 个学生（避免给历史
 * 全量学生跑 LLM）；逐个调用、单个失败不影响其余（异常隔离）；{@code enabled=false} 可整体关闭。
 * 无记忆的学生 {@code prewarm} 返回 none 且不缓存，天然跳过。
 */
@Component
public class NpcGreetingPrewarmJob {

  private static final Logger log = LoggerFactory.getLogger(NpcGreetingPrewarmJob.class);

  /** 预生成覆盖的核心教育 NPC（与 data/npcs/index.json 的 educator_core 对齐）。 */
  static final List<String> CORE_NPCS =
      List.of(
          "mayor_py",
          "foreman_bug",
          "librarian_doc",
          "coach_q",
          "sage_review",
          "professor",
          "mine_keeper");

  private final LearningEventLogRepository eventRepository;
  private final NpcGreetingService greetingService;
  private final boolean enabled;
  private final int activeDays;
  private final int maxUsers;

  public NpcGreetingPrewarmJob(
      LearningEventLogRepository eventRepository,
      NpcGreetingService greetingService,
      @Value("${pythontopia.npc.greeting.prewarm.enabled:true}") boolean enabled,
      @Value("${pythontopia.npc.greeting.prewarm.active-days:7}") int activeDays,
      @Value("${pythontopia.npc.greeting.prewarm.max-users:200}") int maxUsers) {
    this.eventRepository = eventRepository;
    this.greetingService = greetingService;
    this.enabled = enabled;
    this.activeDays = activeDays;
    this.maxUsers = maxUsers;
  }

  /** 每日预热（默认凌晨 3:30）：刷新活跃学生 × 核心 NPC 的寒暄缓存。 */
  @Scheduled(cron = "${pythontopia.npc.greeting.prewarm.cron:0 30 3 * * *}")
  public void prewarmDaily() {
    if (!enabled) {
      return;
    }
    Instant since = Instant.now().minus(Duration.ofDays(Math.max(1, activeDays)));
    List<Long> users;
    try {
      users = eventRepository.findActiveUserIds(since, Math.max(1, maxUsers));
    } catch (RuntimeException ex) {
      log.warn(
          "npc greeting prewarm aborted: list active users failed errorType={}",
          ex.getClass().getSimpleName());
      return;
    }
    int ok = 0;
    int fail = 0;
    for (Long userId : users) {
      if (userId == null) {
        continue;
      }
      for (String npcKey : CORE_NPCS) {
        try {
          greetingService.prewarm(userId, npcKey);
          ok++;
        } catch (RuntimeException ex) {
          // 单个学生×NPC 失败不影响其余（异常隔离），下次定时或玩家实时访问可恢复。
          fail++;
          log.debug(
              "npc greeting prewarm failed errorType={}", ex.getClass().getSimpleName());
        }
      }
    }
    log.info(
        "npc greeting prewarm done: activeUsers={} core_npcs={} ok={} fail={}",
        users.size(),
        CORE_NPCS.size(),
        ok,
        fail);
  }
}
