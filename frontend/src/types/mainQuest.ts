// 主线 + AI Boss 共享类型。
//
// 数据持久化策略：所有运行时进度塞进 `save_slot.snapshot.mainQuest` JSONB，无需独立 migration。

export interface MainSubQuest {
  /** 章节+子序号，例如 "1-3"。全局唯一。 */
  id: string;
  /** 1..7 章号。 */
  chapter: number;
  /** 1..5 章内序号。 */
  index: number;
  /** 用户可见标题。 */
  title: string;
  /** 一句话提示，会在 HUD chip / 主线面板中显示。 */
  hint: string;
  /** 触发任务的地图 key（R-14 单 hub：town_hub）。 */
  mapKey: MainQuestMapKey;
  /** 与之对话的 NPC（可选）。 */
  npcKey?: string;
  /** 推荐学习的 KC（用于 boss 出题 + 学习路径回写）。 */
  kcCodes: string[];
  /** 完成证据需求；CSR 与 boss 服务都依赖此校验。 */
  evidenceRequired: MainSubQuestEvidence;
  /**
   * 完成后奖励（写 mastery）。可选：HANDOUT「讲义」类任务已随「讲义解耦」去掉奖励，
   * 故此字段允许缺省，所有消费方需对 undefined 兜底。
   */
  rewards?: MainSubQuestReward[];
}

export type MainQuestMapKey = 'town_hub';

export type MainSubQuestEvidence =
  | { type: 'dialog_complete'; npcKey: string }
  | { type: 'submission'; problemId: number }
  | { type: 'resource_consumed'; resourceType: string }
  | { type: 'building_visit'; mapKey: MainQuestMapKey; tileX: number; tileY: number }
  | { type: 'kc_mastery'; kcCode: string; thresholdScore: number };

export type MainSubQuestRewardKind = 'coin' | 'gem' | 'item' | 'kc_mastery';

export interface MainSubQuestReward {
  kind: MainSubQuestRewardKind;
  amount: number;
  /** item ref / kc code，按 kind 解释。 */
  ref?: string;
}

/** 7 章 boss 元数据（每章 1 个 boss，4 阶段 AI 出题）。 */
export interface MainQuestBoss {
  chapter: number;
  /** Boss 别名，例如 NameError / TypeError / ConditionBug ... */
  name: string;
  /** 关联章 KC 范围，boss 出题时优先抽这一段。 */
  kcRange: { startCode: string; endCode: string };
  /** 通关奖励（额外金币 + outfit / title 解锁）。 */
  rewards: MainSubQuestReward[];
  /** 解锁后玩家可见的故事一句话。 */
  storyboardCue: string;
}

/** 玩家在 save slot 中的主线进度。 */
export interface MainQuestProgress {
  /** 当前章节 1..7，未开始 = 0。 */
  currentChapter: number;
  /** 当前章节内的子任务序号 1..5，0 = 待领取。 */
  currentSubQuest: number;
  /** 已完成的子任务 ID 列表（跨章节，去重）。 */
  completedSubQuests: string[];
  /** 已击败的 boss 章节列表。 */
  defeatedBosses: number[];
  /** 已解锁的 outfit / title / storyboard 等里程碑。 */
  unlockedMilestones: string[];
  /** AI 出题历史日志（最近 N 题），用于自适应难度。 */
  recentAiProblems: RecentAiProblemEntry[];
  /** 已消费多模态讲解的章节标记（如 "ch1"），用于章节「完整通关」软门槛。 */
  consumedMedia: string[];
}

export interface RecentAiProblemEntry {
  problemId: string;
  kc: string;
  difficulty: number;
  /** null = 未通过；否则是通关耗时（秒）。 */
  succeededInSec: number | null;
  timestamp: string;
}

/** 主线 panel 中按章聚合的视图模型。 */
export interface MainQuestChapterView {
  chapter: number;
  title: string;
  /** 5 个子任务，按 index 排序。 */
  subQuests: MainSubQuestViewItem[];
  /** 章节 boss。 */
  boss: MainQuestBoss;
  /** 玩家在本章的完成度（0..1）。 */
  progress: number;
  /** boss 是否已击败。 */
  bossDefeated: boolean;
}

export interface MainSubQuestViewItem extends MainSubQuest {
  status: 'locked' | 'available' | 'in_progress' | 'completed';
}

/** 新存档的初始主线进度（currentChapter=1、各列表为空）。 */
export function mainQuestEmptyProgress(): MainQuestProgress {
  return {
    currentChapter: 1,
    currentSubQuest: 1,
    completedSubQuests: [],
    defeatedBosses: [],
    unlockedMilestones: [],
    recentAiProblems: [],
    consumedMedia: [],
  };
}
