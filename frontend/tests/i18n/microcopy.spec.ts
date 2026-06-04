/**
 * Pythontopia R-12 i18n 微文案验收测试
 *
 * 测试目的：保证 zh-CN / en-US 文案表与 docs/stardew/07-ui-microcopy.md 规范
 * 严格一致；任何对 i18n 文件的修改若违反字符上限 / 禁用词 / key 集合契约，
 * 必须在 CI 阶段被拦截。
 *
 * 设计依据: /home/cypress/Pythontopia/docs/stardew/07-ui-microcopy.md
 *
 * 测试矩阵（≥ 8 个用例，实际 23+）：
 *   1. zh-CN 与 en-US 含相同的扁平 key 集合
 *   2. 5 个抽样按钮文案符合规范（≤20 宽度 / 不含「！」/ 不含禁用词 / 不含「您」）
 *   3. 5 个抽样 toast 文案符合规范
 *   4. t() 找不到 key 时分别走 fallback / key 兜底
 *   5. setLocale 切换生效（当前 locale + 显式覆盖）
 *   6. tList 按 prefix 收集（按钮 30 / toast 20 / network 错误 2）
 *   7. 全表禁用词审计（笨/傻/蠢/白痴/弱智/智障 / 您 / ！）
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  DEFAULT_LOCALE,
  getLocale,
  messages,
  setLocale,
  t,
  tList,
} from '@/i18n';

const BANNED_WORDS = ['笨', '傻', '蠢', '白痴', '弱智', '智障'] as const;

/** 按钮文案宽度上限（汉字 = 1 宽，ASCII = 0.5 宽，向上取整）。 */
const MAX_WIDTH_BUTTON = 20;
/** Toast 文案宽度上限。 */
const MAX_WIDTH_TOAST = 20;

const BUTTON_SAMPLES = [
  ['hud.btn.confirm', '确认'],
  ['hud.btn.cancel', '取消'],
  ['hud.btn.close', '关闭'],
  ['hud.btn.retry', '再试一次'],
  ['hud.btn.startGame', '开始游戏'],
] as const;

const TOAST_SAMPLES = [
  'toast.questDone',
  'toast.energyLow',
  'toast.offline',
  'toast.saveOk',
  'toast.judging',
] as const;

/**
 * 计算微文案的显示宽度：去掉 `{placeholder}` 后，全角字符（含中文标点）= 1，
 * ASCII 字符 = 0.5，最终向上取整。与设计文档 § 2 字符数定义一致。
 */
function countDisplayWidth(text: string): number {
  const stripped = text.replace(/\{[^}]+\}/g, '');
  let width = 0;
  for (const ch of stripped) {
    const code = ch.codePointAt(0) ?? 0;
    // ASCII (含控制字符) 算半角 0.5；非 ASCII（汉字、全角符号、emoji）算 1。
    // 用 code-point 比较替代正则，避免 eslint no-control-regex 警告。
    width += code <= 0x7f ? 0.5 : 1;
  }
  return Math.ceil(width);
}

beforeEach(() => {
  setLocale(DEFAULT_LOCALE);
});

afterEach(() => {
  setLocale(DEFAULT_LOCALE);
});

describe('i18n key 集合一致性', () => {
  it('zh-CN 与 en-US 扁平后 key 集合完全相同', () => {
    const zhKeys = Object.keys(messages['zh-CN']).sort();
    const enKeys = Object.keys(messages['en-US']).sort();
    expect(zhKeys).toEqual(enKeys);
    expect(zhKeys.length).toBeGreaterThanOrEqual(92);
  });
});

describe('zh-CN 按钮文案抽样（5 项）', () => {
  it.each(BUTTON_SAMPLES)('%s 等于「%s」并满足微文案规范', (key, expected) => {
    const value = t(key, 'zh-CN');
    expect(value).toBe(expected);
    expect(countDisplayWidth(value)).toBeLessThanOrEqual(MAX_WIDTH_BUTTON);
    expect(value).not.toContain('！');
    expect(value).not.toContain('!');
    expect(value).not.toContain('您');
    for (const word of BANNED_WORDS) {
      expect(value, `${key} 命中禁用词 ${word}`).not.toContain(word);
    }
  });
});

