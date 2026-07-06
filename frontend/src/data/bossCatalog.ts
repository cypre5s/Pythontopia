// 章节 Boss 目录（中立数据模块）。
//
// R-15 §12 B2：旧 mainQuest 主线系统退役后，章末 Boss 副本 / 闯关塔 / 无尽模式仍是
// 独立可玩功能，需要「7 章 boss 元数据」。这些数据从已删除的 `data/mainQuests.ts`
// 抽取至此中立模块，与退役的「35 子任务 + mainQuest store」彻底解耦。
//
// 消费方：pages/BossPage.vue、pages/ArenaPage.vue、pages/EndlessPage.vue、api/boss.ts。
// 数据严格 ground 在 packId=43《Python 语言基础》7 章 65 KC（kcRange 对齐 course_kc.json）。
// Boss 不再驱动主线进度（主线 = 后端权威 mainline 域，见 stores/mainline.ts）。

export type MainSubQuestRewardKind = 'item' | 'kc_mastery';

export interface MainSubQuestReward {
  kind: MainSubQuestRewardKind;
  amount: number;
  /** item ref / kc code，按 kind 解释。 */
  ref?: string;
}

/** 7 章 boss 元数据（每章 1 个 boss，4 阶段 AI 出题）。 */
export interface MainQuestBoss {
  chapter: number;
  /** Boss 别名（按章节主题命名）。 */
  name: string;
  /** 关联章 KC 范围，boss 出题时优先抽这一段。 */
  kcRange: { startCode: string; endCode: string };
  /** 通关奖励（掌握度 + outfit / title 解锁）。 */
  rewards: MainSubQuestReward[];
  /** 解锁后玩家可见的故事一句话。 */
  storyboardCue: string;
}

export const MAIN_QUEST_BOSSES: readonly MainQuestBoss[] = [
  {
    chapter: 1,
    name: '算盘灵 · 计算工具守卫',
    kcRange: { startCode: 'PY43-KC-001', endCode: 'PY43-KC-007' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'outfit:apprentice' },
      { kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-001' },
    ],
    storyboardCue: '镇长 Py 将算盘交予你，作为计算思维实习生的徽章。',
  },
  {
    chapter: 2,
    name: '解释器学徒 · Python 语言守关',
    kcRange: { startCode: 'PY43-KC-008', endCode: 'PY43-KC-016' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'title:python_speaker' },
      { kind: 'kc_mastery', amount: 15, ref: 'PY43-KC-016' },
    ],
    storyboardCue: 'Python 解释器在终端中打出一行 "Hello, learner!" 作为礼物。',
  },
  {
    chapter: 3,
    name: '二进制独眼 · 信息表示之灵',
    kcRange: { startCode: 'PY43-KC-017', endCode: 'PY43-KC-030' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'storyboard:binary_dawn' },
      { kind: 'kc_mastery', amount: 18, ref: 'PY43-KC-030' },
    ],
    storyboardCue: '小镇广场亮起 0/1 双色雾灯，照见进制转换的真章。',
  },
  {
    chapter: 4,
    name: '流程钟楼 · 自动化锻造神',
    kcRange: { startCode: 'PY43-KC-031', endCode: 'PY43-KC-038' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'item:loop_bookmark' },
      { kind: 'kc_mastery', amount: 20, ref: 'PY43-KC-037' },
    ],
    storyboardCue: '镇中钟楼自此走时不再卡帧，循环与分支井然有序。',
  },
  {
    chapter: 5,
    name: '集合棱镜 · 数据结构灵兽',
    kcRange: { startCode: 'PY43-KC-039', endCode: 'PY43-KC-047' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'outfit:captain' },
      { kind: 'kc_mastery', amount: 22, ref: 'PY43-KC-047' },
    ],
    storyboardCue: '小镇集市升起绣有 list / dict / set 三面棱镜的银帆。',
  },
  {
    chapter: 6,
    name: '文档守护神 · 文件存取灵',
    kcRange: { startCode: 'PY43-KC-048', endCode: 'PY43-KC-053' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'title:file_keeper' },
      { kind: 'kc_mastery', amount: 25, ref: 'PY43-KC-053' },
    ],
    storyboardCue: '镇上的档案馆里档案柜重新归位，词频统计帮你找到失落的篇章。',
  },
  {
    chapter: 7,
    name: '归纳之镜 · 面向对象与抽象终极',
    kcRange: { startCode: 'PY43-KC-054', endCode: 'PY43-KC-065' },
    rewards: [
      { kind: 'item', amount: 1, ref: 'outfit:graduate' },
      { kind: 'item', amount: 1, ref: 'title:pythontopia_alumnus' },
      { kind: 'kc_mastery', amount: 30, ref: 'PY43-KC-065' },
    ],
    storyboardCue: '镇长 Py 在毕业典礼把镇徽别在你身上，标志你已完成 7 章 65 KC。',
  },
] as const;

export function findBossByChapter(chapter: number): MainQuestBoss | null {
  return MAIN_QUEST_BOSSES.find((b) => b.chapter === chapter) ?? null;
}
