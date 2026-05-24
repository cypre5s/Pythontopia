// 隔离原因：
// 业务层（Vue 组件、Pinia store、HUD chip 等）需要响应式的 i18n 句柄，但
// `src/i18n/index.ts` 提供的 t/setLocale/getLocale 是普通函数 + 模块级变量，
// 切换语言时不会自动触发组件刷新。本 composable 是业务层访问 i18n 的唯一入口：
// 1. 把当前 locale 暴露为模块级 `ref<Locale>`，setLocale 写入后所有组件同步重渲染；
// 2. 在底层 t() 之上叠加一层 `{name}` 简单插值，避免组件层散装 `.replace`；
// 3. 提供最小可用的英文复数 `tn()`，R-13 替换为 vue-i18n + ICU 时此处签名保持稳定。
//
// 约束（与 docs/i18n-translation-guide.md 对齐）：
// - 不引入 vue-i18n / Intl.PluralRules（包体最小化，星露谷 Beta 仅 zh-CN/en-US 两语言）；
// - 业务层禁止直接 import `@/i18n` 的 setLocale —— 否则模块级 ref 不会同步；
// - 占位符语法只支持 `{name}` 单花括号，与底层 i18n 文件保持一致（vue-i18n 默认）；
// - 复数规则只覆盖"末尾英文名词 +s / +es / +ies"，再复杂的 cases / 不规则单复数
//   由 R-13 切到 ICU 后处理（本文件输出的 tn 仅给数量类 toast 用，如「2 items in bag」）。

import { ref, type Ref } from 'vue';
import {
  t as baseT,
  setLocale as baseSetLocale,
  getLocale as baseGetLocale,
  SUPPORTED_LOCALES,
  type Locale,
} from '@/i18n';

export type I18nParams = Record<string, string | number>;

export interface UseI18nReturn {
  /** 当前 locale 的只读响应式引用；视图/computed 订阅它即可在切语言时刷新。 */
  locale: Ref<Locale>;
  /** 翻译 + 简单 `{name}` 插值；找不到 key 时返回 key 本身（与底层 t 行为一致）。 */
  t: (key: string, params?: I18nParams) => string;
  /** 数量化翻译；zh-CN 透传，en-US 在末尾英文单词执行 +s/+es/+ies 简化规则。 */
  tn: (key: string, count: number, params?: I18nParams) => string;
  /** 切换 locale；同步写入底层 currentLocale + 模块级 ref，触发所有订阅者刷新。 */
  setLocale: (next: Locale) => void;
}

// 模块级单例：多个组件 useI18n() 拿到同一个 ref，setLocale 后跨组件一次性同步。
// 不放进 useI18n() 函数体内 —— 否则每个组件各拿独立 ref，setLocale 只刷新自己一份。
const localeRef = ref<Locale>(baseGetLocale());

const PLURAL_TAIL = /^(.*?)([A-Za-z]+)([^A-Za-z]*)$/;
const PLURAL_PLACEHOLDER = /\{(\w+)\}/g;

function interpolate(template: string, params: I18nParams): string {
  return template.replace(PLURAL_PLACEHOLDER, (match, name: string) => {
    if (Object.prototype.hasOwnProperty.call(params, name)) {
      const value = params[name];
      return value === undefined ? match : String(value);
    }
    return match;
  });
}

// 简化版英文复数：仅处理尾部最后一段连续英文字母。
// 设计约束：本函数只服务于"以可数名词单数收尾"的短句（如 "1 item"、"got coin"）。
// 若文案末尾不是字母（标点/数字/中文混排）→ 退化为整串后缀 's'；
// 命中 sibilant 词尾（s/x/z/ch/sh）→ 加 'es'；命中辅音 + y → 截 y 改 'ies'。
function pluralizeEnglishTail(text: string): string {
  const match = PLURAL_TAIL.exec(text);
  if (!match) {
    return `${text}s`;
  }
  const head = match[1] ?? '';
  const word = match[2] ?? '';
  const tail = match[3] ?? '';
  if (
    word.endsWith('s') ||
    word.endsWith('x') ||
    word.endsWith('z') ||
    word.endsWith('ch') ||
    word.endsWith('sh')
  ) {
    return `${head}${word}es${tail}`;
  }
  if (/[^aeiouAEIOU]y$/.test(word)) {
    return `${head}${word.slice(0, -1)}ies${tail}`;
  }
  return `${head}${word}s${tail}`;
}

function translate(key: string, params?: I18nParams): string {
  const raw = baseT(key, localeRef.value);
  if (!params) {
    return raw;
  }
  return interpolate(raw, params);
}

function translatePlural(key: string, count: number, params?: I18nParams): string {
  const merged: I18nParams = { ...(params ?? {}), count };
  const text = translate(key, merged);
  if (localeRef.value === 'zh-CN') {
    return text;
  }
  if (count === 1 || count === -1) {
    return text;
  }
  return pluralizeEnglishTail(text);
}

function applyLocale(next: Locale): void {
  if (!SUPPORTED_LOCALES.includes(next)) {
    return;
  }
  baseSetLocale(next);
  localeRef.value = next;
}

export function useI18n(): UseI18nReturn {
  return {
    locale: localeRef,
    t: translate,
    tn: translatePlural,
    setLocale: applyLocale,
  };
}

// 测试 / SSR 入口下偶尔需要直接读 ref 或 reset 到默认 locale，
// 但禁止业务代码绕过 useI18n() 调用；exported 只为单测使用。
export const __internal__ = {
  localeRef,
  pluralizeEnglishTail,
  interpolate,
};
