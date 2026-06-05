/**
 * Pythontopia i18n 装载器（最小实现，不依赖 vue-i18n）。
 *
 * 约束：
 *   - 文案文件以嵌套 JSON 落地；
 *   - 对外仅暴露 `Record<Locale, Record<string, string>>` 的扁平化视图，
 *     便于 t/tList 直接索引与 prefix 收集；
 *   - 不引入 vue-i18n / ICU 复数。
 */

import zhCNRaw from './zh-CN.json';
import enUSRaw from './en-US.json';

export type Locale = 'zh-CN' | 'en-US';

export const SUPPORTED_LOCALES: readonly Locale[] = ['zh-CN', 'en-US'] as const;

export const DEFAULT_LOCALE: Locale = 'zh-CN';

type NestedDict = { [key: string]: string | NestedDict };

function flatten(node: NestedDict, prefix = '', sink: Record<string, string> = {}): Record<string, string> {
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') {
      sink[path] = value;
    } else if (value && typeof value === 'object') {
      flatten(value, path, sink);
    }
  }
  return sink;
}

export const messages: Record<Locale, Record<string, string>> = {
  'zh-CN': flatten(zhCNRaw as unknown as NestedDict),
  'en-US': flatten(enUSRaw as unknown as NestedDict),
};

let currentLocale: Locale = DEFAULT_LOCALE;

export function getLocale(): Locale {
  return currentLocale;
}

export function setLocale(locale: Locale): void {
  if (!SUPPORTED_LOCALES.includes(locale)) {
    return;
  }
  currentLocale = locale;
}

export function t(key: string, locale?: Locale, fallback?: string): string {
  const targetLocale: Locale = locale ?? currentLocale;
  const dict = messages[targetLocale];
  const value = dict[key];
  if (typeof value === 'string') {
    return value;
  }
  return fallback ?? key;
}

export function tList(prefix: string, locale?: Locale): string[] {
  const targetLocale: Locale = locale ?? currentLocale;
  const dict = messages[targetLocale];
  const result: string[] = [];
  for (const key of Object.keys(dict).sort()) {
    if (key === prefix || key.startsWith(`${prefix}.`)) {
      const value = dict[key];
      if (typeof value === 'string') {
        result.push(value);
      }
    }
  }
  return result;
}

export function hasKey(key: string, locale?: Locale): boolean {
  const targetLocale: Locale = locale ?? currentLocale;
  return typeof messages[targetLocale][key] === 'string';
}

export function listKeys(locale?: Locale): string[] {
  const targetLocale: Locale = locale ?? currentLocale;
  return Object.keys(messages[targetLocale]).sort();
}
