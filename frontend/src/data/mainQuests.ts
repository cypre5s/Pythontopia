// R-13 §5 Phase 1（v2 重写）：35 主线子任务 + 7 boss 静态数据。
//
// 数据严格 ground 在 `data/course-pack-43/json/course_chapter.json` 的 7 章 +
// `course_kc.json` 的 65 KC（packId=43《Python 语言基础》，东华大学教材）。
// 子任务标题 / hint / KC 选择都从课件真章节抽取，杜绝早期占位文案瞎编。
//
// 章节映射（与 course_chapter.json order_index 一一对应）：
//   1. 计算工具与计算思维     (7 KCs)
//   2. Python 语言基础(2)     (9 KCs)
//   3. 计算机信息表示及计算   (14 KCs)
//   4. 流程自动化             (8 KCs)
//   5. 数据结构               (9 KCs)
//   6. 数据存取与分析         (6 KCs)
//   7. 归纳与抽象             (12 KCs)
//
// Boss 设计基于章节主题（非泛 Bug 命名）：
//   1 算盘灵 Abacus Spirit   — 守护从算盘到 ENIAC 的计算工具记忆
//   2 解释器学徒 Interpreter — 测试玩家是否能与 Python 解释器对话
//   3 二进制独眼  Binary Cyclop — 用 0/1 视角看世界，挑战进制转换
//   4 流程钟楼   Loop Tower   — 把 for/while/break/continue 拧成钟塔
//   5 集合棱镜   Dict Prism   — 列表/字典/集合三面拼合
//   6 文档守护神 File Keeper  — 试炼文件读写 + 词频统计
//   7 归纳之镜   Mirror of Abstraction — 类/递归/lambda 综合 boss

import type { MainQuestBoss, MainSubQuest } from '@/types/mainQuest';

export const MAIN_QUEST_BOSSES: readonly MainQuestBoss[] = [
  {
    chapter: 1,
    name: '算盘灵 · 计算工具守卫',
    kcRange: { startCode: 'PY43-KC-001', endCode: 'PY43-KC-007' },
    rewards: [
      { kind: 'coin', amount: 200 },
      { kind: 'item', amount: 1, ref: 'outfit:apprentice' },
    ],
    storyboardCue: '镇长 Py 将算盘交予你，作为计算思维实习生的徽章。',
  },
  {
    chapter: 2,
    name: '解释器学徒 · Python 语言守关',
    kcRange: { startCode: 'PY43-KC-008', endCode: 'PY43-KC-016' },
    rewards: [
      { kind: 'coin', amount: 320 },
      { kind: 'item', amount: 1, ref: 'title:python_speaker' },
    ],
    storyboardCue: 'Python 解释器在终端中打出一行 "Hello, learner!" 作为礼物。',
  },
  {
    chapter: 3,
    name: '二进制独眼 · 信息表示之灵',
    kcRange: { startCode: 'PY43-KC-017', endCode: 'PY43-KC-030' },
    rewards: [
      { kind: 'coin', amount: 420 },
      { kind: 'item', amount: 1, ref: 'storyboard:binary_dawn' },
    ],
    storyboardCue: '矿洞深处亮起 0/1 双色雾灯，照见进制转换的真章。',
  },
  {
    chapter: 4,
    name: '流程钟楼 · 自动化锻造神',
    kcRange: { startCode: 'PY43-KC-031', endCode: 'PY43-KC-038' },
    rewards: [
      { kind: 'coin', amount: 520 },
      { kind: 'item', amount: 1, ref: 'item:loop_bookmark' },
    ],
    storyboardCue: '校园广场的钟楼自此走时不再卡帧，循环与分支井然有序。',
  },
  {
    chapter: 5,
    name: '集合棱镜 · 数据结构灵兽',
    kcRange: { startCode: 'PY43-KC-039', endCode: 'PY43-KC-047' },
    rewards: [
      { kind: 'coin', amount: 620 },
      { kind: 'item', amount: 1, ref: 'outfit:captain' },
    ],
    storyboardCue: '码头商船升起绣有 list / dict / set 三面棱镜的银帆。',
  },
  {
    chapter: 6,
    name: '文档守护神 · 文件存取灵',
    kcRange: { startCode: 'PY43-KC-048', endCode: 'PY43-KC-053' },
    rewards: [
      { kind: 'coin', amount: 720 },
      { kind: 'item', amount: 1, ref: 'title:file_keeper' },
    ],
    storyboardCue: '图书馆深处档案柜重新归位，词频统计帮你找到失落的篇章。',
  },
  {
    chapter: 7,
    name: '归纳之镜 · 面向对象与抽象终极',
    kcRange: { startCode: 'PY43-KC-054', endCode: 'PY43-KC-065' },
    rewards: [
      { kind: 'coin', amount: 1000 },
      { kind: 'item', amount: 1, ref: 'outfit:graduate' },
      { kind: 'item', amount: 1, ref: 'title:pythontopia_alumnus' },
    ],
    storyboardCue: '镇长 Py 在毕业典礼把镇徽别在你身上，标志你已完成 7 章 65 KC。',
  },
] as const;

