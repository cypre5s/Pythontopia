/**
 * 课件问答 `@` 引用命令的输入区状态机。
 *
 * 设计要点：
 *   - 在 textarea 文本末尾检测 `@<query>` 触发，弹出可引用的课件实体菜单（章节 / 知识点 KC）；
 *   - 选中后把 token 插入文本，并记录结构化引用 SelectedRef；
 *   - 提交时：KC 引用 → kcCodes（驱动 RAG 过滤检索）；章节/KC 标签 → context（作为 grounding 上下文透传给导师）。
 *
 * 触发/插入逻辑保持纯函数化，便于单测。
 */
import { computed, ref, type ComputedRef, type Ref } from 'vue';

export type MentionKind = 'chapter' | 'kc';

export interface MentionItem {
  kind: MentionKind;
  /** 内联插入到问题文本中的引用 token（无空格），如 `@KC-012`、`@CH3`（已隐藏 PY43 内码）。 */
  token: string;
  /** 菜单展示名，如 `input 函数`。 */
  label: string;
  /** 次要描述（章节标题 / KC 摘要），用于菜单与上下文。 */
  desc?: string;
  /** KC 引用专用：知识点编码。 */
  kcCode?: string;
  /** 章节引用专用：章节 id。 */
  chapterId?: number;
}

export interface MentionGroup {
  group: string;
  items: MentionItem[];
}

export interface SelectedRef {
  token: string;
  kind: MentionKind;
  label: string;
  kcCode?: string;
  chapterId?: number;
}

export interface CoursewareMention {
  text: Ref<string>;
  menuVisible: Ref<boolean>;
  query: Ref<string>;
  activeIndex: Ref<number>;
  selected: Ref<SelectedRef[]>;
  groups: ComputedRef<MentionGroup[]>;
  flatItems: ComputedRef<MentionItem[]>;
  selectedKcCodes: ComputedRef<string[]>;
  contextHint: ComputedRef<string>;
  updateFromText: (next: string) => void;
  moveActive: (direction: number) => void;
  selectItem: (item: MentionItem) => void;
  commitActive: () => boolean;
  removeRef: (token: string) => void;
  close: () => void;
  reset: () => void;
}

/** 触发正则：文本末尾出现 `@` 后跟非空白、非 `@` 字符（含中文）。 */
const AT_TRIGGER_RE = /(?:^|\s)@([^\s@]*)$/;
/** 选中时用于把末尾 `@query` 替换为 token 的正则。 */
const AT_REPLACE_RE = /(^|\s)@([^\s@]*)$/;

const MAX_CHAPTER_ITEMS = 8;
const MAX_KC_ITEMS = 12;

