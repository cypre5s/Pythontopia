/**
 * 课件问答 `@/` 引用命令状态机单元测试。
 *
 * 覆盖：
 *   1. `@` 触发检测：文本末尾 @query 弹出菜单并解析 query；非触发态关闭。
 *   2. 分组与过滤：章节 / 知识点分组；query 子串过滤。
 *   3. 选中插入：末尾 @query 被替换为 token；记录结构化引用（去重）。
 *   4. 派生：selectedKcCodes 仅含 KC 且去重；contextHint 拼接引用标签。
 *   5. 键盘导航：moveActive 环形移动；commitActive 选中当前项。
 *   6. 删除：removeRef 同时清掉 token 文本与结构化引用。
 *   7. updateFromText：删除 token 文本时自动同步移除引用。
 *   8. buildMentionItems：章节按 orderIndex、KC 按 code 排序，token 格式正确。
 */
import { describe, it, expect } from 'vitest';
import { ref } from 'vue';
import {
  buildMentionItems,
  useCoursewareMention,
  __TESTING__,
  type MentionItem,
} from '@/composables/useCoursewareMention';

function makeItems(): MentionItem[] {
  return buildMentionItems(
    [
      { chapterId: 100, orderIndex: 2, title: '流程控制' },
      { chapterId: 99, orderIndex: 1, title: '入门' },
    ],
    [
      { kcCode: 'PY43-KC-012', kcName: 'input 函数', chapterId: 99, summaryMarkdown: '读取输入' },
      { kcCode: 'PY43-KC-044', kcName: 'for 循环', chapterId: 100, summaryMarkdown: null },
    ],
  );
}

describe('AT_TRIGGER_RE', () => {
  it('匹配文本末尾的 @query（含中文与连字符）', () => {
    expect('问下 @for'.match(__TESTING__.AT_TRIGGER_RE)?.[1]).toBe('for');
    expect('@PY43-KC-012'.match(__TESTING__.AT_TRIGGER_RE)?.[1]).toBe('PY43-KC-012');
    expect('@循环'.match(__TESTING__.AT_TRIGGER_RE)?.[1]).toBe('循环');
  });

  it('不匹配非末尾或被空格截断的 @', () => {
    expect('@for 之后'.match(__TESTING__.AT_TRIGGER_RE)).toBeNull();
    expect('邮箱 a@b'.match(__TESTING__.AT_TRIGGER_RE)).toBeNull();
  });
});