describe('zh-CN toast 文案抽样（5 项）', () => {
  it.each(TOAST_SAMPLES)('%s 满足 toast 微文案规范', (key) => {
    const value = t(key, 'zh-CN');
    expect(value, `${key} 未在 zh-CN 中定义`).not.toBe(key);
    expect(value.length).toBeGreaterThan(0);
    expect(countDisplayWidth(value)).toBeLessThanOrEqual(MAX_WIDTH_TOAST);
    expect(value).not.toContain('！');
    expect(value).not.toContain('!');
    expect(value).not.toContain('您');
    for (const word of BANNED_WORDS) {
      expect(value, `${key} 命中禁用词 ${word}`).not.toContain(word);
    }
  });
});

describe('t() 兜底行为', () => {
  it('找不到 key 且提供 fallback 时返回 fallback', () => {
    expect(t('nonexistent.key.xxx', 'zh-CN', '兜底文案')).toBe('兜底文案');
  });

  it('找不到 key 且未提供 fallback 时返回 key 本身', () => {
    expect(t('nonexistent.key.xxx', 'zh-CN')).toBe('nonexistent.key.xxx');
  });

  it('找到 key 时忽略 fallback 返回真实文案', () => {
    expect(t('hud.btn.confirm', 'zh-CN', '兜底')).toBe('确认');
  });
});

describe('setLocale / getLocale 切换', () => {
  it('默认 locale 为 zh-CN', () => {
    expect(getLocale()).toBe('zh-CN');
  });

  it('切到 en-US 后 t() 默认走英文', () => {
    setLocale('en-US');
    expect(getLocale()).toBe('en-US');
    expect(t('hud.btn.confirm')).toBe('Confirm');
    expect(t('hud.btn.retry')).toBe('Try again');
  });

  it('显式 locale 参数覆盖当前 locale', () => {
    setLocale('en-US');
    expect(t('hud.btn.confirm', 'zh-CN')).toBe('确认');
    expect(t('hud.btn.confirm', 'en-US')).toBe('Confirm');
  });
});

describe('tList 按 prefix 收集', () => {
  it('tList("hud.btn") 命中 30 条按钮', () => {
    const list = tList('hud.btn', 'zh-CN');
    expect(list).toHaveLength(30);
    expect(list).toContain('确认');
    expect(list).toContain('再试一次');
    expect(list).toContain('开始游戏');
  });

  it('tList("toast") 命中 20 条 toast', () => {
    const list = tList('toast', 'zh-CN');
    expect(list).toHaveLength(20);
    expect(list).toContain('已保存');
  });

  it('tList("error.network") 仅收集 network 错误 2 条', () => {
    const list = tList('error.network', 'zh-CN');
    expect(list).toHaveLength(2);
  });

  it('tList("empty") 命中 10 条空状态', () => {
    const list = tList('empty', 'zh-CN');
    expect(list).toHaveLength(10);
  });

  it('tList("npc") 命中 7 个 NPC 招呼语', () => {
    const list = tList('npc', 'zh-CN');
    expect(list).toHaveLength(7);
  });
});

describe('全表禁用词与标点审计', () => {
  it('zh-CN 全表不含人格贬低词', () => {
    const flat = JSON.stringify(messages['zh-CN']);
    for (const word of BANNED_WORDS) {
      expect(flat, `禁用词 ${word} 出现在 zh-CN 文案中`).not.toContain(word);
    }
  });

  it('zh-CN 全表不含「您」（学生端文案统一用「你」）', () => {
    const flat = JSON.stringify(messages['zh-CN']);
    expect(flat).not.toContain('您');
  });

  it('zh-CN 全表不含「！」感叹号（任务约束：不感叹号）', () => {
    const flat = JSON.stringify(messages['zh-CN']);
    expect(flat).not.toContain('！');
    expect(flat).not.toContain('!');
  });
});
