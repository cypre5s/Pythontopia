// 隔离原因：保持业务层无 Phaser 依赖，禁止业务代码直接 import phaser。
// 单例化原因：GameCanvas / FieldCanvas 等多个挂载点可能同时 mount，必须保证全局只有一个 Phaser.Game 实例。
import Phaser from 'phaser';

export type PhaserSceneConfig = Phaser.Types.Scenes.SettingsConfig;

let gameInstance: Phaser.Game | null = null;

// draw call 采样状态：WebGL 渲染器无内置 draw call 计数，这里在 adapter 层包一层
// gl.drawArrays/drawElements 自增，PRE_RENDER 清零、POST_RENDER 落值，得到「上一帧
// 真实 GPU draw call 数」。Canvas 渲染器回退用内置 drawCount（每帧渲染的 GameObject 数）。
let frameDrawCalls = 0;
let lastDrawCalls: number | null = null;
let drawCallsInstrumented = false;

export interface PhaserGameHandle {
  destroy(removeCanvas: boolean): void;
}

export function createPhaserGame(rootEl: HTMLElement, scenes: PhaserSceneConfig[]): PhaserGameHandle {
  if (gameInstance) {
    return { destroy: (removeCanvas) => destroyPhaserGame(removeCanvas) };
  }
  // 全屏沉浸：用 RESIZE 模式让 canvas 永远等于父容器尺寸（WorldCanvas
  // 的 ResizeObserver 会 scale.resize 推送视口变化）。pixelArt + roundPixels 保证
  // 整数倍像素缩放，配合 WorldScene 的 RENDER_SCALE × CAMERA_ZOOM 共同决定 tile
  // 显示尺寸。
  // 初始尺寸用当前 rootEl 的真实 boundingClientRect，避免老的 960×640 黑边一闪。
  const rect = rootEl.getBoundingClientRect();
  const initWidth = Math.max(320, Math.floor(rect.width || window.innerWidth));
  const initHeight = Math.max(240, Math.floor(rect.height || window.innerHeight));
  gameInstance = new Phaser.Game({
    type: Phaser.AUTO,
    parent: rootEl,
    // 关掉 Phaser 启动横幅（控制台噪音：每次挂载都会打印 "Phaser v3.87 …"）。
    banner: false,
    width: initWidth,
    height: initHeight,
    backgroundColor: '#1c2b18',
    pixelArt: true,
    roundPixels: true,
    scene: scenes,
    // 移动用 tween + 手写碰撞网格，全程不使用 Arcade 物理体；
    // 移除 arcade physics 省掉每帧物理步。world/camera 边界由
    // camera.setBounds 承担，WorldScene 里 this.physics?.world 调用均带 ?. 兜底。
    render: {
      // 提示浏览器优先用独显，减少集显掉帧。
      powerPreference: 'high-performance',
    },
    scale: {
      // RESIZE：canvas 跟随父容器尺寸（WorldCanvas ResizeObserver 触发 scale.resize），
      // 不再做信箱式裁切；camera 仍然居中跟随 player，所以画面焦点不会漂移。
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH,
    },
  });
  instrumentDrawCalls(gameInstance);
  return { destroy: destroyPhaserGame };
}

export function getPhaserGame(): Phaser.Game | null {
  return gameInstance;
}

/** 离开 /world 时暂停主循环（配合 App KeepAlive，避免后台空转）。 */
export function pausePhaserGame(): void {
  if (!gameInstance) return;
  gameInstance.loop.sleep();
  try {
    gameInstance.scene.pause('WorldScene');
  } catch {
    // 场景尚未创建或已 shutdown
  }
}

/** 回到 /world 时恢复主循环与当前地图场景。 */
export function resumePhaserGame(): void {
  if (!gameInstance) return;
  gameInstance.loop.wake();
  try {
    gameInstance.scene.resume('WorldScene');
  } catch {
    // 同上
  }
}

/**
 * 只唤醒主循环，不动任何场景的暂停状态。
 *
 * 给「借用 /world 的 canvas 跑别的场景」的页面用（如 /mainline-map 的
 * ChapterMapScene）：pausePhaserGame() 的 loop.sleep() 会直接 raf.stop()，
 * 不唤醒则借来的场景一帧都渲染不出来（画面定格在小镇最后一帧）。
 * 但也不能整个 resumePhaserGame —— 那会把 WorldScene 一起恢复，它的 update()
 * 会在地图页背后继续吃 WASD/E 输入（走位、甚至 router.push 跳走）。
 */
export function resumePhaserLoop(): void {
  if (!gameInstance) return;
  gameInstance.loop.wake();
}

