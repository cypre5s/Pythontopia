/**
 * Pythontopia R-12 tokens-r12.css 验收测试
 *
 * 测试目的：保证 R-12 stardew 视觉系统的 token 文件严格符合设计文档约束，
 * 任何对 `tokens-r12.css` 的修改若违反命名/取值/层级规范，必须在 CI 阶段被拦截。
 *
 * 设计依据: /home/cypress/Pythontopia/docs/stardew/01-palette-tokens-css.md
 *
 * 测试矩阵：
 *   1. 61 个 token 名全部存在（按 9 个分类逐项校验）
 *   2. 字号 5 个全部是整数 px
 *   3. 间距 6 个全部是 4 的倍数（含 0）
 *   4. z-index 5 个严格递增（hud < popover < modal < toast < debug）
 *   5. 暗色模式两个 block 均覆写 ≥5 个反相变量
 *   6. 命名空间隔离：全部 `--pyt-r12-` 前缀，不污染旧 `--pyt-*`
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeAll, describe, expect, it } from 'vitest';

const TOKENS_FILE = resolve(process.cwd(), 'src/styles/tokens-r12.css');

/** 9 个分类下应当被定义的 token 名（不含 --pyt-r12- 前缀），共 61 个。 */
const EXPECTED_TOKENS = {
  palette: [
    'color-primary-50',
    'color-primary-100',
    'color-primary-500',
    'color-primary-900',
    'color-accent-gold',
    'color-accent-purple',
    'color-accent-blue',
    'color-accent-pink',
    'color-success',
    'color-warning',
    'color-danger',
    'color-info',
    'color-neutral',
    'color-gray-50',
    'color-gray-100',
    'color-gray-300',
    'color-gray-500',
    'color-gray-700',
    'color-gray-900',
    'overlay-soft',
    'overlay-medium',
    'overlay-strong',
    'overlay-warm',
    'overlay-night',
    'overlay-rain',
  ],
  font: [
    'font-pixel-cn',
    'font-pixel-en',
    'font-mono-fallback',
    'font-weight-regular',
    'font-weight-bold',
  ],
  size: ['font-size-xs', 'font-size-sm', 'font-size-md', 'font-size-lg', 'font-size-xl'],
  space: ['space-0', 'space-1', 'space-2', 'space-3', 'space-4', 'space-5'],
  radius: ['radius-none', 'radius-sm', 'radius-md', 'radius-lg', 'radius-full'],
  shadow: ['shadow-card', 'shadow-popover', 'shadow-modal', 'shadow-inset'],
  z: ['z-hud', 'z-popover', 'z-modal', 'z-toast', 'z-debug'],
  motion: ['motion-fast', 'motion-base', 'motion-slow', 'motion-slowest'],
  focus: ['focus-ring', 'focus-ring-strong'],
} as const;

const ALL_EXPECTED = Object.values(EXPECTED_TOKENS).flat();

let cssText = '';

/**
 * 从 CSS 文本中抽取第一个顶层 :root { ... } 块（不含 @media / 属性选择器内嵌的覆写块）。
 * 用括号配平算法定位结束位置，避免被字符串值里的花括号误导（CSS token 值中无 {}）。
 */
function extractRootBlock(text: string): string {
  const headerRegex = /(^|\n)\s*:root\s*\{/m;
  const headerMatch = headerRegex.exec(text);
  if (!headerMatch) return '';
  const startIdx = headerMatch.index + headerMatch[0].lastIndexOf('{');
  let depth = 0;
  for (let i = startIdx; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) return text.slice(startIdx + 1, i);
    }
  }
  return '';
}

/**
 * 从 CSS 文本中抽取所有暗色模式相关的 block 内容：
 *   - @media (prefers-color-scheme: dark) { ... }
 *   - :root[data-theme='dark'] { ... } 或 [data-theme="dark"]
 * 用括号配平算法定位结束位置，正确处理 @media 内嵌选择器。
 */
function extractDarkBlocks(text: string): string[] {
  const blocks: string[] = [];
  const patterns = [
    /@media\s*\(\s*prefers-color-scheme\s*:\s*dark\s*\)\s*\{/g,
    /:root\s*\[\s*data-theme\s*=\s*['"]dark['"]\s*\]\s*\{/g,
  ];
  for (const pattern of patterns) {
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(text)) !== null) {
      const openIdx = match.index + match[0].length - 1;
      let depth = 0;
      for (let i = openIdx; i < text.length; i += 1) {
        const ch = text[i];
        if (ch === '{') depth += 1;
        else if (ch === '}') {
          depth -= 1;
          if (depth === 0) {
            blocks.push(text.slice(openIdx + 1, i));
            break;
          }
        }
      }
    }
  }
  return blocks;
}

/** 从一段 CSS 文本里提取所有 `--pyt-r12-xxx` 形式的变量名（去重）。 */
function listTokensIn(blockText: string): string[] {
  const seen = new Set<string>();
  const re = /--pyt-r12-([a-z0-9-]+)\s*:/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(blockText)) !== null) {
    seen.add(match[1]);
  }
  return [...seen];
}

