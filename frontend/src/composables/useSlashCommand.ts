/**
 * 百晓工坊输入框 `/` 斜杠命令状态机（与 useCoursewareMention 的 `@` 提及并存）。
 *
 * 设计要点：
 *   - 斜杠命令是「模式」不是「内联引用」：选中后不往文本里插 token，而是抹掉末尾
 *     `/query`、把 forcedIntent 设为对应工具，由调用方渲染成一枚可移除的「工具」徽标。
 *   - 17 个命令对齐 api/intent.ts 的 IntentKind 全集（17 工具分发表）：
 *     AI 助教（ASK/ASKME/EXPLAIN_CODE/FIX_BUG，导师管线）+ 6 类资源（工坊生成管线）+
 *     看讲解（VIDEO/BROWSE，外部检索非生成）+ 学习管家（PLAN/PROGRESS/NAVIGATE/MY_STUDY/REVIEW）。
 *   - 触发/替换正则与文本所有权解耦：text 由 useCoursewareMention 持有，本组合式只读它做
 *     检测、并在选中时写回（剥离 /query），便于和 `@` 共用同一个 textarea。
 *
 * 纯函数化的触发/过滤逻辑便于 Vitest 单测。
 */
import { computed, ref, type ComputedRef, type Ref } from 'vue';
import type { IntentKind } from '@/api/intent';

export interface SlashCommand {
  intent: IntentKind;
  /** 菜单展示与触发用的命令 token，如 `/思维导图`。 */
  token: string;
  label: string;
  hint: string;
  /** 分组名：AI 助教 / 生成资源 / 看讲解 / 学习管家。 */
  group: string;
  icon: string;
  /** 额外可被 query 命中的别名（英文 / 拼音 / 同义词）。 */
  aliases: string[];
}

export interface SlashCommandGroup {
  group: string;
  items: SlashCommand[];
}

const GROUP_ASK = 'AI 助教';
const GROUP_RESOURCE = '生成资源';
const GROUP_WATCH = '看讲解';
const GROUP_STEWARD = '学习管家';
const GROUP_ORDER = [GROUP_ASK, GROUP_RESOURCE, GROUP_WATCH, GROUP_STEWARD];

// 单一事实来源：WorkshopChat 也从这里取命令表，避免标签 / 图标重复定义。
export const SLASH_COMMANDS: readonly SlashCommand[] = [
  {
    intent: 'ASK',
    token: '/问答',
    label: '问答',
    hint: '随时提问，附课本依据',
    group: GROUP_ASK,
    icon: '?',
    aliases: ['ask', 'wenda', '答疑', '提问', 'qa'],
  },
  {
    intent: 'ASKME',
    token: '/考考我',
    label: '考考我',
    hint: '3 轮口头小测，边考边评',
    group: GROUP_ASK,
    icon: '✓',
    aliases: ['askme', 'kaokaowo', '考核', '小测', '测我', '检验'],
  },
  {
    intent: 'EXPLAIN_CODE',
    token: '/讲代码',
    label: '讲代码',
    hint: '逐行解释这段代码在干嘛',
    group: GROUP_ASK,
    icon: '⌨',
    aliases: ['explain', 'jiangdaima', '解释代码', '看懂代码', '逐行'],
  },
  {
    intent: 'FIX_BUG',
    token: '/修报错',
    label: '修报错',
    hint: '定位报错根因，引导你自己修',
    group: GROUP_ASK,
    icon: '⚠',
    aliases: ['fixbug', 'xiubaocuo', 'bug', 'debug', '报错', '调试'],
  },
  {
    intent: 'HANDOUT',
    token: '/讲义',
    label: '讲义',
    hint: '段落讲解 + 课件页引用',
    group: GROUP_RESOURCE,
    icon: '✎',
    aliases: ['handout', 'jiangyi', '笔记', '讲解'],
  },
  {
    intent: 'MINDMAP',
    token: '/思维导图',
    label: '思维导图',
    hint: '自动画知识结构图',
    group: GROUP_RESOURCE,
    icon: '✦',
    aliases: ['mindmap', 'daotu', '导图', '脑图'],
  },
  {
    intent: 'PRACTICE',
    token: '/练习题',
    label: '练习题',
    hint: '1-3 道题，不含完整答案',
    group: GROUP_RESOURCE,
    icon: '✪',
    aliases: ['practice', 'lianxi', '题', '做题', 'quiz'],
  },
  {
    intent: 'CODE_LAB',
    token: '/代码案例',
    label: '代码案例',
    hint: '能在网页里直接运行',
    group: GROUP_RESOURCE,
    icon: '⚒',
    aliases: ['code', 'codelab', 'daima', '代码', '实操'],
  },
  {
    intent: 'READING',
    token: '/拓展阅读',
    label: '拓展阅读',
    hint: '至少 2 个延伸阅读卡片',
    group: GROUP_RESOURCE,
    icon: '☼',
    aliases: ['reading', 'tuozhan', '阅读', '延伸'],
  },
  {
    intent: 'STORYBOARD',
    token: '/动画脚本',
    label: '动画脚本',
    hint: '3-5 个分镜，不渲染视频',
    group: GROUP_RESOURCE,
    icon: '⌘',
    aliases: ['storyboard', 'donghua', '分镜', '故事板'],
  },
  {
    intent: 'VIDEO',
    token: '/视频',
    label: 'B站视频',
    hint: '抓取B站精选讲解视频卡片',
    group: GROUP_WATCH,
    icon: '▶',
    aliases: ['video', 'shipin', 'b站', 'bilibili', '视频', '讲解视频', '哔哩'],
  },
  {
    intent: 'BROWSE',
    token: '/联网查证',
    label: '联网查证',
    hint: '白名单内查官方资料，标注外部内容',
    group: GROUP_WATCH,
    icon: '⌁',
    aliases: ['browse', 'search', 'web', 'wangluo', '联网', '查证', '搜索', '官方文档', '最新'],
  },
  {
    intent: 'PLAN',
    token: '/学习规划',
    label: '学习规划',
    hint: '学习总管定制看学练测计划',
    group: GROUP_STEWARD,
    icon: '☷',
    aliases: ['plan', 'guihua', '规划', '计划', '路线', '安排'],
  },
  {
    intent: 'PROGRESS',
    token: '/学情报告',
    label: '学情报告',
    hint: '看掌握度强弱项一图速览',
    group: GROUP_STEWARD,
    icon: '◉',
    aliases: ['progress', 'xueqing', '学情', '报告', '掌握度', '薄弱'],
  },
  {
    intent: 'NAVIGATE',
    token: '/带路',
    label: '带路',
    hint: '带你去小镇各处学习地点',
    group: GROUP_STEWARD,
    icon: '➤',
    aliases: ['navigate', 'dailu', '带我去', '导航', '去哪', '传送'],
  },
  {
    intent: 'MY_STUDY',
    token: '/找资料',
    label: '找资料',
    hint: '翻出你书房里生成过的资源',
    group: GROUP_STEWARD,
    icon: '⌕',
    aliases: ['mystudy', 'zhaoziliao', '资料', '我的资源', '书房', '之前生成'],
  },
  {
    intent: 'REVIEW',
    token: '/复习',
    label: '复习',
    hint: '按记忆曲线去错题矿洞复盘',
    group: GROUP_STEWARD,
    icon: '⟳',
    aliases: ['review', 'fuxi', '复盘', '回顾', '巩固', '矿洞'],
  },
];