// 给渲染器装上 draw call 计数。仅在创建真实 Game 后调用一次（测试环境不会创建
// Game，故不受影响）。WebGL 路径只新增一次自增开销，对帧率影响可忽略。
function instrumentDrawCalls(game: Phaser.Game): void {
  if (drawCallsInstrumented) {
    return;
  }
  const renderer = game.renderer as unknown as {
    gl?: WebGLRenderingContext;
    drawCount?: number;
  };
  const gl = renderer?.gl;
  if (gl && typeof gl.drawArrays === 'function' && typeof gl.drawElements === 'function') {
    const origDrawArrays = gl.drawArrays.bind(gl);
    const origDrawElements = gl.drawElements.bind(gl);
    gl.drawArrays = ((...args: Parameters<WebGLRenderingContext['drawArrays']>): void => {
      frameDrawCalls += 1;
      origDrawArrays(...args);
    }) as WebGLRenderingContext['drawArrays'];
    gl.drawElements = ((...args: Parameters<WebGLRenderingContext['drawElements']>): void => {
      frameDrawCalls += 1;
      origDrawElements(...args);
    }) as WebGLRenderingContext['drawElements'];
    game.events.on(Phaser.Core.Events.PRE_RENDER, () => {
      frameDrawCalls = 0;
    });
    game.events.on(Phaser.Core.Events.POST_RENDER, () => {
      lastDrawCalls = frameDrawCalls;
    });
    drawCallsInstrumented = true;
  } else if (renderer && typeof renderer.drawCount === 'number') {
    game.events.on(Phaser.Core.Events.POST_RENDER, () => {
      lastDrawCalls = typeof renderer.drawCount === 'number' ? renderer.drawCount : null;
    });
    drawCallsInstrumented = true;
  }
}

/**
 * 性能诊断快照：纯数据 DTO，供 HUD 层（PerfHud.vue）轮询展示。
 *
 * 隔离取舍：所有 Phaser 运行时对象（loop / scene / renderer / textures）
 * 只在本 adapter 层读取，业务/HUD 层不直接 import phaser，符合 AGENTS
 * 「Phaser 动态对象必须隔离在 adapter 层」。
 */
export interface PerfSnapshot {
  /** Phaser 主循环实际帧率（actualFps，已取整）。 */
  fps: number;
  /** 上一帧耗时（毫秒，delta）。 */
  frameMs: number;
  /** 所有活动场景 display list 上的 GameObject 总数（对象过多 = 渲染/遍历压力）。 */
  objects: number;
  /** 所有活动场景当前活动 tween 数（tween 过多 = 每帧补间开销）。 */
  tweens: number;
  /** 纹理缓存条目数（合图不足时此值偏大，draw call 也会高）。 */
  textures: number;
  /** canvas 实际后备像素宽（高分屏未限制时这里会非常大 = 填充率瓶颈）。 */
  canvasW: number;
  /** canvas 实际后备像素高。 */
  canvasH: number;
  /** 设备像素比（DPR）。 */
  dpr: number;
  /** JS 堆占用（MB，仅 Chromium 暴露 performance.memory 时有值，否则 null）。 */
  heapMB: number | null;
  /** 渲染后端类型。 */
  rendererType: 'WEBGL' | 'CANVAS' | 'HEADLESS' | 'UNKNOWN';
  /** 当前活动场景 key 列表。 */
  activeScenes: string[];
  /**
   * 上一帧真实 draw call 数：WebGL 为 gl.drawArrays/drawElements 调用次数；
   * Canvas 渲染器为内置 drawCount（每帧渲染 GameObject 数）；尚未采样到为 null。
   */
  drawCalls: number | null;
}

export function getPerfSnapshot(): PerfSnapshot | null {
  const game = gameInstance;
  if (!game) {
    return null;
  }

  let objects = 0;
  let tweens = 0;
  const activeScenes: string[] = [];
  try {
    const scenes = game.scene.getScenes(true);
    for (const scene of scenes) {
      const key = (scene.sys as unknown as { settings?: { key?: string } })?.settings?.key;
      if (typeof key === 'string') {
        activeScenes.push(key);
      }
      objects += scene.children?.list?.length ?? 0;
      const tw = scene.tweens as unknown as { getTweens?: () => unknown[] };
      tweens += tw?.getTweens?.()?.length ?? 0;
    }
  } catch {
    // 场景管理器在 restart / HMR / shutdown 瞬态可能不可用，诊断工具静默兜底
  }

  let textures = 0;
  try {
    textures = Object.keys((game.textures as unknown as { list?: object })?.list ?? {}).length;
  } catch {
    // textures 缓存偶发不可用，忽略
  }

  let rendererType: PerfSnapshot['rendererType'] = 'UNKNOWN';
  const rtype = (game.renderer as unknown as { type?: number })?.type;
  if (rtype === Phaser.WEBGL) {
    rendererType = 'WEBGL';
  } else if (rtype === Phaser.CANVAS) {
    rendererType = 'CANVAS';
  } else if (rtype === Phaser.HEADLESS) {
    rendererType = 'HEADLESS';
  }

  const canvas = game.canvas as HTMLCanvasElement | null;

  let heapMB: number | null = null;
  try {
    const mem = (performance as unknown as { memory?: { usedJSHeapSize?: number } }).memory;
    if (mem?.usedJSHeapSize != null) {
      heapMB = Math.round(mem.usedJSHeapSize / (1024 * 1024));
    }
  } catch {
    // performance.memory 非标准，缺失即保持 null
  }

  return {
    fps: Math.round(game.loop?.actualFps ?? 0),
    frameMs: Math.round((game.loop?.delta ?? 0) * 100) / 100,
    objects,
    tweens,
    textures,
    canvasW: canvas?.width ?? 0,
    canvasH: canvas?.height ?? 0,
    dpr: Math.round((typeof window !== 'undefined' ? window.devicePixelRatio : 1) * 100) / 100,
    heapMB,
    rendererType,
    activeScenes,
    drawCalls: lastDrawCalls,
  };
}

export function destroyPhaserGame(removeCanvas = true): void {
  if (gameInstance) {
    gameInstance.destroy(removeCanvas);
    gameInstance = null;
  }
  frameDrawCalls = 0;
  lastDrawCalls = null;
  drawCallsInstrumented = false;
}
