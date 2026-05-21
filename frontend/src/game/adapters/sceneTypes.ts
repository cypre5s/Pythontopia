import type { Direction } from '@api/dto/Direction';

export interface RectangleLike {
  setStrokeStyle(width: number, color: number, alpha?: number): RectangleLike;
  setInteractive(): RectangleLike;
  on(event: string, callback: () => void): RectangleLike;
  setDepth(depth: number): RectangleLike;
  x: number;
  y: number;
}

export interface TextLike {
  setOrigin(x: number, y?: number): TextLike;
  setDepth(depth: number): TextLike;
}

export interface ImageLike {
  setOrigin(x: number, y?: number): ImageLike;
  setDepth(depth: number): ImageLike;
  setScale(scale: number): ImageLike;
  setAlpha(alpha: number): ImageLike;
  x: number;
  y: number;
}

export interface SceneAddLike {
  rectangle(
    x: number,
    y: number,
    width: number,
    height: number,
    fillColor: number,
    fillAlpha?: number,
  ): RectangleLike;
  text(x: number, y: number, text: string, style?: Record<string, string>): TextLike;
  image(x: number, y: number, texture: string): ImageLike;
  tileSprite(x: number, y: number, width: number, height: number, texture: string): ImageLike;
  grid(
    x: number,
    y: number,
    width: number,
    height: number,
    cellWidth: number,
    cellHeight: number,
    fillColor?: number,
    fillAlpha?: number,
    outlineFillColor?: number,
    outlineFillAlpha?: number,
  ): RectangleLike;
}

export interface KeyboardCursorLike {
  up: { isDown: boolean };
  down: { isDown: boolean };
  left: { isDown: boolean };
  right: { isDown: boolean };
}

export interface KeyboardKeyLike {
  isDown: boolean;
}

export interface KeyboardLike {
  createCursorKeys(): KeyboardCursorLike;
  addKey(key: string): KeyboardKeyLike;
}

export interface InputLike {
  keyboard: KeyboardLike | null;
}

export interface SceneTimeLike {
  now: number;
}

export interface SceneCameraLike {
  main: {
    startFollow(target: { x: number; y: number }, roundPixels?: boolean): void;
    setBounds(x: number, y: number, width: number, height: number): void;
  };
}

export interface SceneWorldLike {
  setBounds(x: number, y: number, width: number, height: number): void;
}

export interface ScenePhysicsLike {
  world: SceneWorldLike;
}

export interface SceneLike {
  add: SceneAddLike;
  input: InputLike;
  time: SceneTimeLike;
  cameras: SceneCameraLike;
  physics?: ScenePhysicsLike;
}

export interface PlayerSpriteHandle {
  x: number;
  y: number;
  tileX: number;
  tileY: number;
  direction: Direction;
  moveToTile(tileX: number, tileY: number, direction: Direction): void;
}