export const MAIN_QUESTS: readonly MainSubQuest[] = [
  // ───── 第 1 章 计算工具与计算思维：town_hub 喷泉广场 ─────
  {
    id: '1-1',
    chapter: 1,
    index: 1,
    title: '回望中国计算（KC-001）',
    hint: '去喷泉广场找镇长 Py，听他讲银河一号到神威·太湖之光的故事。',
    mapKey: 'town_hub',
    npcKey: 'mayor_py',
    kcCodes: ['PY43-KC-001'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'mayor_py' },
    rewards: [{ kind: 'coin', amount: 25 }],
  },
  {
    id: '1-2',
    chapter: 1,
    index: 2,
    title: '从算盘到 ENIAC（KC-002）',
    hint: '在工坊为「计算工具的演变」生成一份 STORYBOARD，看 5 段历史。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-002'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'STORYBOARD' },
    rewards: [{ kind: 'coin', amount: 30 }],
  },
  {
    id: '1-3',
    chapter: 1,
    index: 3,
    title: '计算思维 5 步（KC-003）',
    hint: '工坊里给 KC-003 生成讲义，把「发现 → 抽象 → 算法 → 实现 → 评价」记牢。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-003'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'HANDOUT' },
    rewards: [{ kind: 'coin', amount: 35 }, { kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-003' }],
  },
  {
    id: '1-4',
    chapter: 1,
    index: 4,
    title: '计算的概念（KC-006）',
    hint: '到训练场提交一道关于「什么是计算」的小练（PLACEHOLDER problemId，教师可补）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-006'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 40 }],
  },
  {
    id: '1-5',
    chapter: 1,
    index: 5,
    title: '计算科学 ≠ 计算机科学（KC-007）',
    hint: '工坊里读完一份 READING，理解两个学科的边界与交集。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-007'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'READING' },
    rewards: [{ kind: 'coin', amount: 50 }, { kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-007' }],
  },

  // ───── 第 2 章 Python 语言基础(2)：town_hub Python 启动 + farm_north 学习田 ─────
  {
    id: '2-1',
    chapter: 2,
    index: 1,
    title: '装好 Python（KC-008）',
    hint: '工坊生成 Python 环境搭建的 STORYBOARD，跟着步骤把 IDLE 跑起来。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-008'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'STORYBOARD' },
    rewards: [{ kind: 'coin', amount: 60 }],
  },
  {
    id: '2-2',
    chapter: 2,
    index: 2,
    title: '初识变量与运算符（KC-010）',
    hint: '工坊里给 KC-010 生成 CODE_LAB，在浏览器里 Pyodide 跑一段 a + b。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-010'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'coin', amount: 60 }],
  },
  {
    id: '2-3',
    chapter: 2,
    index: 3,
    title: '基本数据类型（KC-011）',
    hint: '完成一道 int / float / str 转换题（PLACEHOLDER problemId，教师可补）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-011'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 65 }],
  },
  {
    id: '2-4',
    chapter: 2,
    index: 4,
    title: 'input / print 双轨（KC-013）',
    hint: '工坊里把 KC-013 + 基础函数 KC-012 组合生成一份 PRACTICE。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-012', 'PY43-KC-013'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'PRACTICE' },
    rewards: [{ kind: 'coin', amount: 70 }],
  },
  {
    id: '2-5',
    chapter: 2,
    index: 5,
    title: '理解程序运行方式（KC-014）',
    hint: 'KC-014 + KC-016 共同推到 40 以上，搞清楚解释执行 vs 编译执行。',
    mapKey: 'farm_north',
    kcCodes: ['PY43-KC-014', 'PY43-KC-016'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-016', thresholdScore: 40 },
    rewards: [{ kind: 'coin', amount: 80 }, { kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-016' }],
  },

  // ───── 第 3 章 计算机信息表示及计算：mine_south 矿洞 0/1 ─────
  {
    id: '3-1',
    chapter: 3,
    index: 1,
    title: '0 和 1 的计算思维（KC-017）',
    hint: '到矿洞入口与守矿人对话，他会讲二进制的本质（PLACEHOLDER NPC mine_keeper）。',
    mapKey: 'mine_south',
    npcKey: 'mine_keeper',
    kcCodes: ['PY43-KC-017'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'mine_keeper' },
    rewards: [{ kind: 'coin', amount: 75 }],
  },
  {
    id: '3-2',
    chapter: 3,
    index: 2,
    title: '数值类型与运算（KC-019）',
    hint: '完成一道 int / float / complex 计算题（PLACEHOLDER）。',
    mapKey: 'mine_south',
    kcCodes: ['PY43-KC-019'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 80 }],
  },
  {
    id: '3-3',
    chapter: 3,
    index: 3,
    title: '字符串方法包（KC-022）',
    hint: '在工坊生成一份字符串处理 MINDMAP（split / strip / find / replace 等）。',
    mapKey: 'mine_south',
    kcCodes: ['PY43-KC-022'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'MINDMAP' },
    rewards: [{ kind: 'coin', amount: 85 }],
  },
  {
    id: '3-4',
    chapter: 3,
    index: 4,
    title: '字符串索引与切片（KC-024）',
    hint: 'KC-023 + KC-024 共同推到 35 以上，能熟练用 s[1:-1]。',
    mapKey: 'mine_south',
    kcCodes: ['PY43-KC-023', 'PY43-KC-024'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-024', thresholdScore: 35 },
    rewards: [{ kind: 'coin', amount: 90 }],
  },
  {
    id: '3-5',
    chapter: 3,
    index: 5,
    title: '进制转换（KC-030）',
    hint: '工坊生成 KC-030 的 PRACTICE，跑一遍 2/8/10/16 进制互转。',
    mapKey: 'mine_south',
    kcCodes: ['PY43-KC-030'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'PRACTICE' },
    rewards: [{ kind: 'coin', amount: 95 }, { kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-030' }],
  },

  // ───── 第 4 章 流程自动化：campus_east 校园广场 ─────
  {
    id: '4-1',
    chapter: 4,
    index: 1,
    title: '走通顺序结构（KC-038）',
    hint: '到校园广场图书馆入口（建筑触发），看顺序执行示意图。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-038'],
    evidenceRequired: { type: 'building_visit', mapKey: 'campus_east', tileX: 12, tileY: 15 },
    rewards: [{ kind: 'coin', amount: 100 }],
  },
  {
    id: '4-2',
    chapter: 4,
    index: 2,
    title: '分支与多条件（KC-033 + KC-035）',
    hint: '提交一道 if / elif / and / or 综合题（PLACEHOLDER）。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-033', 'PY43-KC-035'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 105 }],
  },
  {
    id: '4-3',
    chapter: 4,
    index: 3,
    title: 'for 循环与遍历（KC-031）',
    hint: '工坊生成 KC-031 的 CODE_LAB，跑一段 for i in range(n)。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-031'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'coin', amount: 110 }],
  },
  {
    id: '4-4',
    chapter: 4,
    index: 4,
    title: 'while 与循环控制（KC-032 + KC-037）',
    hint: 'KC-032 + KC-037 共同推到 45 以上，掌握 break / continue。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-032', 'PY43-KC-037'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-037', thresholdScore: 45 },
    rewards: [{ kind: 'coin', amount: 115 }],
  },
  {
    id: '4-5',
    chapter: 4,
    index: 5,
    title: '嵌套循环综合（KC-036）',
    hint: '工坊生成 KC-036 的 HANDOUT，理解二维问题里的循环嵌套。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-036'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'HANDOUT' },
    rewards: [{ kind: 'coin', amount: 125 }, { kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-036' }],
  },

  // ───── 第 5 章 数据结构：port_west 商船 ─────
  {
    id: '5-1',
    chapter: 5,
    index: 1,
    title: '列表与 Random（KC-039）',
    hint: '到码头与船长对话，他给你一个抽奖箱演示列表 + random（PLACEHOLDER NPC captain_def）。',
    mapKey: 'port_west',
    npcKey: 'captain_def',
    kcCodes: ['PY43-KC-039'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'captain_def' },
    rewards: [{ kind: 'coin', amount: 130 }],
  },
  {
    id: '5-2',
    chapter: 5,
    index: 2,
    title: '列表基本操作（KC-040）',
    hint: '工坊生成 KC-040 的 PRACTICE：append / pop / sort 一气呵成。',
    mapKey: 'port_west',
    kcCodes: ['PY43-KC-040'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'PRACTICE' },
    rewards: [{ kind: 'coin', amount: 135 }],
  },
  {
    id: '5-3',
    chapter: 5,
    index: 3,
    title: '字典基本操作（KC-042）',
    hint: '提交一道字典 CRUD 题（PLACEHOLDER）。',
    mapKey: 'port_west',
    kcCodes: ['PY43-KC-042'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 140 }],
  },
  {
    id: '5-4',
    chapter: 5,
    index: 4,
    title: '序列共性（KC-044 + KC-045）',
    hint: 'KC-045 掌握度推到 45 — 理解 list / tuple / str 都是序列。',
    mapKey: 'port_west',
    kcCodes: ['PY43-KC-044', 'PY43-KC-045'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-045', thresholdScore: 45 },
    rewards: [{ kind: 'coin', amount: 145 }],
  },
  {
    id: '5-5',
    chapter: 5,
    index: 5,
    title: '集合去重（KC-046 + KC-047）',
    hint: '工坊生成集合主题的 STORYBOARD，用 set 解决商船清单去重。',
    mapKey: 'port_west',
    kcCodes: ['PY43-KC-046', 'PY43-KC-047'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'STORYBOARD' },
    rewards: [{ kind: 'coin', amount: 160 }, { kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-047' }],
  },

  // ───── 第 6 章 数据存取与分析：campus_east 图书馆档案 ─────
  {
    id: '6-1',
    chapter: 6,
    index: 1,
    title: '初识文件系统（KC-051）',
    hint: '工坊生成 KC-051 的 HANDOUT，搞清 abs path vs relative path。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-051'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'HANDOUT' },
    rewards: [{ kind: 'coin', amount: 165 }],
  },
  {
    id: '6-2',
    chapter: 6,
    index: 2,
    title: '文件读写四步（KC-048）',
    hint: '提交一道 open / read / write / close 题（PLACEHOLDER）。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-048'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 170 }],
  },
  {
    id: '6-3',
    chapter: 6,
    index: 3,
    title: '文件写入实战（KC-050）',
    hint: '工坊生成 KC-050 的 MINDMAP，覆盖 w/a/r+ 三种模式。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-050'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'MINDMAP' },
    rewards: [{ kind: 'coin', amount: 180 }],
  },
  {
    id: '6-4',
    chapter: 6,
    index: 4,
    title: '文件读取数据处理（KC-052）',
    hint: 'KC-052 推到 50：把一份 CSV 读进来并求和。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-052'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-052', thresholdScore: 50 },
    rewards: [{ kind: 'coin', amount: 185 }],
  },
  {
    id: '6-5',
    chapter: 6,
    index: 5,
    title: '词频统计综合（KC-053）',
    hint: '工坊生成 KC-049 + KC-053 的 CODE_LAB，做一篇文章的 top-10 词频。',
    mapKey: 'campus_east',
    kcCodes: ['PY43-KC-049', 'PY43-KC-053'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'coin', amount: 200 }, { kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-053' }],
  },

  // ───── 第 7 章 归纳与抽象：全镇毕业典礼 ─────
  {
    id: '7-1',
    chapter: 7,
    index: 1,
    title: '函数作用域 LEGB（KC-060）',
    hint: '到图书馆翻一份原 PDF 课件 KC-060 任意页（building_visit 触发）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-060'],
    evidenceRequired: { type: 'building_visit', mapKey: 'town_hub', tileX: 32, tileY: 18 },
    rewards: [{ kind: 'coin', amount: 200 }],
  },
  {
    id: '7-2',
    chapter: 7,
    index: 2,
    title: 'lambda 短函数（KC-054）',
    hint: '工坊生成 KC-054 的 READING，理解何时该用 lambda。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-054'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'READING' },
    rewards: [{ kind: 'coin', amount: 210 }],
  },
  {
    id: '7-3',
    chapter: 7,
    index: 3,
    title: '函数递归（KC-061）',
    hint: '提交一道递归题，例如 N! 或斐波那契（PLACEHOLDER）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-061'],
    evidenceRequired: { type: 'submission', problemId: 0 },
    rewards: [{ kind: 'coin', amount: 220 }],
  },
  {
    id: '7-4',
    chapter: 7,
    index: 4,
    title: '模块化编程（KC-063）',
    hint: '工坊生成 KC-063 的 CODE_LAB，把一段长程序拆成多个模块。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-063'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'coin', amount: 230 }],
  },
  {
    id: '7-5',
    chapter: 7,
    index: 5,
    title: '类与面向对象（KC-064 + KC-065）',
    hint: 'KC-064 + KC-065 都到 60，触发毕业典礼。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-064', 'PY43-KC-065'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-065', thresholdScore: 60 },
    rewards: [
      { kind: 'coin', amount: 300 },
      { kind: 'kc_mastery', amount: 15, ref: 'PY43-KC-065' },
      { kind: 'item', amount: 1, ref: 'outfit:graduate' },
    ],
  },
] as const;

/** O(1) 用 id 查 sub-quest。 */
const MAIN_QUEST_INDEX: ReadonlyMap<string, MainSubQuest> = new Map(
  MAIN_QUESTS.map((quest) => [quest.id, quest]),
);

export function findMainQuestById(id: string): MainSubQuest | null {
  return MAIN_QUEST_INDEX.get(id) ?? null;
}

export function listSubQuestsByChapter(chapter: number): MainSubQuest[] {
  return MAIN_QUESTS.filter((quest) => quest.chapter === chapter).sort(
    (a, b) => a.index - b.index,
  );
}

export function findBossByChapter(chapter: number): MainQuestBoss | null {
  return MAIN_QUEST_BOSSES.find((b) => b.chapter === chapter) ?? null;
}
