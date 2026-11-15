import { isTextFocus } from "../pathfinding";
import { enteredInteractions } from "../interactionRegions";
import {
  nearbyWorldTarget,
  portalAvailable,
  portalBlockedMessage,
  portalRoute,
  reachableTargetApproach,
  resolveWorldTargets,
  worldConfig,
  worldTargetHint,
  type WorldTarget,
} from "../config/worldConfig";
import type { WorldCallbacks, WorldSceneSurface, WorldState } from "./types";

export class WorldInteractions {
  constructor(
    private scene: WorldSceneSurface,
    private state: WorldState,
    private callbacks: WorldCallbacks,
  ) {}
  approach(key: string, preferredPoint?: { x: number; y: number }) {
    const { scene, state, callbacks } = this;
    if (state.paused || scene.transitioning) return;
    let targets = resolveWorldTargets(key, state.npcs, state.runtime);
    if (!targets.length) {
      callbacks.error("暂时找不到这个地点。");
      return;
    }
    const local = targets.filter((target) => target.map_key === scene.mapKey);
    if (!local.length) {
      const route = portalRoute(
        scene.mapKey,
        targets[0]!.map_key,
        state.flags,
        worldConfig,
        state.runtime,
      );
      if (!route?.length) {
        const blockedPortal = worldConfig.portals.find(
          (portal) =>
            portal.from.map_key === scene.mapKey &&
            portal.to.map_key === targets[0]!.map_key &&
            !portalAvailable(portal, state.flags, state.runtime),
        );
        callbacks.error(
          (blockedPortal
            ? portalBlockedMessage(blockedPortal, state.runtime)
            : undefined) ?? "暂时无法前往该地点。",
        );
        return;
      }
      scene.travelTarget = key;
      targets = scene.objects.filter((item) => item.id === route[0]!.id);
    } else {
      scene.travelTarget = null;
      targets = local;
    }
    const reachable = reachableTargetApproach(
      { x: scene.tileX, y: scene.tileY },
      targets,
      scene.width,
      scene.height,
      (x, y) => scene.blocked(x, y),
      preferredPoint,
    );
    if (!reachable) {
      callbacks.error("暂时无法走到门前，请换个位置再试。");
      return;
    }
    scene.pendingTarget = reachable.target.id;
    scene.path = reachable.path;
    if (!scene.path.length) void scene.interact(reachable.target.id);
  }
  nearbyTarget(preferredTarget?: string) {
    const { scene, state } = this;
    const npcTargets: WorldTarget[] = scene.activeNpcs.map((item) => ({
      id: item.npc.key,
      map_key: scene.mapKey,
      label: item.npc.name,
      x: item.homeX,
      y: item.homeY,
      kind: "npc",
      radius: 2,
    }));
    return nearbyWorldTarget(
      {
        map_key: scene.mapKey,
        x: scene.player.x / 16 - 0.5,
        y: scene.player.y / 16 - 0.75,
      },
      [...npcTargets, ...scene.objects],
      state.flags,
      preferredTarget,
      state.runtime,
    );
  }
  refreshHint() {
    const { scene, state, callbacks } = this;
    const target =
      !state.paused &&
      !scene.transitioning &&
      !isTextFocus(document.activeElement)
        ? scene.nearbyTarget()
        : undefined;
    const hint = target
      ? `E · ${worldTargetHint(target, state.flags, state.runtime)}`
      : "";
    if (hint !== scene.lastHint) {
      scene.lastHint = hint;
      callbacks.hint(hint);
    }
  }
  async interact(preferredTarget?: string) {
    const { scene, state, callbacks } = this;
    if (state.paused || scene.busy || scene.transitioning) return;
    const explicitLockedPortal = preferredTarget
      ? scene.objects.find(
          (target) =>
            target.id === preferredTarget &&
            target.portal &&
            !portalAvailable(target.portal, state.flags, state.runtime) &&
            Math.abs(target.x - (scene.player.x / 16 - 0.5)) +
              Math.abs(target.y - (scene.player.y / 16 - 0.75)) <=
              target.radius,
        )
      : undefined;
    if (explicitLockedPortal?.portal) {
      scene.pendingTarget = null;
      scene.path = [];
      callbacks.error(
        portalBlockedMessage(explicitLockedPortal.portal, state.runtime),
      );
      return;
    }
    const target = scene.nearbyTarget(preferredTarget);
    const near =
      target?.kind === "npc"
        ? scene.activeNpcs.find((item) => item.npc.key === target.id)
        : undefined;
    if (near) {
      scene.pendingTarget = null;
      scene.path = [];
      const dir =
        scene.tileY > near.homeY
          ? 0
          : scene.tileX < near.homeX
            ? 1
            : scene.tileX > near.homeX
              ? 2
              : 3;
      near.sprite.anims.stop();
      near.sprite.setFrame(dir * 3 + 1);
      scene.save();
      callbacks.interact(near.npc.key, "npc");
      return;
    }
    const obj = target?.kind !== "npc" ? target : undefined;
    if (obj) {
      scene.path = [];
      scene.pendingTarget = null;
      if (obj.portal) {
        if (!portalAvailable(obj.portal, state.flags, state.runtime)) {
          callbacks.error(portalBlockedMessage(obj.portal, state.runtime));
          return;
        }
        scene.transitioning = true;
        state.pressed.clear();
        callbacks.hint("正在通过入口…");
        try {
          const player = await callbacks.transition(
            obj.portal.id,
            scene.position(),
          );
          if (player && !state.destroyed) scene.changeMap(player);
          else scene.travelTarget = null;
        } catch (error) {
          callbacks.error(
            error instanceof Error
              ? error.message
              : "暂时无法通过入口，请重试。",
          );
        } finally {
          scene.transitioning = false;
        }
        return;
      }
      scene.save();
      callbacks.interact(obj.id, "object");
    }
  }
  async checkRegionEntry() {
    const { scene, state, callbacks } = this;
    const crossings = enteredInteractions(
      scene.position(),
      state.runtime?.interactions ?? [],
      scene.enteredRegions,
    );
    scene.enteredRegions = crossings.inside;
    if (
      !crossings.entered.length ||
      !callbacks.enterRegion ||
      state.paused ||
      state.destroyed
    )
      return;
    // Both a completed walking step and an authorized cross-map landing enter a region.
    // Keep the exact tile until persistence and the flow request finish.
    scene.busy = true;
    state.pressed.clear();
    scene.path = [];
    scene.pendingTarget = null;
    scene.travelTarget = null;
    try {
      await callbacks.enterRegion(crossings.entered[0]!, scene.position());
    } catch (error) {
      if (!state.destroyed)
        callbacks.error(
          error instanceof Error ? error.message : "区域互动暂不可用",
        );
    } finally {
      scene.busy = false;
    }
  }
}
