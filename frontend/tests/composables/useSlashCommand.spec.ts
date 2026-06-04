/**
 * 百晓工坊 `/` 斜杠命令状态机单元测试。
 *
 * 覆盖：
 *   1. `/` 触发检测：文本末尾 /query 弹菜单并解析 query；非触发态关闭。
 *   2. 分组与过滤：答疑 / 生成资源分组；query 子串（含别名）过滤。
 *   3. 选中即模式：抹掉末尾 /query、设 forcedIntent（不往文本插 token）。
 *   4. 键盘导航：moveActive 环形；commitActive 选中当前项。
 *   5. forcedIntent：setForced / clearForced / forcedCommand 派生。
 *   6. reset 清空 forcedIntent 与菜单。
 */
import { describe, it, expect } from 'vitest';
import { ref } from 'vue';
import {
  SLASH_COMMANDS,
  useSlashCommand,
  __TESTING__,
} from '@/composables/useSlashCommand';

describe('SLASH_TRIGGER_RE', () => {
  it('匹配文本末尾的 /query（含中文）', () => {
    expect('/'.match(__TESTING__.SLASH_TRIGGER_RE)?.[1]).toBe('');
    expect('讲讲 /思'.match(__TESTING__.SLASH_TRIGGER_RE)?.[1]).toBe('思');
    expect('/思维导图'.match(__TESTING__.SLASH_TRIGGER_RE)?.[1]).toBe('思维导图');
  });

  it('不匹配非末尾或无空白前缀的 /', () => {
    expect('/讲 之后'.match(__TESTING__.SLASH_TRIGGER_RE)).toBeNull();
    expect('path/to'.match(__TESTING__.SLASH_TRIGGER_RE)).toBeNull();
  });
});

describe('useSlashCommand', () => {
  it('输入 / 弹出菜单，分组为答疑 + 生成资源，共 7 个命令', () => {
    const s = useSlashCommand(ref('/'));
    s.updateFromText('/');
    expect(s.menuVisible.value).toBe(true);
    expect(s.groups.value.map((g) => g.group)).toEqual(['答疑', '生成资源']);
    expect(s.flatItems.value).toHaveLength(SLASH_COMMANDS.length);
    expect(s.flatItems.value).toHaveLength(7);
  });

  it('query 子串过滤（命中 label/别名）', () => {
    const s = useSlashCommand(ref('/思'));
    s.updateFromText('/思');
    expect(s.flatItems.value).toHaveLength(1);
    expect(s.flatItems.value[0]?.intent).toBe('MINDMAP');
  });

  it('别名过滤：mindmap 命中思维导图', () => {
    const s = useSlashCommand(ref('/mindmap'));
    s.updateFromText('/mindmap');
    expect(s.flatItems.value[0]?.intent).toBe('MINDMAP');
  });

  it('非触发态关闭菜单', () => {
    const s = useSlashCommand(ref('/讲'));
    s.updateFromText('/讲');
    expect(s.menuVisible.value).toBe(true);
    s.updateFromText('普通问题');
    expect(s.menuVisible.value).toBe(false);
    expect(s.query.value).toBe('');
  });

  it('选中命令：抹掉末尾 /query，设 forcedIntent，不插 token', () => {
    const text = ref('讲讲循环 /思');
    const s = useSlashCommand(text);
    s.updateFromText('讲讲循环 /思');
    const cmd = s.flatItems.value[0];
    expect(cmd?.intent).toBe('MINDMAP');
    s.selectCommand(cmd!);
    expect(text.value).toBe('讲讲循环 ');
    expect(s.forcedIntent.value).toBe('MINDMAP');
    expect(s.forcedCommand.value?.label).toBe('思维导图');
    expect(s.menuVisible.value).toBe(false);
  });

  it('行首 / 选中后文本清空为前缀', () => {
    const text = ref('/讲义');
    const s = useSlashCommand(text);
    s.updateFromText('/讲义');
    s.commitActive();
    expect(text.value).toBe('');
    expect(s.forcedIntent.value).toBe('HANDOUT');
  });

  it('moveActive 环形移动，commitActive 选中当前项', () => {
    const text = ref('/');
    const s = useSlashCommand(text);
    s.updateFromText('/');
    expect(s.activeIndex.value).toBe(0);
    s.moveActive(-1);
    expect(s.activeIndex.value).toBe(6);
    s.moveActive(1);
    expect(s.activeIndex.value).toBe(0);
    const committed = s.commitActive();
    expect(committed).toBe(true);
    expect(s.forcedIntent.value).toBe('ASK');
  });

  it('setForced / clearForced 切换强制工具', () => {
    const s = useSlashCommand(ref(''));
    s.setForced('CODE_LAB');
    expect(s.forcedCommand.value?.intent).toBe('CODE_LAB');
    s.clearForced();
    expect(s.forcedIntent.value).toBeNull();
    expect(s.forcedCommand.value).toBeNull();
  });

  it('commitActive 在菜单关闭时返回 false', () => {
    const s = useSlashCommand(ref(''));
    expect(s.commitActive()).toBe(false);
  });

  it('reset 清空 forcedIntent 与菜单', () => {
    const text = ref('/');
    const s = useSlashCommand(text);
    s.updateFromText('/');
    s.commitActive();
    s.reset();
    expect(s.forcedIntent.value).toBeNull();
    expect(s.menuVisible.value).toBe(false);
    expect(s.activeIndex.value).toBe(0);
  });
});
