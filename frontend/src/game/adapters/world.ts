import Phaser from "phaser";
import type { Npc, Player } from "../../shared/types";
import { findPath, isTextFocus } from "../pathfinding";
import {
  SRW_PROP_GROUPS,
  SRW_HOUSE_KEYS,
  SRW_CHARA_FRAME,
} from "../config/srwAssets.config";
interface TiledObject {
  name: string;
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  properties?: { name: string; value: unknown }[];
}
interface MapData {
  width: number;
  height: number;
  layers: {
    name: string;
    type: string;
    objects?: TiledObject[];
    data?: number[];
  }[];
}
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
}
export interface WorldCallbacks {
  cameraReady?: (view: { x: number; y: number; zoom: number; map: string }) => void;
  position: (player: Player) => void;
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
  let drifting = false, driftStart = 0;
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
    keys: MapData | null = null;
    collision: number[] = [];
    width = 50;
    height = 40;
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
    objects: { id: string; x: number; y: number }[] = [];
    weather!: Phaser.GameObjects.Graphics;
    resident: Phaser.GameObjects.Sprite | null = null;
    patrolTime = 0;
    constructor() {
      super("town");
    }
    preload() {
      this.load.json("town-map", "/assets/maps/town_hub.json");
      this.load.tilemapTiledJSON("town-tiled", "/assets/maps/town_hub.json");
      this.load.image(
        "town-tiles",
        "/assets/pixel/tilesets/super-retro-world/town_tiles.png",
      );
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
      this.load.once("filecomplete-json-town-map", () => {
        const map = this.cache.json.get("town-map") as MapData;
        const used = new Set(
          map.layers
            .flatMap((l) => l.objects ?? [])
            .map((o) =>
              String(
                o.properties?.find((p) => p.name === "sprite")?.value ?? "",
              ),
            ),
        );
        const paths = new Map<string, string>();
        SRW_HOUSE_KEYS.forEach((k) =>
          paths.set(
            `srw_${k}`,
            `/assets/pixel/buildings/super-retro-world/${k}.png`,
          ),
        );
        SRW_PROP_GROUPS.forEach((g) =>
          g.stems.forEach((k) =>
            paths.set(
              `srw_${k}`,
              `/assets/pixel/props/super-retro-world/${g.dir}/${k}.png`,
            ),
          ),
        );
        used.forEach((k) => {
          const url = paths.get(k);
          if (url) this.load.image(k, url);
        });
      });
    }
    create() {
      scene = this;
      this.keys = this.cache.json.get("town-map") as MapData;
      this.build();
      this.input.on(
        "pointerdown",
        (p: Phaser.Input.Pointer, objects: Phaser.GameObjects.GameObject[]) => {
          if (paused || objects.length) return;
          const world = p.positionToCamera(
            this.cameras.main,
          ) as Phaser.Math.Vector2;
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
      const indoor = this.mapKey === "library";
      this.width = indoor ? 18 : 50;
      this.height = indoor ? 14 : 40;
      if (!this.textures.exists("tile-frames"))
        this.textures.addSpriteSheet(
          "tile-frames",
          this.textures.get("town-tiles").getSourceImage() as HTMLImageElement,
          { frameWidth: 16, frameHeight: 16 },
        );
      if (indoor) {
        const room = this.add.graphics();
        room.fillStyle(0x594b37).fillRect(0, 0, 288, 224);
        room.fillStyle(0xd5bd86).fillRect(16, 16, 256, 192);
        for (let y = 18; y < 206; y += 8) {
          room.lineStyle(1, 0xb69b68, 0.5).lineBetween(16, y, 272, y);
        }
        room.fillStyle(0x4a6549).fillRect(100, 115, 88, 60);
        for (let x = 32; x <= 224; x += 48) {
          room.fillStyle(0x6d4e30).fillRect(x, 42, 32, 38);
          room.fillStyle(0x332c25).fillRect(x + 3, 45, 26, 29);
          for (let i = 0; i < 6; i++) {
            room
              .fillStyle([0x8a9b66, 0xc4966b, 0x728c91, 0xbdb481][i % 4]!)
              .fillRect(x + 4 + i * 4, 48, 3, 19);
          }
          room.fillStyle(0x9d794a).fillRect(x, 68, 32, 3);
        }
        room.fillStyle(0x7ba1a0).fillRect(20, 90, 8, 25);
        room.fillStyle(0xe9d49b).fillRect(23, 93, 2, 18);
        this.add
          .text(144, 22, "小镇图书馆", {
            fontSize: "12px",
            color: "#fff9dd",
            resolution: 3,
          })
          .setOrigin(0.5);
        this.objects = [
          { id: "library_shelf", x: 4, y: 4 },
          { id: "library_exit", x: 9, y: 12 },
        ];
        this.collision = [];
      } else {
        const data = this.keys!;
        // Composite static ground once: adjacent WebGL tile quads can leave
        // subpixel seams while the camera follows or uses a fractional zoom.
        const groundKey = "town-ground";
        if (!this.textures.exists(groundKey)) {
          const ground = this.textures.createCanvas(groundKey, data.width * 16, data.height * 16);
          if (!ground) throw new Error("无法创建小镇地面纹理");
          const context = ground.getContext();
          const tiles = this.textures.get("town-tiles").getSourceImage() as HTMLImageElement;
          const columns = tiles.width / 16;
          context.imageSmoothingEnabled = false;
          for (const layer of data.layers.filter(
            (item) => item.type === "tilelayer" && item.name !== "collision",
          )) {
            layer.data?.forEach((gid, index) => {
              if (!gid) return;
              const tile = gid - 1;
              context.drawImage(tiles, (tile % columns) * 16, Math.floor(tile / columns) * 16,
                16, 16, (index % data.width) * 16, Math.floor(index / data.width) * 16, 16, 16);
            });
          }
          ground.refresh();
        }
        this.add.image(0, 0, groundKey).setOrigin(0);
        this.collision =
          data.layers.find((l) => l.name === "collision")?.data ?? [];
        for (const o of data.layers.flatMap((l) => l.objects ?? [])) {
          const props = Object.fromEntries(
            (o.properties ?? []).map((p) => [p.name, p.value]),
          );
          const key = String(props.sprite ?? "");
          if (this.textures.exists(key)) {
            const isBuilding = o.type === "building",
              x = o.x + (isBuilding ? o.width / 2 : o.width / 2),
              y = o.y + (isBuilding ? o.height : o.height);
            this.add
              .image(x, y, key)
              .setOrigin(0.5, 1)
              .setDepth(y / 16 + 1);
          }
          if (o.type === "building") {
            const id = String(props.key ?? o.name);
            this.objects.push({
              id,
              x: Math.floor((o.x + o.width / 2) / 16),
              y: Math.floor((o.y + o.height) / 16) + 1,
            });
          }
        }
        this.objects = [
          { id: "library_door", x: 8, y: 22 },
          { id: "practice_plaza", x: 38, y: 24 },
        ];
      }
      const available = indoor
        ? currentNpcs.filter((n) => n.key === "librarian_doc")
        : currentNpcs.filter((n) => n.map_key === "town_hub");
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
        sprite.on("pointerdown", () => this.approach(npc.key));
        this.activeNpcs.push({ npc, sprite, label, homeX: x, homeY: y });
      });
      this.player = this.add
        .sprite(this.tileX * 16 + 8, this.tileY * 16 + 12, "chara_0", 1)
        .setOrigin(0.5, 1)
        .setDepth(this.tileY + 3);
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
        .rectangle(0, 0, this.width * 16, this.height * 16, 0x14204f, 0.0)
        .setOrigin(0)
        .setDepth(100)
        .setBlendMode(Phaser.BlendModes.MULTIPLY);
      this.weather = this.add.graphics().setDepth(101);
      this.resize();
      this.cameras.main.startFollow(this.player, true, 0.18, 0.18);
      this.cameras.main.setBounds(0, 0, this.width * 16, this.height * 16);
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
    }
    blocked(x: number, y: number) {
      return (
        x < 0 ||
        y < 0 ||
        x >= this.width ||
        y >= this.height ||
        (this.mapKey === "library"
          ? x === 0 || y === 0 || x === 17 || y === 13
          : Boolean(this.collision[y * this.width + x])) ||
        this.activeNpcs.some((n) => n.homeX === x && n.homeY === y)
      );
    }
    walkTo(x: number, y: number) {
      if (paused || this.busy || this.blocked(x, y)) return;
      this.path = findPath(
        { x: this.tileX, y: this.tileY },
        { x, y },
        this.width,
        this.height,
        (a, b) => this.blocked(a, b),
      );
    }
    approach(key: string) {
      if (paused) return;
      const targetNpc = currentNpcs.find((n) => n.key === key);
      if (this.mapKey === "library" && targetNpc?.map_key === "town_hub") {
        this.travelTarget = key;
        key = "library_exit";
      }
      const n = this.activeNpcs.find((n) => n.npc.key === key);
      if (!n) {
        const target =
          this.objects.find((o) => o.id === key) ||
          (key === "librarian_doc" && this.mapKey === "town_hub"
            ? this.objects.find((o) => o.id === "library_door")
            : undefined);
        if (target) {
          this.pendingTarget = target.id;
          const point = [
            { x: target.x, y: target.y },
            { x: target.x, y: target.y + 1 },
            { x: target.x - 1, y: target.y },
          ].find((p) => !this.blocked(p.x, p.y));
          if (point) this.walkTo(point.x, point.y);
        }
        return;
      }
      this.pendingTarget = key;
      const adjacent = [
        [0, 1],
        [1, 0],
        [-1, 0],
        [0, -1],
      ]
        .map(([dx, dy]) => ({ x: n.homeX + dx!, y: n.homeY + dy! }))
        .filter((p) => !this.blocked(p.x, p.y));
      adjacent.sort(
        (a, b) =>
          Math.abs(a.x - this.tileX) +
          Math.abs(a.y - this.tileY) -
          Math.abs(b.x - this.tileX) -
          Math.abs(b.y - this.tileY),
      );
      const p = adjacent[0];
      if (p) this.walkTo(p.x, p.y);
      if (Math.abs(n.homeX - this.tileX) + Math.abs(n.homeY - this.tileY) <= 2)
        this.interact(key);
    }
    interact(preferredTarget?: string) {
      if (paused || this.busy) return;
      const near = this.activeNpcs.find(
        (n) =>
          (!preferredTarget || n.npc.key === preferredTarget) &&
          Math.abs(n.homeX - this.tileX) + Math.abs(n.homeY - this.tileY) <= 2,
      );
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
      const obj = this.objects.find(
        (o) =>
          (!preferredTarget || o.id === preferredTarget) &&
          Math.abs(o.x - this.tileX) + Math.abs(o.y - this.tileY) <= 2,
      );
      if (obj) {
        if (obj.id === "library_door") {
          if (!currentFlags.includes("library_unlocked")) {
            callbacks.error("图书馆正在修复。先完成镇长的委托。");
            return;
          }
          this.changeMap("library", 9, 11);
          return;
        }
        if (obj.id === "library_exit") {
          this.changeMap("town_hub", 8, 22);
          return;
        }
        this.save();
        callbacks.interact(obj.id, "object");
      }
    }
    changeMap(key: string, x: number, y: number) {
      this.mapKey = key;
      this.tileX = x;
      this.tileY = y;
      this.path = [];
      this.pendingTarget = null;
      this.build();
      this.save();
      if (this.travelTarget && key === "town_hub") {
        const finalTarget = this.travelTarget;
        this.travelTarget = null;
        this.time.delayedCall(200, () => this.approach(finalTarget));
      }
    }
    save() {
      callbacks.position({
        map_key: this.mapKey,
        tile_x: this.tileX,
        tile_y: this.tileY,
        direction: this.direction.toLowerCase(),
      });
    }
    update(time: number) {
      if (!this.player) return;
      const offset = this.cameras.main.followOffset;
      if (drifting) {
        if (!driftStart) driftStart = time;
        const t = (time - driftStart) / 1000;
        const easeIn = Math.min(1, t / 1.2);
        offset.x = Math.sin(t * 0.38) * 18 * easeIn;
        offset.y = Math.sin(t * 0.27) * 10 * easeIn;
      } else {
        offset.x *= 0.88; offset.y *= 0.88;
        if (Math.abs(offset.x) < .01) offset.x = 0;
        if (Math.abs(offset.y) < .01) offset.y = 0;
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
          const x = (i * 47 + time * 0.016) % 800,
            y = (i * 83 + time * 0.08) % 640;
          this.weather.lineBetween(x, y, x - 2, y + 7);
        }
      }
      const hour = new Date().getHours(),
        night = hour >= 19 || hour < 6;
      this.shade.setAlpha(night ? 0.25 : 0);
      if (paused || isTextFocus(document.activeElement)) {
        this.path = [];
        this.player.anims.stop();
        return;
      }
      if (this.busy) return;
      let dx = 0,
        dy = 0;
      if (pressed.has("arrowup") || pressed.has("w")) dy = -1;
      else if (pressed.has("arrowdown") || pressed.has("s")) dy = 1;
      else if (pressed.has("arrowleft") || pressed.has("a")) dx = -1;
      else if (pressed.has("arrowright") || pressed.has("d")) dx = 1;
      if (dx || dy) {
        this.path = [];
        this.pendingTarget = null;
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
        const near = this.activeNpcs.find(
          (n) =>
            Math.abs(n.homeX - this.tileX) + Math.abs(n.homeY - this.tileY) <=
            2,
        );
        const obj = this.objects.find(
          (o) => Math.abs(o.x - this.tileX) + Math.abs(o.y - this.tileY) <= 2,
        );
        callbacks.hint(
          near
            ? `E · 与${near.npc.name}对话`
            : obj
              ? `E · ${obj.id === "library_door" ? "进入图书馆" : obj.id === "library_exit" ? "离开图书馆" : "互动"}`
              : "",
        );
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
      }
    },
    setNpcs(value) {
      if (JSON.stringify(value) !== JSON.stringify(currentNpcs)) {
        currentNpcs = value;
        if (scene?.player) scene.build();
      }
    },
    setFlags(value) {
      currentFlags = value;
    },
    direction(key, down) {
      if (down) pressed.add(key);
      else pressed.delete(key);
    },
    interact() {
      scene?.interact();
    },
    guide(key) {
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
  };
}
