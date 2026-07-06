// 昼夜 tint 通过结构化入口隔离在 adapter。
//
// 实现要点：
// - 4 段制（morning / noon / evening / night），与课表节奏对齐。
// - 由 worldStore.session 的 inGameMinutes 推导，不引入新 DTO 字段。
// - noon 也调一次 setTint，保持调用一致；测试更易断言「每次都会写入」。
import type { SceneLike } from './sceneTypes';

export type TimeOfDay = 'morning' | 'noon' | 'evening' | 'night';

const TINTS: Record<TimeOfDay, number> = {
  morning: 0xfff4d0,
  noon: 0xffffff,
  evening: 0xffb070,
  night: 0x506080,
};

interface PhaserCamera {
  setTint?: (color: number) => void;
}

interface PhaserScene {
  cameras: { main: PhaserCamera };
}

export function applyTimeOfDay(scene: SceneLike, time: TimeOfDay): void {
  const phaserScene = scene as unknown as PhaserScene;
  const target = TINTS[time];
  phaserScene.cameras.main.setTint?.(target);
}

// inGameMinutes（0~24*60）→ TimeOfDay。
// 边界分段：05:00-10:59 morning, 11:00-15:59 noon, 16:00-19:59 evening, 其它 night。
// 这样夜晚 8 小时、黄昏 4 小时、白天 5 小时、清晨 6 小时，演示节奏清晰。
export function deriveTimeOfDay(input: { inGameMinutes?: number | null; timeOfDay?: string | null }): TimeOfDay {
  const explicit = normalizeTimeOfDay(input.timeOfDay);
  if (explicit) return explicit;
  const minutes = typeof input.inGameMinutes === 'number' ? input.inGameMinutes : 12 * 60;
  const wrapped = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60);
  const hour = Math.floor(wrapped / 60);
  if (hour >= 5 && hour < 11) return 'morning';
  if (hour >= 11 && hour < 16) return 'noon';
  if (hour >= 16 && hour < 20) return 'evening';
  return 'night';
}

function normalizeTimeOfDay(input: string | null | undefined): TimeOfDay | null {
  const value = String(input ?? '').toLowerCase();
  if (value === 'morning') return 'morning';
  if (value === 'noon' || value === 'day') return 'noon';
  if (value === 'evening' || value === 'dusk') return 'evening';
  if (value === 'night') return 'night';
  return null;
}

export const __testing = { TINTS };
