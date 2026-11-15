import Phaser from "phaser";
import type { Npc, Player } from "../../shared/types";
import type { WorldRuntime } from "../config/worldConfig";
import type { WorldCallbacks, WorldHandle, WorldState } from "../world/types";
import { createTownScene, type TownScene } from "../world/scene";
import { bindWorldKeyboard } from "../world/keyboard";

let instance: Phaser.Game | null = null;
export function createWorld(
  root: HTMLElement,
  initial: Player,
  npcs: Npc[],
  flags: string[],
  callbacks: WorldCallbacks,
  runtime?: WorldRuntime,
): WorldHandle {
  if (instance) throw new Error("一个游戏会话只能创建一个世界实例");
  const state: WorldState = {
    drifting: false,
    driftStart: 0,
    destroyed: false,
    paused: false,
    npcs,
    flags,
    runtime,
    pressed: new Set(),
  };
  const ambient = new Audio("/assets/pixel/bgm/track_01.mp3");
  ambient.loop = true;
  ambient.volume = 0.15;
  let scene: TownScene;
  const unbindKeyboard = bindWorldKeyboard(root, state, () => {
    void scene?.interact();
  });
  const Scene = createTownScene(initial, state, callbacks, (value) => {
    scene = value;
  });
  instance = new Phaser.Game({
    type: Phaser.AUTO,
    parent: root,
    width: root.clientWidth,
    height: root.clientHeight,
    backgroundColor: "#233e2d",
    pixelArt: true,
    roundPixels: true,
    banner: false,
    scene: [Scene],
    scale: { mode: Phaser.Scale.RESIZE, autoCenter: Phaser.Scale.CENTER_BOTH },
  });
  return {
    destroy() {
      state.destroyed = true;
      ambient.pause();
      ambient.src = "";
      unbindKeyboard();
      instance?.destroy(true);
      instance = null;
    },
    setWelcomeDrift(value) {
      state.drifting = value;
      state.driftStart = 0;
    },
    setPaused(value) {
      state.paused = value;
      state.pressed.clear();
      if (scene) {
        scene.path = [];
        scene.pendingTarget = null;
        if (value) scene.travelTarget = null;
        scene.refreshHint();
        if (scene.player) callbacks.mapPosition?.(scene.mapPosition());
      }
    },
    setNpcs(value) {
      if (JSON.stringify(value) !== JSON.stringify(state.npcs)) {
        state.npcs = value;
        if (scene?.player) {
          if (scene.queueCharacterSprites()) {
            scene.load.once("complete", () => {
              if (!state.destroyed) scene.build();
            });
            scene.load.start();
          } else scene.build();
        }
      }
    },
    setFlags(value) {
      if (JSON.stringify(value) !== JSON.stringify(state.flags)) {
        state.flags = value;
        if (scene?.player) scene.build();
      }
    },
    setRuntime(value) {
      if (JSON.stringify(value) !== JSON.stringify(state.runtime)) {
        state.runtime = value;
        if (scene?.player) scene.build();
      }
    },
    setPlayer(value) {
      if (scene?.player && value.map_key !== scene.mapKey)
        scene.changeMap(value);
    },
    direction(key, down) {
      if (down) state.pressed.add(key);
      else state.pressed.delete(key);
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
    setVolume(value) {
      ambient.volume = Number.isFinite(value)
        ? Math.min(1, Math.max(0, value))
        : 0.15;
    },
    getMapPosition() {
      return scene?.player ? scene.mapPosition() : undefined;
    },
  };
}