describe('useCoursewareMention', () => {
  it('输入 @ 时弹出菜单，分组为章节 + 知识点', () => {
    const m = useCoursewareMention(ref(makeItems()));
    m.updateFromText('请讲 @');
    expect(m.menuVisible.value).toBe(true);
    expect(m.groups.value.map((g) => g.group)).toEqual(['章节', '知识点']);
    expect(m.flatItems.value).toHaveLength(4);
  });

  it('query 子串过滤候选项', () => {
    const m = useCoursewareMention(ref(makeItems()));
    m.updateFromText('@循环');
    expect(m.menuVisible.value).toBe(true);
    expect(m.flatItems.value).toHaveLength(1);
    expect(m.flatItems.value[0]?.kcCode).toBe('PY43-KC-044');
  });

  it('非触发态关闭菜单', () => {
    const m = useCoursewareMention(ref(makeItems()));
    m.updateFromText('@for');
    expect(m.menuVisible.value).toBe(true);
    m.updateFromText('普通问题');
    expect(m.menuVisible.value).toBe(false);
    expect(m.query.value).toBe('');
  });

  it('选中 KC 后替换末尾 @query 为 token 并记录引用', () => {
    const items = makeItems();
    const m = useCoursewareMention(ref(items));
    m.updateFromText('讲讲 @inp');
    const kc = m.flatItems.value.find((i) => i.kcCode === 'PY43-KC-012');
    expect(kc).toBeDefined();
    m.selectItem(kc as MentionItem);
    expect(m.text.value).toBe('讲讲 @PY43-KC-012 ');
    expect(m.menuVisible.value).toBe(false);
    expect(m.selected.value).toHaveLength(1);
    expect(m.selectedKcCodes.value).toEqual(['PY43-KC-012']);
  });

  it('重复选中同一引用不重复记录', () => {
    const items = makeItems();
    const m = useCoursewareMention(ref(items));
    const kc = items.find((i) => i.kcCode === 'PY43-KC-012') as MentionItem;
    m.updateFromText('@x');
    m.selectItem(kc);
    m.updateFromText(`${m.text.value}@y`);
    m.selectItem(kc);
    expect(m.selected.value).toHaveLength(1);
  });

  it('selectedKcCodes 仅含 KC，章节引用进入 contextHint 但不进 kcCodes', () => {
    const items = makeItems();
    const m = useCoursewareMention(ref(items));
    const chapter = items.find((i) => i.kind === 'chapter') as MentionItem;
    const kc = items.find((i) => i.kcCode === 'PY43-KC-044') as MentionItem;
    m.updateFromText('@a');
    m.selectItem(chapter);
    m.updateFromText(`${m.text.value}@b`);
    m.selectItem(kc);
    expect(m.selectedKcCodes.value).toEqual(['PY43-KC-044']);
    expect(m.contextHint.value).toContain('PY43-KC-044');
    expect(m.contextHint.value).toContain('章节');
  });

  it('moveActive 环形移动，commitActive 选中当前项', () => {
    const m = useCoursewareMention(ref(makeItems()));
    m.updateFromText('@');
    expect(m.activeIndex.value).toBe(0);
    m.moveActive(-1);
    expect(m.activeIndex.value).toBe(3);
    m.moveActive(1);
    expect(m.activeIndex.value).toBe(0);
    const committed = m.commitActive();
    expect(committed).toBe(true);
    expect(m.selected.value).toHaveLength(1);
  });

  it('removeRef 清掉 token 文本与结构化引用', () => {
    const items = makeItems();
    const m = useCoursewareMention(ref(items));
    const kc = items.find((i) => i.kcCode === 'PY43-KC-012') as MentionItem;
    m.updateFromText('讲 @z');
    m.selectItem(kc);
    expect(m.text.value).toContain('@PY43-KC-012');
    m.removeRef('@PY43-KC-012');
    expect(m.selected.value).toHaveLength(0);
    expect(m.text.value).not.toContain('@PY43-KC-012');
  });

  it('删除 token 文本时自动同步移除引用', () => {
    const items = makeItems();
    const m = useCoursewareMention(ref(items));
    const kc = items.find((i) => i.kcCode === 'PY43-KC-012') as MentionItem;
    m.updateFromText('@z');
    m.selectItem(kc);
    expect(m.selected.value).toHaveLength(1);
    m.updateFromText('全部删掉了');
    expect(m.selected.value).toHaveLength(0);
  });

  it('reset 清空文本、引用与菜单', () => {
    const items = makeItems();
    const m = useCoursewareMention(ref(items));
    m.updateFromText('@');
    m.commitActive();
    m.reset();
    expect(m.text.value).toBe('');
    expect(m.selected.value).toHaveLength(0);
    expect(m.menuVisible.value).toBe(false);
  });
});

describe('buildMentionItems', () => {
  it('章节按 orderIndex 排序、token 为 @CH<order>', () => {
    const items = buildMentionItems(
      [
        { chapterId: 2, orderIndex: 2, title: 'B' },
        { chapterId: 1, orderIndex: 1, title: 'A' },
      ],
      [],
    );
    expect(items.map((i) => i.token)).toEqual(['@CH1', '@CH2']);
    expect(items[0]?.kind).toBe('chapter');
  });

  it('KC 按 code 排序、token 为 @<kcCode>', () => {
    const items = buildMentionItems(
      [],
      [
        { kcCode: 'PY43-KC-044', kcName: 'for', chapterId: 1, summaryMarkdown: null },
        { kcCode: 'PY43-KC-012', kcName: 'input', chapterId: 1, summaryMarkdown: null },
      ],
    );
    expect(items.map((i) => i.token)).toEqual(['@PY43-KC-012', '@PY43-KC-044']);
    expect(items[0]?.kcCode).toBe('PY43-KC-012');
  });
});
