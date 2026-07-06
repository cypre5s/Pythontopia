function clamp01(value: number): number {
  if (Number.isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(1, value));
}

/** 0..1 得分 → 百分比文本，如 0.73 -> "73%"。 */
export function toPercent(score: number, digits = 0): string {
  return `${(clamp01(score) * 100).toFixed(digits)}%`;
}

/** 0..1 得分 → 进度条宽度样式值，如 0.73 -> "73%"。 */
export function percentWidth(score: number): string {
  return `${clamp01(score) * 100}%`;
}
