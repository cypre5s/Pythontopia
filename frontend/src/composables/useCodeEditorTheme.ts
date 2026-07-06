import { computed, ref, type ComputedRef, type Ref } from 'vue';

export type CodeEditorTheme = 'light' | 'dark';

/** 探测站点主题（data-theme 优先，其次系统偏好），默认深色。 */
function detectSiteTheme(): CodeEditorTheme {
  if (typeof document === 'undefined') return 'dark';
  const attr = document.documentElement.dataset.theme;
  if (attr === 'light') return 'light';
  if (attr === 'dark') return 'dark';
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: light)').matches) {
    return 'light';
  }
  return 'dark';
}

// 模块级单例：全站代码编辑器共享同一主题。把「暗色 / 亮色」切换按钮从编辑器内部上提到
// 做题页头部（AI 助教左侧）后，按钮与编辑器需要共享同一份可响应状态；做题页切换主题，
// 无尽闯关等其它页的编辑器也一并跟随。
const theme = ref<CodeEditorTheme>(detectSiteTheme());
const userOverrode = ref(false);

export interface UseCodeEditorThemeReturn {
  theme: Ref<CodeEditorTheme>;
  isLight: ComputedRef<boolean>;
  userOverrode: Ref<boolean>;
  toggle: () => void;
  /** 站点主题变化时同步（仅在用户未手动切换过时生效）。 */
  followSiteTheme: () => void;
}

export function useCodeEditorTheme(): UseCodeEditorThemeReturn {
  const isLight = computed(() => theme.value === 'light');

  function toggle(): void {
    userOverrode.value = true;
    theme.value = theme.value === 'light' ? 'dark' : 'light';
  }

  function followSiteTheme(): void {
    if (userOverrode.value) return;
    const next = detectSiteTheme();
    if (next !== theme.value) theme.value = next;
  }

  return { theme, isLight, userOverrode, toggle, followSiteTheme };
}