/** 触发正则：文本末尾出现 `/` 后跟非空白、非 `/` 字符（含中文）。 */
const SLASH_TRIGGER_RE = /(?:^|\s)\/([^\s/]*)$/;
/** 选中时把末尾 `/query` 抹掉（保留前缀空白），命令转成 forcedIntent 徽标。 */
const SLASH_REPLACE_RE = /(^|\s)\/([^\s/]*)$/;

export interface SlashCommandState {
  menuVisible: Ref<boolean>;
  query: Ref<string>;
  activeIndex: Ref<number>;
  forcedIntent: Ref<IntentKind | null>;
  forcedCommand: ComputedRef<SlashCommand | null>;
  groups: ComputedRef<SlashCommandGroup[]>;
  flatItems: ComputedRef<SlashCommand[]>;
  updateFromText: (next: string) => void;
  moveActive: (direction: number) => void;
  selectCommand: (cmd: SlashCommand) => void;
  commitActive: () => boolean;
  setForced: (intent: IntentKind) => void;
  clearForced: () => void;
  close: () => void;
  reset: () => void;
}

export function useSlashCommand(text: Ref<string>): SlashCommandState {
  const menuVisible = ref(false);
  const query = ref('');
  const activeIndex = ref(0);
  const forcedIntent = ref<IntentKind | null>(null);

  const groups = computed<SlashCommandGroup[]>(() => {
    const q = query.value.trim().toLowerCase();
    const filtered = SLASH_COMMANDS.filter((cmd) => {
      if (!q) return true;
      const haystack =
        `${cmd.token} ${cmd.label} ${cmd.hint} ${cmd.aliases.join(' ')}`.toLowerCase();
      return haystack.includes(q);
    });
    const out: SlashCommandGroup[] = [];
    for (const group of GROUP_ORDER) {
      const items = filtered.filter((cmd) => cmd.group === group);
      if (items.length > 0) out.push({ group, items });
    }
    return out;
  });

  const flatItems = computed<SlashCommand[]>(() =>
    groups.value.reduce<SlashCommand[]>((acc, group) => acc.concat(group.items), []),
  );

  const forcedCommand = computed<SlashCommand | null>(
    () => SLASH_COMMANDS.find((cmd) => cmd.intent === forcedIntent.value) ?? null,
  );

  function updateFromText(next: string): void {
    const match = next.match(SLASH_TRIGGER_RE);
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

  function selectCommand(cmd: SlashCommand): void {
    text.value = text.value.replace(SLASH_REPLACE_RE, (_match, prefix: string) => prefix);
    forcedIntent.value = cmd.intent;
    menuVisible.value = false;
    query.value = '';
    activeIndex.value = 0;
  }

  function commitActive(): boolean {
    if (!menuVisible.value) return false;
    const cmd = flatItems.value[activeIndex.value];
    if (cmd) selectCommand(cmd);
    return true;
  }

  function setForced(intent: IntentKind): void {
    forcedIntent.value = intent;
  }

  function clearForced(): void {
    forcedIntent.value = null;
  }

  function close(): void {
    menuVisible.value = false;
    query.value = '';
  }

  function reset(): void {
    forcedIntent.value = null;
    close();
    activeIndex.value = 0;
  }

  return {
    menuVisible,
    query,
    activeIndex,
    forcedIntent,
    forcedCommand,
    groups,
    flatItems,
    updateFromText,
    moveActive,
    selectCommand,
    commitActive,
    setForced,
    clearForced,
    close,
    reset,
  };
}

export const __TESTING__ = { SLASH_TRIGGER_RE, SLASH_REPLACE_RE };
