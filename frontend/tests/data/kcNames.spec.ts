import { describe, expect, it } from 'vitest';
import { kcDisplayName, humanizeKcText } from '@/data/kcNames';

describe('kcDisplayName', () => {
  it('resolves full and short KC codes to Chinese names', () => {
    expect(kcDisplayName('PY43-KC-002')).toBe('计算工具的演变');
    expect(kcDisplayName('KC-002')).toBe('计算工具的演变');
  });

  it('falls back gracefully', () => {
    expect(kcDisplayName(undefined)).toBe('本知识点');
    expect(kcDisplayName('PY43-KC-999')).toBe('相关知识点');
    expect(kcDisplayName('KC-1000')).toBe('相关知识点');
  });
});

describe('humanizeKcText', () => {
  it('strips redundant 「（KC-00X）」 title suffixes', () => {
    expect(humanizeKcText('从算盘到 ENIAC（KC-002）')).toBe('从算盘到 ENIAC');
  });

  it('replaces inline KC codes with bracketed names', () => {
    // 源文本里编码两侧的空格按原样保留（无害），关键是不再出现原始编码。
    const out = humanizeKcText('工坊里给 KC-003 生成讲义');
    expect(out).toContain('「计算思维的方法与特征」');
    expect(out).not.toMatch(/KC-\d{3}/);
  });

  it('handles empty / undefined', () => {
    expect(humanizeKcText('')).toBe('');
    expect(humanizeKcText(undefined)).toBe('');
  });

  it('never leaks a raw KC code for known codes', () => {
    const out = humanizeKcText('完成 PY43-KC-031 与 KC-040 练习');
    expect(out).not.toMatch(/KC-\d{3}/);
    expect(out).toContain('for 循环与遍历');
    expect(out).toContain('列表基本操作');
  });

  it('replaces unknown KC codes with a safe label', () => {
    const out = humanizeKcText('暂未收录 PY43-KC-999 和 KC-1000');
    expect(out.match(/「相关知识点」/g)).toHaveLength(2);
    expect(out).not.toMatch(/KC-\d+/);
  });
});
