/**
 * 百晓工坊 `/` 斜杠命令状态机单元测试。
 *
 * 覆盖：
 *   1. `/` 触发检测：文本末尾 /query 弹菜单并解析 query；非触发态关闭。
 *   2. 分组与过滤：AI 助教 / 生成资源 / 看讲解 / 学习管家分组；query 子串（含别名）过滤。
 *   3. 16 工具分发表完整性：IntentKind 全集（16 值）每个都有且只有一条命令。
 *   4. 选中即模式：抹掉末尾 /query、设 forcedIntent（不往文本插 token）。
 *   5. 键盘导航：moveActive 环形；commitActive 选中当前项。
 *   6. forcedIntent：setForced / clearForced / forcedCommand 派生。
 *   7. reset 清空 forcedIntent 与菜单。
 */
import { describe, it, expect } from 'vitest';
import { ref } from 'vue';
import type { IntentKind } from '@/api/intent';
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

/** 16 工具分发表全集（与 api/intent.ts 的 IntentKind 一一对应；类型标注保证漏值即编译报错）。 */
const ALL_INTENTS: IntentKind[] = [
  'ASK', 'ASKME', 'EXPLAIN_CODE', 'FIX_BUG',
  'HANDOUT', 'MINDMAP', 'PRACTICE', 'CODE_LAB', 'READING', 'STORYBOARD',
  'VIDEO',
  'PLAN', 'PROGRESS', 'NAVIGATE', 'MY_STUDY', 'REVIEW',
];

describe('SLASH_COMMANDS 16 工具分发表完整性', () => {
  it('16 个 intent 全有命令，且一一对应不重复（REVIEW 新增 /复习 不豁免）', () => {
    expect(SLASH_COMMANDS).toHaveLength(16);
    const intents = SLASH_COMMANDS.map((c) => c.intent);
    expect(new Set(intents).size).toBe(16);
    for (const intent of ALL_INTENTS) {
      expect(intents, `intent ${intent} 缺少斜杠命令`).toContain(intent);
    }
  });

  it('7 条新命令 token / 图标 / 分组正确', () => {
    const byIntent = new Map(SLASH_COMMANDS.map((c) => [c.intent, c]));
    expect(byIntent.get('ASKME')).toMatchObject({ token: '/考考我', icon: '✓', group: 'AI 助教' });
    expect(byIntent.get('EXPLAIN_CODE')).toMatchObject({ token: '/讲代码', icon: '⌨', group: 'AI 助教' });
    expect(byIntent.get('FIX_BUG')).toMatchObject({ token: '/修报错', icon: '⚠', group: 'AI 助教' });
    expect(byIntent.get('PLAN')).toMatchObject({ token: '/学习规划', icon: '☷', group: '学习管家' });
    expect(byIntent.get('PROGRESS')).toMatchObject({ token: '/学情报告', icon: '◉', group: '学习管家' });
    expect(byIntent.get('NAVIGATE')).toMatchObject({ token: '/带路', icon: '➤', group: '学习管家' });
    expect(byIntent.get('MY_STUDY')).toMatchObject({ token: '/找资料', icon: '⌕', group: '学习管家' });
    // REVIEW 原本无命令 → 新增 /复习 并入学习管家。
    expect(byIntent.get('REVIEW')).toMatchObject({ token: '/复习', group: '学习管家' });
  });

  it('分组成员对齐蓝本：AI 助教 4 / 生成资源 6 / 看讲解 1 / 学习管家 5', () => {
    const ofGroup = (g: string): string[] =>
      SLASH_COMMANDS.filter((c) => c.group === g).map((c) => c.intent);
    expect(ofGroup('AI 助教')).toEqual(['ASK', 'ASKME', 'EXPLAIN_CODE', 'FIX_BUG']);
    expect(ofGroup('生成资源')).toEqual([
      'HANDOUT', 'MINDMAP', 'PRACTICE', 'CODE_LAB', 'READING', 'STORYBOARD',
    ]);
    expect(ofGroup('看讲解')).toEqual(['VIDEO']);
    expect(ofGroup('学习管家')).toEqual(['PLAN', 'PROGRESS', 'NAVIGATE', 'MY_STUDY', 'REVIEW']);
  });
});

describe('useSlashCommand', () => {
  it('输入 / 弹出菜单，分组为 AI 助教 + 生成资源 + 看讲解 + 学习管家，共 16 个命令', () => {
    const s = useSlashCommand(ref('/'));
    s.updateFromText('/');
    expect(s.menuVisible.value).toBe(true);
    expect(s.groups.value.map((g) => g.group)).toEqual([
      'AI 助教', '生成资源', '看讲解', '学习管家',
    ]);
    expect(s.flatItems.value).toHaveLength(SLASH_COMMANDS.length);
    expect(s.flatItems.value).toHaveLength(16);
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

  it('别名过滤：b站 命中 B站视频（VIDEO）', () => {
    const s = useSlashCommand(ref('/b站'));
    s.updateFromText('/b站');
    expect(s.flatItems.value[0]?.intent).toBe('VIDEO');
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
    expect(s.activeIndex.value).toBe(15);
    s.moveActive(1);
    expect(s.activeIndex.value).toBe(0);
    const committed = s.commitActive();
    expect(committed).toBe(true);
    expect(s.forcedIntent.value).toBe('ASK');
  });

  it('新命令别名过滤：bug 命中修报错、kaokao 命中考考我、规划命中学习规划', () => {
    const fix = useSlashCommand(ref('/bug'));
    fix.updateFromText('/bug');
    expect(fix.flatItems.value[0]?.intent).toBe('FIX_BUG');

    const askme = useSlashCommand(ref('/kaokao'));
    askme.updateFromText('/kaokao');
    expect(askme.flatItems.value[0]?.intent).toBe('ASKME');

    const plan = useSlashCommand(ref('/规划'));
    plan.updateFromText('/规划');
    expect(plan.flatItems.value[0]?.intent).toBe('PLAN');
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
