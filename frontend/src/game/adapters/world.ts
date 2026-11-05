import Phaser from "phaser";
import type { Npc, Player } from "../../shared/types";
import { findPath, isTextFocus } from "../pathfinding";
import { fromWorldCanvas, startedOnWorldCanvas } from "../pointerBoundary";
import { getWorldMap, mapCameraBounds, mapRenderBounds, mapTargets, nearbyWorldTarget, portalAvailable, portalRoute, reachableTargetApproach, resolveWorldTargets, withinWalkBounds, worldConfig, worldTargetHint, type WorldTarget } from "../config/worldConfig";
import { drawTileLayers, mapTextureUrls, parseRenderMap, type RenderMap } from "../config/worldRender";
import { SRW_CHARA_FRAME } from "../config/srwAssets.config";
const textureKey = (url: string) => `world-image:${url}`;
export interface WorldHandle {
  destroy(): void;
  setPaused(value: boolean): void;
  setWelcomeDrift?(value: boolean): void;
  setNpcs(npcs: Npc[]): void;
  setFlags(flags: string[]): void;
  direction(key: string, down: boolean): void;
  interact(): void;
  guide(npcKey: string): void;
  mute(value: boolean): void;
  getMapPosition?(): Player | undefined;
}
export interface WorldCallbacks {
  cameraReady?: (view: { x: number; y: number; zoom: number; map: string }) => void;
  position: (player: Player) => void;
  mapPosition?: (player: Player) => void;
  transition: (portalId: string, source: Player) => Promise<Player | null>;
  interact: (target: string, type: "npc" | "object") => void;
  hint: (text: string) => void;
  progress: (value: number) => void;
  error: (text: string) => void;
}
let instance: Phaser.Game | null = null;
export function createWorld(
  root: HTMLElement,
  initial: Player,
  npcs: Npc[],
  flags: string[],
  callbacks: WorldCallbacks,
): WorldHandle {
  if (instance) throw new Error("一个游戏会话只能创建一个世界实例");
  let drifting = false, driftStart = 0, destroyed = false;
  let paused = false,
    currentNpcs = npcs,
    currentFlags = flags;
  const ambient = new Audio("/assets/pixel/bgm/track_01.mp3");
  ambient.loop = true;
  ambient.volume = 0.15;
  const pressed = new Set<string>();
  let scene: TownScene;
  const keyDown = (e: KeyboardEvent) => {
    if (paused || e.isComposing || e.ctrlKey || e.metaKey || e.altKey || isTextFocus(document.activeElement)) return;
    // Movement returns focus to the world after closing a keyboard-accessible menu.
    if (["w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright"].includes(e.key.toLowerCase()) && document.activeElement?.closest(".world-ui")) {
      root.focus({ preventScroll: true });
    }
    if (
      ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", " "].includes(e.key)
    )
      e.preventDefault();
    pressed.add(e.key.toLowerCase());
    if (e.key.toLowerCase() === "e" && !e.repeat) scene?.interact();
  };
  const keyUp = (e: KeyboardEvent) => pressed.delete(e.key.toLowerCase());
  const blur = () => pressed.clear();
  window.addEventListener("keydown", keyDown);
  window.addEventListener("keyup", keyUp);
  window.addEventListener("blur", blur);
  class TownScene extends Phaser.Scene {
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
    resident: Phaser.GameObjects.Sprite | null = null;
    patrolTime = 0;
    renderMaps = new Map<string, RenderMap>();
    queuedTextures = new Set<string>();
    lastHint = "";
    constructor() {
      super("town");
    }
    preload() {
      if (worldConfig.portal_marker) this.load.image("portal-marker", worldConfig.portal_marker.asset);
      this.load.spritesheet(
        "chara_0",
        "/assets/pixel/sprites/super-retro-world/characters/chara_0.png",
        SRW_CHARA_FRAME,
      );
      ["chara_2", "chara_4", "chara_6", "chara_10"].forEach((k) =>
        this.load.spritesheet(
          k,
          `/assets/pixel/sprites/super-retro-world/characters/${k}.png`,
          SRW_CHARA_FRAME,
        ),
      );
      this.load.on("progress", (n: number) => callbacks.progress(n));
      this.load.on("loaderror", (file: Phaser.Loader.File) =>
        callbacks.error(`素材未能加载：${file.key}`),
      );
      for (const [mapKey, config] of Object.entries(worldConfig.maps)) {
        if (config.renderer !== "tiled" || !config.asset) continue;
        const cacheKey = `world-map-${mapKey}`;
        this.load.json(cacheKey, config.asset);
        this.load.once(`filecomplete-json-${cacheKey}`, () => {
          const map = parseRenderMap(this.cache.json.get(cacheKey), config.asset!);
          this.renderMaps.set(mapKey, map);
          for (const url of mapTextureUrls(map)) {
            if (!this.queuedTextures.has(url)) {
              this.queuedTextures.add(url);
              this.load.image(textureKey(url), url);
            }
          }
        });
      }
    }
    create() {
      scene = this;
      this.build();
      this.input.on(
        "pointerdown",
        (p: Phaser.Input.Pointer, objects: Phaser.GameObjects.GameObject[]) => {
          if (!startedOnWorldCanvas(p, this.game.canvas) || paused || objects.length) return;
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
        callbacks.cameraReady?.({ x: camera.worldView.x, y: camera.worldView.y, zoom: camera.zoom, map: this.mapKey });
      });
    }
    build() {
      this.tweens.killAll();
      this.busy = false;
      this.children.removeAll(true);
      this.activeNpcs = [];
      this.resident = null;
      this.objects = [];
      this.lastHint = "";
      callbacks.hint("");
      const config = getWorldMap(this.mapKey);
      const indoor = mapCameraBounds(config) === null;
      this.width = config.width;
      this.height = config.height;
      this.objects = mapTargets(this.mapKey).flatMap(target => target.points?.map(point => ({ ...target, ...point })) ?? [target]);
      const data = this.renderMaps.get(this.mapKey);
      if (!data) throw new Error("地图素材尚未准备好。");
      const bounds = mapRenderBounds(config);
      const groundKey = `${this.mapKey}-ground`;
      if (!this.textures.exists(groundKey)) {
        const ground = this.textures.createCanvas(groundKey, bounds.width * 16, bounds.height * 16);
        if (!ground) throw new Error("无法创建地图纹理");
        const context = ground.getContext();
        context.translate(-bounds.x * 16, -bounds.y * 16);
        const images = new Map(mapTextureUrls(data).map(url => [url, this.textures.get(textureKey(url)).getSourceImage() as HTMLImageElement]));
        drawTileLayers(context, data, images);
        ground.refresh();
      }
      this.add.image(bounds.x * 16, bounds.y * 16, groundKey).setOrigin(0).setDepth(-1000);
      this.collision = data.collision;
      for (const prop of data.props) {
        const key = textureKey(prop.textureUrl), texture = this.textures.get(key);
        const frameKey = prop.frame ? `crop:${prop.frame.x}:${prop.frame.y}:${prop.frame.width}:${prop.frame.height}` : undefined;
        if (prop.frame && frameKey && !texture.has(frameKey)) texture.add(frameKey, 0, prop.frame.x, prop.frame.y, prop.frame.width, prop.frame.height);
        const image = this.add.image(prop.x, prop.y, key, frameKey).setOrigin(0.5, 1).setDepth(prop.depth);
        const building = config.buildings.find(item => item.object_name === prop.name);
        if (building) {
          image.setInteractive({ useHandCursor: true });
          image.on("pointerdown", fromWorldCanvas(this.game.canvas, () => this.approach(building.id)));
          const label = this.add.text(prop.x, prop.y - image.height - 3, building.label, {
            fontSize: "8px", fontFamily: "sans-serif", resolution: 3, color: "#fff2d1", stroke: "#233a2b", strokeThickness: 2,
          }).setOrigin(0.5, 1).setDepth(80).setInteractive({ useHandCursor: true });
          label.on("pointerdown", fromWorldCanvas(this.game.canvas, () => this.approach(building.id)));
        } else if (prop.interactionId) {
          image.setInteractive({ useHandCursor: true });
          image.on("pointerdown", fromWorldCanvas(this.game.canvas, () => this.approach(prop.interactionId!, { x: Math.floor(prop.x / 16), y: Math.floor(prop.y / 16) })));
        }
      }
      if (worldConfig.portal_marker && this.textures.exists("portal-marker")) {
        for (const target of this.objects.filter(item => item.portal)) {
          const marker = worldConfig.portal_marker;
          const angle: Record<string, number> = { up: 0, right: 90, down: 180, left: 270 };
          const arrow = this.add.image(target.x * 16 + 8, target.y * 16 + 8, "portal-marker")
            .setDisplaySize(marker.width, marker.height).setAngle(angle[target.portal!.to.direction] ?? 0)
            .setAlpha(portalAvailable(target.portal!, currentFlags) ? 0.92 : 0.55).setDepth(target.y + 4);
          // Enlarge only the invisible hit area; the source artwork remains a small unobtrusive chevron.
          arrow.setInteractive({ useHandCursor: true,
            hitArea: new Phaser.Geom.Rectangle(-arrow.width / 2, -arrow.height / 2, arrow.width * 2, arrow.height * 2),
            hitAreaCallback: Phaser.Geom.Rectangle.Contains });
          arrow.on("pointerdown", fromWorldCanvas(this.game.canvas, () => this.approach(target.id)));
        }
      }
      const available = currentNpcs.filter((n) => n.map_key === this.mapKey);
      available.forEach((npc, i) => {
        const x = npc.tile_x,
          y = npc.tile_y;
        const texture = ["chara_2", "chara_4", "chara_6", "chara_10"][i % 4]!;
        const sprite = this.add
          .sprite(x * 16 + 8, y * 16 + 12, texture, 1)
          .setOrigin(0.5, 1)
          .setDepth(y + 3)
          .setInteractive({ useHandCursor: true });
        const label = this.add
          .text(x * 16 + 8, y * 16 - 12, npc.name, {
            fontSize: "9px",
            fontFamily: "sans-serif",
            resolution: 3,
            color: "#fff6d4",
            backgroundColor: "#1e3927bb",
            padding: { x: 3, y: 2 },
          })
          .setOrigin(0.5, 1)
          .setDepth(90);
        sprite.on("pointerdown", fromWorldCanvas(this.game.canvas, () => this.approach(npc.key)));
        this.activeNpcs.push({ npc, sprite, label, homeX: x, homeY: y });
      });
      this.player = this.add
        .sprite(this.tileX * 16 + 8, this.tileY * 16 + 12, "chara_0", 1)
        .setOrigin(0.5, 1)
        .setDepth(this.tileY + 3);
      callbacks.mapPosition?.(this.mapPosition());
      for (const name of [
        "chara_0",
        "chara_2",
        "chara_4",
        "chara_6",
        "chara_10",
      ])
        for (let d = 0; d < 4; d++) {
          const key = `${name}-${d}`;
          if (!this.anims.exists(key))
            this.anims.create({
              key,
              frames: this.anims.generateFrameNumbers(name, {
                start: d * 3,
                end: d * 3 + 2,
              }),
              frameRate: 8,
              repeat: -1,
            });
        }
      this.shade = this.add
        .rectangle(bounds.x * 16, bounds.y * 16, bounds.width * 16, bounds.height * 16, 0x14204f, 0.0)
        .setOrigin(0)
        .setDepth(100)
        .setBlendMode(Phaser.BlendModes.MULTIPLY);
      this.weather = this.add.graphics().setDepth(101);
      this.cameras.main.removeBounds();
      this.cameras.main.setBackgroundColor(indoor ? "#142019" : "#324c36");
      this.cameras.main.startFollow(this.player, true, indoor ? 1 : 0.18, indoor ? 1 : 0.18, 0, 10);
      this.resize();
      if (!indoor) {
        const spot = [
          { x: 26, y: 35 },
          { x: 24, y: 36 },
          { x: 20, y: 36 },
        ].find((p) => !this.blocked(p.x, p.y));
        if (spot)
          this.resident = this.add
            .sprite(spot.x * 16 + 8, spot.y * 16 + 12, "chara_10", 1)
            .setOrigin(0.5, 1)
            .setDepth(spot.y + 3);
      }
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
      if (bounds) camera.setBounds(bounds.x * 16, bounds.y * 16, bounds.width * 16, bounds.height * 16);
      else camera.removeBounds();
      if (this.player) camera.centerOn(this.player.x - camera.followOffset.x, this.player.y - camera.followOffset.y);
    }
    blocked(x: number, y: number) {
      return (
        !withinWalkBounds(getWorldMap(this.mapKey), x, y) ||
        Boolean(this.collision[y * this.width + x]) ||
        this.activeNpcs.some((n) => n.homeX === x && n.homeY === y)
      );
    }
    walkTo(x: number, y: number) {
      if (paused || this.busy || this.transitioning || this.blocked(x, y)) return;
      this.path = findPath(
        { x: this.tileX, y: this.tileY },
        { x, y },
        this.width,
        this.height,
        (a, b) => this.blocked(a, b),
      );
    }
    approach(key: string, preferredPoint?: { x: number; y: number }) {
      if (paused || this.transitioning) return;
      let targets = resolveWorldTargets(key, currentNpcs);
      if (!targets.length) { callbacks.error("暂时找不到这个地点。"); return; }
      const local = targets.filter(target => target.map_key === this.mapKey);
      if (!local.length) {
        const route = portalRoute(this.mapKey, targets[0]!.map_key, currentFlags);
        if (!route?.length) {
          const blockedPortal = worldConfig.portals.find(portal => portal.from.map_key === this.mapKey && portal.to.map_key === targets[0]!.map_key && !portalAvailable(portal, currentFlags));
          callbacks.error(blockedPortal?.blocked_message ?? "暂时无法前往该地点。");
          return;
        }
        this.travelTarget = key;
        targets = this.objects.filter(item => item.id === route[0]!.id);
      } else { this.travelTarget = null; targets = local; }
      const reachable = reachableTargetApproach({ x: this.tileX, y: this.tileY }, targets, this.width, this.height, (x, y) => this.blocked(x, y), preferredPoint);
      if (!reachable) { callbacks.error("暂时无法走到门前，请换个位置再试。"); return; }
      this.pendingTarget = reachable.target.id;
      this.path = reachable.path;
      if (!this.path.length) void this.interact(reachable.target.id);
    }
    nearbyTarget(preferredTarget?: string) {
      const npcTargets: WorldTarget[] = this.activeNpcs.map(item => ({ id: item.npc.key, map_key: this.mapKey, label: item.npc.name,
        x: item.homeX, y: item.homeY, kind: "npc", radius: 2 }));
      return nearbyWorldTarget({ map_key: this.mapKey, x: this.player.x / 16 - 0.5, y: this.player.y / 16 - 0.75 },
        [...npcTargets, ...this.objects], currentFlags, preferredTarget);
    }
    refreshHint() {
      const target = !paused && !this.transitioning && !isTextFocus(document.activeElement) ? this.nearbyTarget() : undefined;
      const hint = target ? `E · ${worldTargetHint(target, currentFlags)}` : "";
      if (hint !== this.lastHint) { this.lastHint = hint; callbacks.hint(hint); }
    }
    async interact(preferredTarget?: string) {
      if (paused || this.busy || this.transitioning) return;
      const explicitLockedPortal = preferredTarget ? this.objects.find(target => target.id === preferredTarget && target.portal
        && !portalAvailable(target.portal, currentFlags)
        && Math.abs(target.x - (this.player.x / 16 - 0.5)) + Math.abs(target.y - (this.player.y / 16 - 0.75)) <= target.radius) : undefined;
      if (explicitLockedPortal?.portal) {
        this.pendingTarget = null;
        this.path = [];
        callbacks.error(explicitLockedPortal.portal.blocked_message);
        return;
      }
      const target = this.nearbyTarget(preferredTarget);
      const near = target?.kind === "npc" ? this.activeNpcs.find(item => item.npc.key === target.id) : undefined;
      if (near) {
        this.pendingTarget = null;
        this.path = [];
        const dir =
          this.tileY > near.homeY
            ? 0
            : this.tileX < near.homeX
              ? 1
              : this.tileX > near.homeX
                ? 2
                : 3;
        near.sprite.anims.stop();
        near.sprite.setFrame(dir * 3 + 1);
        this.save();
        callbacks.interact(near.npc.key, "npc");
        return;
      }
      const obj = target?.kind !== "npc" ? target : undefined;
      if (obj) {
        this.path = [];
        this.pendingTarget = null;
        if (obj.portal) {
          if (!portalAvailable(obj.portal, currentFlags)) {
            callbacks.error(obj.portal.blocked_message);
            return;
          }
          this.transitioning = true;
          pressed.clear();
          callbacks.hint("正在通过入口…");
          try {
            const player = await callbacks.transition(obj.portal.id, this.position());
            if (player && !destroyed) this.changeMap(player);
            else this.travelTarget = null;
          } catch (error) {
            callbacks.error(error instanceof Error ? error.message : "暂时无法通过入口，请重试。");
          } finally { this.transitioning = false; }
          return;
        }
        this.save();
        callbacks.interact(obj.id, "object");
      }
    }
    changeMap(player: Player) {
      this.mapKey = player.map_key;
      this.tileX = player.tile_x;
      this.tileY = player.tile_y;
      this.direction = player.direction.toUpperCase();
      this.path = [];
      this.pendingTarget = null;
      this.build();
      if (this.travelTarget) {
        const finalTarget = this.travelTarget;
        this.time.delayedCall(200, () => { if (this.travelTarget === finalTarget) this.approach(finalTarget); });
      }
    }
    position(): Player {
      return { map_key: this.mapKey, tile_x: this.tileX, tile_y: this.tileY, direction: this.direction.toLowerCase() };
    }
    mapPosition(): Player {
      return { map_key: this.mapKey, tile_x: this.player.x / 16 - 0.5, tile_y: this.player.y / 16 - 0.75,
        direction: this.direction.toLowerCase() };
    }
    save() {
      if (!this.transitioning) callbacks.position(this.position());
    }
    update(time: number) {
      if (!this.player) return;
      const offset = this.cameras.main.followOffset;
      if (mapCameraBounds(getWorldMap(this.mapKey)) === null) {
        offset.set(0, 10);
      } else if (drifting) {
        if (!driftStart) driftStart = time;
        const t = (time - driftStart) / 1000;
        const easeIn = Math.min(1, t / 1.2);
        offset.x = Math.sin(t * 0.38) * 18 * easeIn;
        offset.y = 10 + Math.sin(t * 0.27) * 10 * easeIn;
      } else {
        offset.x *= 0.88; offset.y = 10 + (offset.y - 10) * 0.88;
        if (Math.abs(offset.x) < .01) offset.x = 0;
        if (Math.abs(offset.y - 10) < .01) offset.y = 10;
      }
      if (time - this.saveTimer > 1500) {
        this.saveTimer = time;
        this.save();
      }
      if (this.resident && !paused && time - this.patrolTime > 6000) {
        this.patrolTime = time;
        const x = Math.floor(this.resident.x / 16),
          y = Math.floor(this.resident.y / 16),
          step = Math.floor(time / 6000) % 2 ? 1 : -1;
        if (!this.blocked(x + step, y)) {
          this.resident.play(`chara_10-${step > 0 ? 2 : 1}`, true);
          const resident = this.resident;
          this.tweens.add({
            targets: resident,
            x: resident.x + step * 16,
            duration: 800,
            onComplete: () => {
              resident.anims.stop();
              resident.setFrame(1);
            },
          });
        }
      }
      this.weather.clear();
      if (this.mapKey === "town_hub" && new Date().getDate() % 3 === 0) {
        this.weather.lineStyle(1, 0xa4ced3, 0.16);
        for (let i = 0; i < 36; i++) {
            const x = (i * 47 + time * 0.016) % (this.width * 16),
            y = (i * 83 + time * 0.08) % (this.height * 16);
          this.weather.lineBetween(x, y, x - 2, y + 7);
        }
      }
      const hour = new Date().getHours(),
        night = hour >= 19 || hour < 6;
      this.shade.setAlpha(night ? 0.25 : 0);
      this.refreshHint();
      if (paused || isTextFocus(document.activeElement)) {
        this.path = [];
        this.player.anims.stop();
        return;
      }
      if (this.busy || this.transitioning) return;
      let dx = 0,
        dy = 0;
      if (pressed.has("arrowup") || pressed.has("w")) dy = -1;
      else if (pressed.has("arrowdown") || pressed.has("s")) dy = 1;
      else if (pressed.has("arrowleft") || pressed.has("a")) dx = -1;
      else if (pressed.has("arrowright") || pressed.has("d")) dx = 1;
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
        if (time - this.saveTimer > 1500) {
          this.saveTimer = time;
          this.save();
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
        onComplete: () => {
          this.busy = false;
          callbacks.mapPosition?.(this.mapPosition());
        },
      });
    }
  }
  instance = new Phaser.Game({
    type: Phaser.AUTO,
    parent: root,
    width: root.clientWidth,
    height: root.clientHeight,
    backgroundColor: "#233e2d",
    pixelArt: true,
    roundPixels: true,
    banner: false,
    scene: [TownScene],
    scale: { mode: Phaser.Scale.RESIZE, autoCenter: Phaser.Scale.CENTER_BOTH },
  });
  return {
    destroy() {
      destroyed = true;
      ambient.pause();
      ambient.src = "";
      pressed.clear();
      window.removeEventListener("keydown", keyDown);
      window.removeEventListener("keyup", keyUp);
      window.removeEventListener("blur", blur);
      instance?.destroy(true);
      instance = null;
    },
    setWelcomeDrift(value) { drifting = value; driftStart = 0; },
    setPaused(value) {
      paused = value;
      pressed.clear();
      if (scene) {
        scene.path = [];
        scene.pendingTarget = null;
        if (value) scene.travelTarget = null;
        scene.refreshHint();
        if (scene.player) callbacks.mapPosition?.(scene.mapPosition());
      }
    },
    setNpcs(value) {
      if (JSON.stringify(value) !== JSON.stringify(currentNpcs)) {
        currentNpcs = value;
        if (scene?.player) scene.build();
      }
    },
    setFlags(value) {
      if (JSON.stringify(value) !== JSON.stringify(currentFlags)) {
        currentFlags = value;
        if (scene?.player) scene.build();
      }
    },
    direction(key, down) {
      if (down) pressed.add(key);
      else pressed.delete(key);
    },
    interact() {
      scene?.interact();
    },
    guide(key) {
      if (scene) scene.travelTarget = null;
      scene?.approach(key);
    },
    mute(value) {
      if (value) ambient.pause();
      else
        void ambient
          .play()
          .catch(() =>
            callbacks.error("浏览器未允许播放音乐，请再次切换音乐选项。"),
          );
    },
    getMapPosition() { return scene?.player ? scene.mapPosition() : undefined; },
  };
}