/** 从某个 :root block 内取一个具体 token 的字符串值（首次出现）。 */
function readTokenValue(blockText: string, name: string): string | undefined {
  const re = new RegExp(`--pyt-r12-${name}\\s*:\\s*([^;]+);`);
  const match = re.exec(blockText);
  return match?.[1].trim();
}

beforeAll(() => {
  cssText = readFileSync(TOKENS_FILE, 'utf-8');
});

describe('tokens-r12.css 文件结构', () => {
  it('文件存在且非空', () => {
    expect(cssText.length).toBeGreaterThan(0);
  });

  it('全部变量名以 --pyt-r12- 前缀，不污染旧 --pyt- 命名空间', () => {
    const allDecls = cssText.match(/--[a-z][a-z0-9-]*\s*:/g) ?? [];
    const nonPrefixed = allDecls.filter((decl) => !decl.startsWith('--pyt-r12-'));
    expect(nonPrefixed).toEqual([]);
  });
});

describe('tokens-r12.css 61 个变量完整性', () => {
  it('合计应当声明 61 个不同的 token 名', () => {
    expect(ALL_EXPECTED).toHaveLength(61);
  });

  it(':root 主 block 至少定义 61 个 token', () => {
    const rootBlock = extractRootBlock(cssText);
    const defined = listTokensIn(rootBlock);
    expect(defined.length).toBeGreaterThanOrEqual(61);
  });

  it.each(ALL_EXPECTED)('token 已声明: --pyt-r12-%s', (name) => {
    const rootBlock = extractRootBlock(cssText);
    expect(rootBlock).toMatch(new RegExp(`--pyt-r12-${name}\\s*:`));
  });

  it('调色板分类合计 25 个', () => {
    expect(EXPECTED_TOKENS.palette).toHaveLength(25);
  });

  it('字体分类合计 5 个', () => {
    expect(EXPECTED_TOKENS.font).toHaveLength(5);
  });
});

describe('tokens-r12.css 字号约束（5 个全部整数 px）', () => {
  it.each(EXPECTED_TOKENS.size)('font-size %s 为整数 px', (name) => {
    const rootBlock = extractRootBlock(cssText);
    const value = readTokenValue(rootBlock, name);
    expect(value, `${name} 未定义`).toBeDefined();
    expect(value).toMatch(/^\d+px$/);
    const px = Number.parseInt(value!.replace('px', ''), 10);
    expect(Number.isInteger(px)).toBe(true);
    expect(px).toBeGreaterThan(0);
  });
});

describe('tokens-r12.css 间距约束（6 个全部是 4 的倍数）', () => {
  it.each(EXPECTED_TOKENS.space)('space %s 是 4 的倍数或 0', (name) => {
    const rootBlock = extractRootBlock(cssText);
    const value = readTokenValue(rootBlock, name);
    expect(value, `${name} 未定义`).toBeDefined();
    const numeric = Number.parseInt(value!.replace(/px$/, ''), 10);
    expect(Number.isInteger(numeric)).toBe(true);
    expect(numeric % 4).toBe(0);
  });
});

describe('tokens-r12.css z-index 严格递增', () => {
  it('hud(100) < popover(200) < modal(300) < toast(400) < debug(500)', () => {
    const rootBlock = extractRootBlock(cssText);
    const order = EXPECTED_TOKENS.z.map((name) => {
      const value = readTokenValue(rootBlock, name);
      expect(value, `${name} 未定义`).toBeDefined();
      return Number.parseInt(value!, 10);
    });
    for (let i = 1; i < order.length; i += 1) {
      expect(order[i]).toBeGreaterThan(order[i - 1]);
    }
    expect(order).toEqual([100, 200, 300, 400, 500]);
  });
});

describe('tokens-r12.css 过渡时长（4 个，按用户规范命名）', () => {
  const motionExpected: Record<string, string> = {
    'motion-fast': '80ms',
    'motion-base': '120ms',
    'motion-slow': '200ms',
    'motion-slowest': '400ms',
  };
  it.each(Object.entries(motionExpected))('motion %s 取值 %s', (name, expected) => {
    const rootBlock = extractRootBlock(cssText);
    const value = readTokenValue(rootBlock, name);
    expect(value).toBe(expected);
  });
});

describe('tokens-r12.css 暗色模式覆写', () => {
  it('至少检测到两个暗色模式 block（@media + [data-theme=dark]）', () => {
    const blocks = extractDarkBlocks(cssText);
    expect(blocks.length).toBeGreaterThanOrEqual(2);
  });

  it('每个暗色模式 block 至少包含 5 个反相变量', () => {
    const blocks = extractDarkBlocks(cssText);
    for (const block of blocks) {
      const overrides = listTokensIn(block);
      expect(overrides.length).toBeGreaterThanOrEqual(5);
    }
  });

  it('暗色模式中 primary-50 由浅 (#fef7e3) 反相为深色', () => {
    const blocks = extractDarkBlocks(cssText);
    const allOverrides = blocks.flatMap((b) => {
      const value = readTokenValue(b, 'color-primary-50');
      return value ? [value.toLowerCase()] : [];
    });
    expect(allOverrides.length).toBeGreaterThan(0);
    for (const value of allOverrides) {
      expect(value).not.toBe('#fef7e3');
      expect(value).toMatch(/^#[0-9a-f]{6}$/);
    }
  });
});
