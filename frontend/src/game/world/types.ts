import type Phaser from "phaser";
import type { Npc, Player } from "../../shared/types";
import type { WorldRuntime, WorldTarget } from "../config/worldConfig";
import type { RenderMap } from "../config/worldRender";
export interface WorldHandle {
  destroy(): void;
  setPaused(value: boolean): void;
  setWelcomeDrift?(value: boolean): void;
  setNpcs(npcs: Npc[]): void;
  setFlags(flags: string[]): void;
  setRuntime(runtime: WorldRuntime): void;
  setPlayer(player: Player): void;
  direction(key: string, down: boolean): void;
  interact(): void;
  guide(npcKey: string): void;
  mute(value: boolean): void;
  setVolume(value: number): void;
  getMapPosition?(): Player | undefined;
}
export interface WorldCallbacks {
  cameraReady?: (view: {
    x: number;
    y: number;
    zoom: number;
    map: string;
  }) => void;
  position: (player: Player) => void;
  mapPosition?: (player: Player) => void;
  transition: (portalId: string, source: Player) => Promise<Player | null>;
  interact: (target: string, type: "npc" | "object") => void;
  enterRegion?: (target: string, player: Player) => Promise<void>;
  hint: (text: string) => void;
  progress: (value: number) => void;
  error: (text: string) => void;
}

export interface WorldState {
  npcs: Npc[];
  flags: string[];
  runtime?: WorldRuntime;
  paused: boolean;
  destroyed: boolean;
  drifting: boolean;
  driftStart: number;
  pressed: Set<string>;
}

// Surface shared by rendering and interaction systems, without importing the
// concrete scene or the Vue-facing adapter back into these systems.
export interface WorldSceneSurface extends Phaser.Scene {
  player: Phaser.GameObjects.Sprite;
  mapKey: string;
  tileX: number;
  tileY: number;
  direction: string;
  busy: boolean;
  path: { x: number; y: number }[];
  collision: number[];
  width: number;
  height: number;
  activeNpcs: {
    npc: Npc;
    sprite: Phaser.GameObjects.Sprite;
    label: Phaser.GameObjects.Text;
    homeX: number;
    homeY: number;
  }[];
  shade: Phaser.GameObjects.Rectangle;
  weather: Phaser.GameObjects.Graphics;
  saveTimer: number;
  pendingTarget: string | null;
  travelTarget: string | null;
  objects: WorldTarget[];
  transitioning: boolean;
  renderMaps: Map<string, RenderMap>;
  queuedTextures: Set<string>;
  lastHint: string;
  enteredRegions: Set<string>;
  build(): void;
  resize(): void;
  approach(key: string, preferredPoint?: { x: number; y: number }): void;
  nearbyTarget(preferredTarget?: string): WorldTarget | undefined;
  interact(preferredTarget?: string): Promise<void>;
  blocked(x: number, y: number): boolean;
  changeMap(player: Player): void;
  position(): Player;
  mapPosition(): Player;
  save(): void;
  checkRegionEntry(): Promise<void>;
}
