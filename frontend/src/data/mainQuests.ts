// 35 主线子任务 + 7 boss 静态数据。
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

// ───────────────────────── 统一不变式（R-14，单局可通） ─────────────────────────
// 每个可测章（2–7）锚定「一个有判题真题的 KC」：该章的「练」(submission) 与「测」(kc_mastery)
// 指向同一个锚 KC，玩家「练完即测」——做 2 次 AC（+0.20 ×2 = 0.40）即过统一阈值 40。
//   ch2→KC-016 / ch3→KC-025 / ch4→KC-035 / ch5→KC-041 / ch6→KC-049 / ch7→KC-064
// 这修掉了旧 5-4/6-4/7-5 把「测」挂在题库 0 题 KC(045/052/065) 上、单局不可达的死点。
// 第 1 章为纯理论导入（packId=43 题库对其 7 个 KC 0 题，受 39 题硬约束不可新增），故无「练/测」，
// 是唯一例外。其余章内非锚 KC 由「看/学」子任务覆盖广度。
export const MAIN_QUESTS: readonly MainSubQuest[] = [
  // ───── 第 1 章 计算工具与计算思维：小镇 town_hub 启程 ─────
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
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-001' }],
  },
  {
    id: '1-2',
    chapter: 1,
    index: 2,
    title: '从算盘到 ENIAC（KC-002）',
    hint: '去广场找教授，听他用「计算工具的演变」给你上第 1 课（算盘 → ENIAC → 超算）。',
    mapKey: 'town_hub',
    npcKey: 'professor',
    kcCodes: ['PY43-KC-002'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'professor' },
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-002' }],
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
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-003' }],
  },
  {
    id: '1-4',
    chapter: 1,
    index: 4,
    title: '计算的概念（KC-006）',
    // 第 1 章为理论导入章，packId=43 题库无 KC-006 判题真题（且受「39 题」硬约束不可新增）。
    // 故此里程碑回归「学」性质——在工坊读懂「数据+运算=计算」，而非点进空题库。
    hint: '第 1 章是理论导入：在工坊为 KC-006「什么是计算」生成一份 READING，读懂「数据 + 运算 = 计算」。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-006'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'READING' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-006' }],
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
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-007' }],
  },

  // ───── 第 2 章 Python 语言基础(2)：小镇 town_hub Python 启动 ─────
  {
    id: '2-1',
    chapter: 2,
    index: 1,
    title: '装好 Python（KC-008）',
    hint: '工坊生成 Python 环境搭建的 STORYBOARD，跟着步骤把 IDLE 跑起来。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-008'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'STORYBOARD' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-008' }],
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
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-010' }],
  },
  {
    id: '2-3',
    chapter: 2,
    index: 3,
    title: '基本数据类型（KC-011）',
    hint: '到训练场完成真题「圆面积」：用 float() 把输入转成数值再计算（problem #476）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-011'],
    evidenceRequired: { type: 'submission', problemId: 476 },
    rewards: [{ kind: 'kc_mastery', amount: 8, ref: 'PY43-KC-011' }],
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
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-012' }],
  },
  {
    id: '2-5',
    chapter: 2,
    index: 5,
    title: '理解程序运行方式（KC-014）',
    hint: 'KC-014 + KC-016 共同推到 40 以上，搞清楚解释执行 vs 编译执行。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-014', 'PY43-KC-016'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-016', thresholdScore: 40 },
    rewards: [{ kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-016' }],
  },

  // ───── 第 3 章 计算机信息表示及计算：小镇 town_hub 二进制 0/1 ─────
  {
    id: '3-1',
    chapter: 3,
    index: 1,
    title: '0 和 1 的计算思维（KC-017）',
    hint: '在小镇找守矿人对话，他会讲二进制的本质。',
    mapKey: 'town_hub',
    npcKey: 'mine_keeper',
    kcCodes: ['PY43-KC-017'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'mine_keeper' },
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-017' }],
  },
  {
    id: '3-2',
    chapter: 3,
    index: 2,
    title: '整数位数提取（KC-025）',
    hint: '在小镇训练场完成真题「数位提取」：用 // 和 % 做整数运算（problem #477）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-025'],
    evidenceRequired: { type: 'submission', problemId: 477 },
    rewards: [{ kind: 'kc_mastery', amount: 8, ref: 'PY43-KC-025' }],
  },
  {
    id: '3-3',
    chapter: 3,
    index: 3,
    title: '字符串方法包（KC-022）',
    hint: '在工坊生成一份字符串处理 MINDMAP（split / strip / find / replace 等）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-022'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'MINDMAP' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-022' }],
  },
  {
    id: '3-4',
    chapter: 3,
    index: 4,
    // 统一不变式：本章「练」(3-2 #477) 与「测」(3-4) 锚定同一个有判题真题的 KC-025，
    // 玩家练完即测，单局可达阈值（2 次 AC ≈ 0.40）。
    title: '整数位数提取·达标（KC-025）',
    hint: '把刚练的「整数位数提取」掌握度刷到 40：在训练场对 #477 再 AC 一次即达标。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-025'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-025', thresholdScore: 40 },
    rewards: [{ kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-025' }],
  },
  {
    id: '3-5',
    chapter: 3,
    index: 5,
    title: '进制转换（KC-030）',
    hint: '工坊生成 KC-030 的 PRACTICE，跑一遍 2/8/10/16 进制互转。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-030'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'PRACTICE' },
    rewards: [{ kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-030' }],
  },

  // ───── 第 4 章 流程自动化：小镇 town_hub 流程钟楼 ─────
  {
    id: '4-1',
    chapter: 4,
    index: 1,
    title: '走通顺序结构（KC-038）',
    hint: '去镇里找馆长 Doc，听他讲「顺序结构」——程序默认一行行往下跑。',
    mapKey: 'town_hub',
    npcKey: 'librarian_doc',
    kcCodes: ['PY43-KC-038'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'librarian_doc' },
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-038' }],
  },
  {
    id: '4-2',
    chapter: 4,
    index: 2,
    title: '分支与多条件（KC-033 + KC-035）',
    hint: '在小镇训练场完成真题「三角形判断」：用 and 组合多条件 + if/else（problem #487）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-033', 'PY43-KC-035'],
    evidenceRequired: { type: 'submission', problemId: 487 },
    rewards: [{ kind: 'kc_mastery', amount: 8, ref: 'PY43-KC-033' }],
  },
  {
    id: '4-3',
    chapter: 4,
    index: 3,
    title: 'for 循环与遍历（KC-031）',
    hint: '工坊生成 KC-031 的 CODE_LAB，跑一段 for i in range(n)。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-031'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-031' }],
  },
  {
    id: '4-4',
    chapter: 4,
    index: 4,
    // 锚定同一有题 KC-035（练 4-2 #487 三角形判断）；循环控制 KC-037 仍由 4-3/4-5「学」覆盖。
    title: '多条件判断·达标（KC-035）',
    hint: '把刚练的「多条件判断与逻辑运算」掌握度刷到 40：在训练场对 #487 再 AC 一次即达标。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-035'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-035', thresholdScore: 40 },
    rewards: [{ kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-035' }],
  },
  {
    id: '4-5',
    chapter: 4,
    index: 5,
    title: '嵌套循环综合（KC-036）',
    hint: '工坊生成 KC-036 的 HANDOUT，理解二维问题里的循环嵌套。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-036'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'HANDOUT' },
    rewards: [{ kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-036' }],
  },

  // ───── 第 5 章 数据结构：小镇 town_hub 集合工坊 ─────
  {
    id: '5-1',
    chapter: 5,
    index: 1,
    title: '列表与 Random（KC-039）',
    hint: '在小镇找船长 Def 对话，他给你一个抽奖箱演示列表 + random。',
    mapKey: 'town_hub',
    npcKey: 'captain_def',
    kcCodes: ['PY43-KC-039'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'captain_def' },
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-039' }],
  },
  {
    id: '5-2',
    chapter: 5,
    index: 2,
    title: '列表基本操作（KC-040）',
    hint: '工坊生成 KC-040 的 PRACTICE：append / pop / sort 一气呵成。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-040'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'PRACTICE' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-040' }],
  },
  {
    id: '5-3',
    chapter: 5,
    index: 3,
    title: '字典应用·通讯录（KC-041）',
    hint: '在小镇训练场完成真题「通讯录」：增删查改一气呵成，体会字典在实际问题中的应用（problem #499）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-041'],
    evidenceRequired: { type: 'submission', problemId: 499 },
    rewards: [{ kind: 'kc_mastery', amount: 8, ref: 'PY43-KC-041' }],
  },
  {
    id: '5-4',
    chapter: 5,
    index: 4,
    // 修复+对齐：原测点 KC-045「序列类型」题库 0 题，单局不可达。改锚定有题 KC-041（练 5-3 #499）。
    title: '字典应用·达标（KC-041）',
    hint: '把刚练的「字典在实际问题中的应用」掌握度刷到 40：在训练场对通讯录题 #499 再 AC 一次即达标。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-041'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-041', thresholdScore: 40 },
    rewards: [{ kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-041' }],
  },
  {
    id: '5-5',
    chapter: 5,
    index: 5,
    title: '集合去重（KC-046 + KC-047）',
    hint: '工坊生成集合主题的 STORYBOARD，用 set 解决小镇集市清单去重。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-046', 'PY43-KC-047'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'STORYBOARD' },
    rewards: [{ kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-047' }],
  },

  // ───── 第 6 章 数据存取与分析：小镇 town_hub 档案馆 ─────
  {
    id: '6-1',
    chapter: 6,
    index: 1,
    title: '初识文件系统（KC-051）',
    hint: '工坊生成 KC-051 的 HANDOUT，搞清 abs path vs relative path。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-051'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'HANDOUT' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-051' }],
  },
  {
    id: '6-2',
    chapter: 6,
    index: 2,
    title: '基于文件的数据分析（KC-049）',
    hint: '在小镇训练场完成真题「学科等级」：读入数据并按规则处理输出（problem #508）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-049'],
    evidenceRequired: { type: 'submission', problemId: 508 },
    rewards: [{ kind: 'kc_mastery', amount: 8, ref: 'PY43-KC-049' }],
  },
  {
    id: '6-3',
    chapter: 6,
    index: 3,
    title: '文件写入实战（KC-050）',
    hint: '工坊生成 KC-050 的 MINDMAP，覆盖 w/a/r+ 三种模式。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-050'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'MINDMAP' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-050' }],
  },
  {
    id: '6-4',
    chapter: 6,
    index: 4,
    // 修复+对齐：原测点 KC-052「文件读取与数据处理」题库 0 题。改锚定有题 KC-049（练 6-2 #508）。
    title: '文件数据分析·达标（KC-049）',
    hint: '把刚练的「基于文件的数据分析」掌握度刷到 40：在训练场对 #508 再 AC 一次即达标。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-049'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-049', thresholdScore: 40 },
    rewards: [{ kind: 'kc_mastery', amount: 10, ref: 'PY43-KC-049' }],
  },
  {
    id: '6-5',
    chapter: 6,
    index: 5,
    title: '词频统计综合（KC-053）',
    hint: '工坊生成 KC-049 + KC-053 的 CODE_LAB，做一篇文章的 top-10 词频。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-049', 'PY43-KC-053'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'kc_mastery', amount: 12, ref: 'PY43-KC-053' }],
  },

  // ───── 第 7 章 归纳与抽象：小镇 town_hub 毕业典礼 ─────
  {
    id: '7-1',
    chapter: 7,
    index: 1,
    title: '函数作用域 LEGB（KC-060）',
    hint: '去镇里找复盘师 Sage，听他用 LEGB 讲清「变量在哪一层可见」。',
    mapKey: 'town_hub',
    npcKey: 'sage_review',
    kcCodes: ['PY43-KC-060'],
    evidenceRequired: { type: 'dialog_complete', npcKey: 'sage_review' },
    rewards: [{ kind: 'kc_mastery', amount: 5, ref: 'PY43-KC-060' }],
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
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-054' }],
  },
  {
    id: '7-3',
    chapter: 7,
    index: 3,
    // OOP 终章脊柱：练 7-3 与测 7-5 锚定同一有题 KC-064「类和对象基础」(#510)。
    title: '类和对象实战（KC-064）',
    hint: '到训练场完成真题「类和对象的创建」：用 class 定义 Dog / Cat 并调用方法（problem #510）。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-064'],
    evidenceRequired: { type: 'submission', problemId: 510 },
    rewards: [{ kind: 'kc_mastery', amount: 8, ref: 'PY43-KC-064' }],
  },
  {
    id: '7-4',
    chapter: 7,
    index: 4,
    // 递归 KC-061 从原 7-3「练」降为此处「学」（OOP 脊柱让位）；与模块化 KC-063 合并为一份 CODE_LAB。
    title: '递归与模块化（KC-061 + KC-063）',
    hint: '工坊生成一份 CODE_LAB，把递归（如阶乘 #511）与模块化拆分一起学懂。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-061', 'PY43-KC-063'],
    evidenceRequired: { type: 'resource_consumed', resourceType: 'CODE_LAB' },
    rewards: [{ kind: 'kc_mastery', amount: 6, ref: 'PY43-KC-061' }],
  },
  {
    id: '7-5',
    chapter: 7,
    index: 5,
    // 修复+对齐：原测点 KC-065「面向对象特征」题库 0 题。改锚定有题 KC-064（练 7-3 #510）；标题叙事不变。
    title: '类与面向对象（KC-064 + KC-065）',
    hint: '把刚练的「类和对象基础」掌握度刷到 40：在训练场对 #510 再 AC 一次即触发毕业典礼。',
    mapKey: 'town_hub',
    kcCodes: ['PY43-KC-064', 'PY43-KC-065'],
    evidenceRequired: { type: 'kc_mastery', kcCode: 'PY43-KC-064', thresholdScore: 40 },
    rewards: [
      { kind: 'kc_mastery', amount: 15, ref: 'PY43-KC-064' },
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

/**
 * 某章主线实际覆盖的核心 KC code 集合（去重）。
 * 用于把章内「非主线 KC」显式标为选修/拓展，避免初学者误以为必须全啃。
 */
export function mainlineKcCodesForChapter(chapter: number): string[] {
  const set = new Set<string>();
  for (const quest of MAIN_QUESTS) {
    if (quest.chapter === chapter) {
      quest.kcCodes.forEach((code) => set.add(code));
    }
  }
  return [...set];
}

export function findBossByChapter(chapter: number): MainQuestBoss | null {
  return MAIN_QUEST_BOSSES.find((b) => b.chapter === chapter) ?? null;
}

/** 规范学习闭环阶段：让「看→学→练→测」对玩家显式可见。 */
export type MainQuestPhase = 'watch' | 'learn' | 'practice' | 'assess';

export interface MainQuestPhaseMeta {
  phase: MainQuestPhase;
  /** 中文短标签（看/学/练/测）。 */
  label: string;
  /** 一句话说明此阶段在闭环里的角色。 */
  role: string;
}

const PHASE_META: Readonly<Record<MainQuestPhase, MainQuestPhaseMeta>> = Object.freeze({
  watch: { phase: 'watch', label: '看', role: '观察 / 访谈，建立直觉' },
  learn: { phase: 'learn', label: '学', role: '生成并消费学习资源（里程碑，非章节判据）' },
  practice: { phase: 'practice', label: '练', role: '提交真题，服务端判题' },
  assess: { phase: 'assess', label: '测', role: '掌握度达阈值，验证真的学会' },
});

/**
 * 把子任务 evidence 类型映射成规范闭环阶段（看/学/练/测）。
 * - dialog_complete / building_visit → 看
 * - resource_consumed → 学（输入里程碑，不作章节完成判据，见 chapterStandardMet）
 * - submission → 练
 * - kc_mastery → 测
 */
export function mainSubQuestPhase(quest: MainSubQuest): MainQuestPhaseMeta {
  switch (quest.evidenceRequired.type) {
    case 'submission':
      return PHASE_META.practice;
    case 'kc_mastery':
      return PHASE_META.assess;
    case 'resource_consumed':
      return PHASE_META.learn;
    case 'dialog_complete':
    case 'building_visit':
    default:
      return PHASE_META.watch;
  }
}
