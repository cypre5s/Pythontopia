package com.pythontopia.world.travel;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Validation-only skeleton for R-12 W5 fast travel. This service deliberately does <strong>not</strong>
 * touch {@code world_session} / {@code save_slot} / {@code learner_wallet}; persistence is owned by
 * the caller (e.g. a future {@code FastTravelController}) so that the cost deltas computed here can
 * be composed with the world clock and wallet transactions inside a single outer @Transactional.
 *
 * <p>Hard checks (in short-circuit order):
 *
 * <ol>
 *   <li>route must exist for {@code (fromScene, toScene)}
 *   <li>{@code route.unlockPhase <= currentPhase}
 *   <li>{@code currentPythoco >= route.costPythoco}
 *   <li>{@code currentEnergy >= route.costEnergy}
 *   <li>{@code currentEnergy >= 10} (low-energy lockout, avoids "fast travel exhaustion" abuse)
 *   <li>{@code dailyUsedCount < 10} (daily cap, only enforced by {@link #travel})
 * </ol>
 *
 * See {@code docs/gameplay/r12-deepen/07-fast-travel-network.md} §5 for the priority table that
 * the production controller will eventually layer on top (story / combat / festival / deep-mine
 * locks live above this service and short-circuit before it is even consulted).
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
   * Returns {@code true} when the player satisfies every non-daily precondition for the route.
   * Daily usage is intentionally excluded so the read-only "can I show the button as enabled"
   * query stays cheap; {@link #travel(long, String, String, int, int, int, int)} re-validates
   * everything before committing.
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
   * Computes the post-travel deltas without mutating any aggregate. Callers must apply the
   * returned {@code newPythoco}/{@code newEnergy} to {@code learner_wallet}/{@code player_state}
   * and advance the world clock by {@code minutesPassed}, all inside one transaction.
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
   * Outcome of a {@link FastTravelService#travel} call. On failure, {@code newPythoco} /
   * {@code newEnergy} mirror the inputs (no deduction) and {@code minutesPassed} is {@code 0},
   * so a naive caller that ignores {@code success} will at worst no-op.
   */
  public record FastTravelResult(
      boolean success, String reason, int newPythoco, int newEnergy, int minutesPassed) {}
}
