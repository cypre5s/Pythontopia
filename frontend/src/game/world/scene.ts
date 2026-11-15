import Phaser from "phaser";
import type { Npc, Player } from "../../shared/types";
import { findPath, isTextFocus } from "../pathfinding";
import { startedOnWorldCanvas } from "../pointerBoundary";
import { enteredInteractions } from "../interactionRegions";
import {
  getWorldMap,
  mapCameraBounds,
  withinWalkBounds,
  type WorldTarget,
} from "../config/worldConfig";
import type { RenderMap } from "../config/worldRender";
import { preloadWorld, queueCharacterSprites } from "./assets";
import { buildWorld } from "./render";
import { updateEnvironment } from "./environment";
import { WorldInteractions } from "./interactions";
import type { WorldCallbacks, WorldSceneSurface, WorldState } from "./types";

export function createTownScene(
  initial: Player,
  state: WorldState,
  callbacks: WorldCallbacks,
  ready: (scene: TownScene) => void,
) {
  class TownScene extends Phaser.Scene implements WorldSceneSurface {
    interactions: WorldInteractions = new WorldInteractions(
      this,
      state,
      callbacks,
    );
    player!: Phaser.GameObjects.Sprite;
    mapKey = initial.map_key;
    tileX = initial.tile_x;
    tileY = initial.tile_y;
    direction = (initial.direction ?? "down").toUpperCase();
    busy = false;
    path: { x: number; y: number }[] = [];
    collision: number[] = [];
    width = getWorldMap(initial.map_key).width;
    height = getWorldMap(initial.map_key).height;
    activeNpcs: {
      npc: Npc;
      sprite: Phaser.GameObjects.Sprite;
      label: Phaser.GameObjects.Text;
      homeX: number;
      homeY: number;
    }[] = [];
    shade!: Phaser.GameObjects.Rectangle;
    saveTimer = 0;
    pendingTarget: string | null = null;
    travelTarget: string | null = null;
    objects: WorldTarget[] = [];
    transitioning = false;
    weather!: Phaser.GameObjects.Graphics;
    renderMaps = new Map<string, RenderMap>();
    queuedTextures = new Set<string>();
    lastHint = "";
    enteredRegions = enteredInteractions(
      initial,
      state.runtime?.interactions ?? [],
      new Set(),
    ).inside;
    constructor() {
      super("town");
    }
    preload() {
      preloadWorld(this, state, callbacks);
    }
    queueCharacterSprites() {
      return queueCharacterSprites(this, state);
    }
    create() {
      ready(this);
      this.build();
      this.input.on(
        "pointerdown",
        (p: Phaser.Input.Pointer, objects: Phaser.GameObjects.GameObject[]) => {
          if (
            !startedOnWorldCanvas(p, this.game.canvas) ||
            state.paused ||
            objects.length
          )
            return;
          const world = p.positionToCamera(
            this.cameras.main,
          ) as Phaser.Math.Vector2;
          this.pendingTarget = null;
          this.travelTarget = null;
          this.walkTo(Math.floor(world.x / 16), Math.floor(world.y / 16));
        },
      );
      this.scale.on("resize", () => this.resize());
      callbacks.progress(1);
      this.game.events.once("postrender", () => {
        const camera = this.cameras.main;
        callbacks.cameraReady?.({
          x: camera.worldView.x,
          y: camera.worldView.y,
          zoom: camera.zoom,
          map: this.mapKey,
        });
      });
    }
    build() {
      buildWorld(this, state, callbacks);
    }
    resize() {
      this.cameras.main.setZoom(
        Math.min(
          3,
          Math.max(
            1.5,
            Math.min(this.scale.width / 380, this.scale.height / 240),
          ),
        ),
      );
      const bounds = mapCameraBounds(getWorldMap(this.mapKey));
      const camera = this.cameras.main;
      if (bounds)
        camera.setBounds(
          bounds.x * 16,
          bounds.y * 16,
          bounds.width * 16,
          bounds.height * 16,
        );
      else camera.removeBounds();
      if (this.player)
        camera.centerOn(
          this.player.x - camera.followOffset.x,
          this.player.y - camera.followOffset.y,
        );
    }
    blocked(x: number, y: number) {
      return (
        !withinWalkBounds(getWorldMap(this.mapKey), x, y) ||
        Boolean(this.collision[y * this.width + x]) ||
        this.activeNpcs.some((n) => n.homeX === x && n.homeY === y)
      );
    }
    walkTo(x: number, y: number) {
      if (state.paused || this.busy || this.transitioning || this.blocked(x, y))
        return;
      this.path = findPath(
        { x: this.tileX, y: this.tileY },
        { x, y },
        this.width,
        this.height,
        (a, b) => this.blocked(a, b),
      );
    }
    approach(key: string, preferredPoint?: { x: number; y: number }) {
      this.interactions.approach(key, preferredPoint);
    }
    nearbyTarget(preferredTarget?: string) {
      return this.interactions.nearbyTarget(preferredTarget);
    }
    refreshHint() {
      this.interactions.refreshHint();
    }
    interact(preferredTarget?: string) {
      return this.interactions.interact(preferredTarget);
    }
    changeMap(player: Player) {
      const crossedMap = this.mapKey !== player.map_key;
      this.mapKey = player.map_key;
      this.tileX = player.tile_x;
      this.tileY = player.tile_y;
      this.direction = player.direction.toUpperCase();
      this.path = [];
      this.pendingTarget = null;
      if (crossedMap) this.enteredRegions = new Set();
      this.build();
      if (crossedMap)
        this.time.delayedCall(0, () => {
          if (!state.destroyed) void this.checkRegionEntry();
        });
      if (this.travelTarget) {
        const finalTarget = this.travelTarget;
        this.time.delayedCall(200, () => {
          if (this.travelTarget === finalTarget) this.approach(finalTarget);
        });
      }
    }
    position(): Player {
      return {
        map_key: this.mapKey,
        tile_x: this.tileX,
        tile_y: this.tileY,
        direction: this.direction.toLowerCase(),
      };
    }
    mapPosition(): Player {
      return {
        map_key: this.mapKey,
        tile_x: this.player.x / 16 - 0.5,
        tile_y: this.player.y / 16 - 0.75,
        direction: this.direction.toLowerCase(),
      };
    }
    checkRegionEntry() {
      return this.interactions.checkRegionEntry();
    }
    save() {
      if (!this.transitioning) callbacks.position(this.position());
    }
    update(time: number) {
      if (!this.player) return;
      updateEnvironment(this, state, time);
      this.refreshHint();
      if (state.paused || isTextFocus(document.activeElement)) {
        this.path = [];
        this.player.anims.stop();
        return;
      }
      if (this.busy || this.transitioning) return;
      let dx = 0,
        dy = 0;
      if (state.pressed.has("arrowup") || state.pressed.has("w")) dy = -1;
      else if (state.pressed.has("arrowdown") || state.pressed.has("s")) dy = 1;
      else if (state.pressed.has("arrowleft") || state.pressed.has("a"))
        dx = -1;
      else if (state.pressed.has("arrowright") || state.pressed.has("d"))
        dx = 1;
      if (dx || dy) {
        this.path = [];
        this.pendingTarget = null;
        this.travelTarget = null;
      } else {
        const next = this.path.shift();
        if (next) {
          dx = next.x - this.tileX;
          dy = next.y - this.tileY;
        }
      }
      if (!dx && !dy) {
        this.player.anims.stop();
        const row = { DOWN: 0, LEFT: 1, RIGHT: 2, UP: 3 }[this.direction] ?? 0;
        this.player.setFrame(row * 3 + 1);
        if (this.pendingTarget) {
          const target = this.pendingTarget;
          this.pendingTarget = null;
          this.interact(target);
        }
        return;
      }
      this.direction =
        dy < 0 ? "UP" : dy > 0 ? "DOWN" : dx < 0 ? "LEFT" : "RIGHT";
      const row = { DOWN: 0, LEFT: 1, RIGHT: 2, UP: 3 }[this.direction]!;
      this.player.setFrame(row * 3 + 1);
      if (this.blocked(this.tileX + dx, this.tileY + dy)) return;
      this.tileX += dx;
      this.tileY += dy;
      this.busy = true;
      this.player.play(`chara_0-${row}`, true);
      this.player.setDepth(this.tileY + 3);
      this.tweens.add({
        targets: this.player,
        x: this.tileX * 16 + 8,
        y: this.tileY * 16 + 12,
        duration: 130,
        onComplete: async () => {
          callbacks.mapPosition?.(this.mapPosition());
          await this.checkRegionEntry();
          this.busy = false;
        },
      });
    }
  }

  return TownScene;
}
export type TownScene = InstanceType<ReturnType<typeof createTownScene>>;
