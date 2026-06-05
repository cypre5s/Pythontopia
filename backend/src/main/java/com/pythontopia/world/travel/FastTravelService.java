package com.pythontopia.world.travel;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 快速旅行的纯校验服务：只计算花费增量，<strong>不</strong>写 {@code world_session} /
 * {@code save_slot} / {@code learner_wallet}；持久化由调用方负责，以便把这里算出的 cost delta 与世界
 * 时钟、钱包事务放进同一个外层 @Transactional。
 *
 * <p>硬校验（按短路顺序）：
 *
 * <ol>
 *   <li>{@code (fromScene, toScene)} 必须存在路线
 *   <li>{@code route.unlockPhase <= currentPhase}
 *   <li>{@code currentPythoco >= route.costPythoco}
 *   <li>{@code currentEnergy >= route.costEnergy}
 *   <li>{@code currentEnergy >= 10}（低能量锁定，防「快速旅行耗竭」滥用）
 *   <li>{@code dailyUsedCount < 10}（每日上限，仅 {@link #travel} 强制）
 * </ol>
 */
@Service
public class FastTravelService {

  static final int MIN_ENERGY_TO_TRAVEL = 10;
  static final int DAILY_USAGE_CAP = 10;

  private final FastTravelRepository repository;

  public FastTravelService(FastTravelRepository repository) {
    this.repository = repository;
  }

  /**
   * 满足该路线除「每日上限」外的全部前置条件时返回 {@code true}。每日用量故意不查，使「按钮是否可点」这种
   * 只读查询保持廉价；{@link #travel(long, String, String, int, int, int, int)} 提交前会重新校验全部条件。
   */
  public boolean canTravel(
      long userId,
      String fromScene,
      String toScene,
      int currentPhase,
      int currentPythoco,
      int currentEnergy) {
    Optional<FastTravelRoute> route = repository.find(fromScene, toScene);
    if (route.isEmpty()) {
      return false;
    }
    FastTravelRoute r = route.get();
    if (r.unlockPhase() > currentPhase) {
      return false;
    }
    if (currentPythoco < r.costPythoco()) {
      return false;
    }
    if (currentEnergy < r.costEnergy()) {
      return false;
    }
    if (currentEnergy < MIN_ENERGY_TO_TRAVEL) {
      return false;
    }
    return true;
  }

  /**
   * 计算旅行后的增量，不修改任何聚合。调用方须把返回的 {@code newPythoco}/{@code newEnergy} 落到
   * {@code learner_wallet}/{@code player_state}，并按 {@code minutesPassed} 推进世界时钟，全部在同一事务内。
   */
  public FastTravelResult travel(
      long userId,
      String fromScene,
      String toScene,
      int currentPhase,
      int currentPythoco,
      int currentEnergy,
      int dailyUsedCount) {
    Optional<FastTravelRoute> routeOpt = repository.find(fromScene, toScene);
    if (routeOpt.isEmpty()) {
      return new FastTravelResult(false, "ROUTE_NOT_FOUND", currentPythoco, currentEnergy, 0);
    }
    FastTravelRoute route = routeOpt.get();
    if (route.unlockPhase() > currentPhase) {
      return new FastTravelResult(false, "PHASE_LOCKED", currentPythoco, currentEnergy, 0);
    }
    if (currentPythoco < route.costPythoco()) {
      return new FastTravelResult(false, "INSUFFICIENT_FUND", currentPythoco, currentEnergy, 0);
    }
    if (currentEnergy < route.costEnergy()) {
      return new FastTravelResult(false, "INSUFFICIENT_ENERGY", currentPythoco, currentEnergy, 0);
    }
    if (currentEnergy < MIN_ENERGY_TO_TRAVEL) {
      return new FastTravelResult(false, "LOW_ENERGY", currentPythoco, currentEnergy, 0);
    }
    if (dailyUsedCount >= DAILY_USAGE_CAP) {
      return new FastTravelResult(false, "DAILY_CAP", currentPythoco, currentEnergy, 0);
    }
    int newPythoco = currentPythoco - route.costPythoco();
    int newEnergy = currentEnergy - route.costEnergy();
    return new FastTravelResult(true, "OK", newPythoco, newEnergy, route.costMinutes());
  }

  /**
   * {@link FastTravelService#travel} 的结果。失败时 {@code newPythoco}/{@code newEnergy} 原样回传（不扣减）、
   * {@code minutesPassed} 为 {@code 0}，因此忽略 {@code success} 的简单调用方最坏也只是 no-op。
   */
  public record FastTravelResult(
      boolean success, String reason, int newPythoco, int newEnergy, int minutesPassed) {}
}
