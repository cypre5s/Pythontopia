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
  setFrame?(frame: number | string): ImageLike;
  x: number;
  y: number;
}

export interface EllipseLike {
  setDepth(depth: number): EllipseLike;
  x: number;
  y: number;
}

export interface SpriteLike {
  setOrigin(x: number, y?: number): SpriteLike;
  setDepth(depth: number): SpriteLike;
  setScale(scale: number): SpriteLike;
  setFrame(frame: number | string): SpriteLike;
  play(key: string, ignoreIfPlaying?: boolean): SpriteLike;
  stop(): SpriteLike;
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
  text(x: number, y: number, text: string, style?: Record<string, unknown>): TextLike;
  image(x: number, y: number, texture: string, frame?: number | string): ImageLike;
  tileSprite(
    x: number,
    y: number,
    width: number,
    height: number,
    texture: string,
    frame?: number | string,
  ): ImageLike;
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

export interface SceneCameraMainLike {
  startFollow(
    target: { x: number; y: number },
    roundPixels?: boolean,
    lerpX?: number,
    lerpY?: number,
  ): void;
  setBounds(x: number, y: number, width: number, height: number): void;
  setZoom?(zoom: number): void;
}

export interface SceneCameraLike {
  main: SceneCameraMainLike;
}

export interface SceneWorldLike {
  setBounds(x: number, y: number, width: number, height: number): void;
}

export interface ScenePhysicsLike {
  world: SceneWorldLike;
}

export interface SceneEventsLike {
  on(event: string, callback: () => void): void;
  once?(event: string, callback: () => void): void;
  off?(event: string, callback: () => void): void;
}

export interface SceneLike {
  add: SceneAddLike;
  input: InputLike;
  time: SceneTimeLike;
  cameras: SceneCameraLike;
  physics?: ScenePhysicsLike;
  events?: SceneEventsLike;
}

export interface PlayerSpriteHandle {
  x: number;
  y: number;
  tileX: number;
  tileY: number;
  direction: Direction;
  moveToTile(tileX: number, tileY: number, direction: Direction): void;
}