export function useCoursewareMention(allItems: Ref<MentionItem[]>): CoursewareMention {
  const text = ref('');
  const menuVisible = ref(false);
  const query = ref('');
  const activeIndex = ref(0);
  const selected = ref<SelectedRef[]>([]);

  const groups = computed<MentionGroup[]>(() => {
    const q = query.value.trim().toLowerCase();
    const filtered = allItems.value.filter((item) => {
      if (!q) return true;
      const haystack = `${item.token} ${item.label} ${item.desc ?? ''}`.toLowerCase();
      return haystack.includes(q);
    });
    const out: MentionGroup[] = [];
    const chapters = filtered.filter((item) => item.kind === 'chapter').slice(0, MAX_CHAPTER_ITEMS);
    const kcs = filtered.filter((item) => item.kind === 'kc').slice(0, MAX_KC_ITEMS);
    if (chapters.length > 0) out.push({ group: '章节', items: chapters });
    if (kcs.length > 0) out.push({ group: '知识点', items: kcs });
    return out;
  });

  const flatItems = computed<MentionItem[]>(() =>
    groups.value.reduce<MentionItem[]>((acc, group) => acc.concat(group.items), []),
  );

  const selectedKcCodes = computed<string[]>(() =>
    Array.from(
      new Set(
        selected.value
          .map((ref_) => ref_.kcCode)
          .filter((code): code is string => typeof code === 'string' && code.length > 0),
      ),
    ),
  );

  const contextHint = computed<string>(() => {
    if (selected.value.length === 0) return '';
    const parts = selected.value.map((ref_) =>
      ref_.kind === 'kc' && ref_.kcCode
        ? `知识点 ${ref_.kcCode}（${ref_.label}）`
        : `章节「${ref_.label}」`,
    );
    return `学生指定参考课件范围：${parts.join('、')}。`;
  });

  function updateFromText(next: string): void {
    text.value = next;
    // 引用 token 被删除时同步移除结构化引用。
    if (selected.value.length > 0) {
      selected.value = selected.value.filter((ref_) => next.includes(ref_.token));
    }
    const match = next.match(AT_TRIGGER_RE);
    if (match) {
      menuVisible.value = true;
      query.value = match[1] ?? '';
      activeIndex.value = 0;
    } else {
      menuVisible.value = false;
      query.value = '';
    }
  }

  function moveActive(direction: number): void {
    const total = flatItems.value.length;
    if (total === 0) return;
    activeIndex.value = (activeIndex.value + direction + total) % total;
  }

  function selectItem(item: MentionItem): void {
    const current = text.value;
    const replaced = current.replace(AT_REPLACE_RE, (_match, prefix: string) => `${prefix}${item.token} `);
    text.value = replaced === current ? `${current}${item.token} ` : replaced;
    if (!selected.value.some((ref_) => ref_.token === item.token)) {
      // exactOptionalPropertyTypes：仅在有值时挂载可选字段，避免显式 undefined。
      const next: SelectedRef = { token: item.token, kind: item.kind, label: item.label };
      if (item.kcCode !== undefined) next.kcCode = item.kcCode;
      if (item.chapterId !== undefined) next.chapterId = item.chapterId;
      selected.value = [...selected.value, next];
    }
    menuVisible.value = false;
    query.value = '';
    activeIndex.value = 0;
  }

  function commitActive(): boolean {
    if (!menuVisible.value) return false;
    const item = flatItems.value[activeIndex.value];
    if (item) selectItem(item);
    return true;
  }

  function removeRef(token: string): void {
    selected.value = selected.value.filter((ref_) => ref_.token !== token);
    text.value = text.value.replace(token, '').replace(/\s{2,}/g, ' ').trimStart();
  }

  function close(): void {
    menuVisible.value = false;
    query.value = '';
  }

  function reset(): void {
    text.value = '';
    selected.value = [];
    close();
    activeIndex.value = 0;
  }

  return {
    text,
    menuVisible,
    query,
    activeIndex,
    selected,
    groups,
    flatItems,
    selectedKcCodes,
    contextHint,
    updateFromText,
    moveActive,
    selectItem,
    commitActive,
    removeRef,
    close,
    reset,
  };
}

/** 工具：把课程章节 + KC 转换为 mention 候选项。 */
export function buildMentionItems(
  chapters: { chapterId: number; orderIndex: number; title: string }[],
  kcs: { kcCode: string; kcName: string; chapterId: number; summaryMarkdown?: string | null }[],
): MentionItem[] {
  const chapterItems: MentionItem[] = chapters
    .slice()
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .map((chapter) => ({
      kind: 'chapter',
      token: `@CH${chapter.orderIndex}`,
      label: `第 ${chapter.orderIndex} 章 ${chapter.title}`,
      desc: chapter.title,
      chapterId: chapter.chapterId,
    }));
  const kcItems: MentionItem[] = kcs
    .slice()
    .sort((a, b) => a.kcCode.localeCompare(b.kcCode))
    .map((kc) => ({
      kind: 'kc',
      // token 仅作 UI 内联标记，去掉 PY43- 前缀（学生不该看到 PY43-KC-012 这种内码）；
      // kcCode 仍保留全码用于驱动后端 RAG / 生成，DB 与规范码不变。
      token: `@${kc.kcCode.replace(/^PY43-/, '')}`,
      label: kc.kcName,
      desc: kc.summaryMarkdown ?? kc.kcName,
      kcCode: kc.kcCode,
    }));
  return [...chapterItems, ...kcItems];
}

export const __TESTING__ = { AT_TRIGGER_RE, AT_REPLACE_RE };
