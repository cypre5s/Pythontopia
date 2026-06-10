// 70 个章节闯关塔关卡数据。
//
// 数据严格 ground 在 packId=43《Python 语言基础》的 7 章 65 KC（与 mainQuests.ts 同源）。
// 每章 9 关知识/技能小关 + 1 关章末 boss（共 70 关）。
// 关型分布按教育节奏：
//   - PDF 问答（看 PDF + 单选）→ 章首入门
//   - 填空 → 第 2 关，巩固基础概念
//   - 改错 → 第 3 关，发现典型 bug
//   - 简答（grounding）→ 第 4 关，让玩家说清概念
//   - 调试闭环 → 第 5 关，深化 NameError/TypeError 等
//   - 双轨（Pyodide+ 后端）→ 第 6 关，正式提交
//   - 多轮组合 → 第 7 关，3 题 input/print
//   - 教学反转 → 第 8 关，向 NPC 解释概念
//   - Bug 矿洞 → 第 9 关，30 秒挑战
//   - Boss → 第 10 关，4 阶段副本（跳 BossPage）

import type { ArenaLevel, ArenaLevelKind } from '@/types/arena';

const LEVEL_KIND_BY_INDEX: readonly ArenaLevelKind[] = [
  'pdf_question',
  'fill_blank',
  'debug',
  'short_answer',
  'closed_loop',
  'dual_track',
  'multi_round',
  'teach_back',
  'bug_mine',
  'boss',
];

interface ChapterTemplate {
  chapter: number;
  /** 章节名（陪伴 boss 战字幕） */
  name: string;
  /** 9 关每关锚定 KC code 数组（顺序 = 关号 1..9，第 10 关 = boss 用全章 KC） */
  kcByLevel: readonly (readonly string[])[];
  /** 9 关每关标题 + 提示 */
  titlesAndHints: readonly { title: string; hint: string }[];
  /** 9 关每关难度 1..5 */
  difficulties: readonly (1 | 2 | 3 | 4 | 5)[];
  /** 全章 KC range（用于 boss 第 10 关） */
  allKcs: readonly string[];
}

// ───────────────────────────────────────────────────────
// 章 1：计算工具与计算思维（KC-001..007）
// ───────────────────────────────────────────────────────
const CHAPTER_1: ChapterTemplate = {
  chapter: 1,
  name: '算盘灵 · 计算工具守卫',
  kcByLevel: [
    ['PY43-KC-001'], ['PY43-KC-002'], ['PY43-KC-003'], ['PY43-KC-004'],
    ['PY43-KC-005'], ['PY43-KC-006'], ['PY43-KC-007'], ['PY43-KC-002', 'PY43-KC-003'],
    ['PY43-KC-006'],
  ],
  titlesAndHints: [
    { title: '中国计算往事', hint: '读第 1 章 PDF 1-3 页，挑出银河、神威·太湖之光的关键年份。' },
    { title: '算盘到 ENIAC 填空', hint: '一段计算工具变迁的小段子，填上关键术语。' },
    { title: '思维 5 步改错', hint: '别人写错了「计算思维 5 步」的顺序，改正它。' },
    { title: '什么是计算（简答）', hint: '用 30 字简述「计算」的本质，AI 会引用 PDF 给评分。' },
    { title: 'NameError 闭环', hint: '一个未定义变量的程序，看堆栈跳到正确行修复。' },
    { title: '双轨：「Hello, Pythontopia」', hint: 'Pyodide 预览 → 正式提交，记入 submission_id。' },
    { title: '3 段计算思维短题', hint: '依次 3 题：定义/抽象/算法各一题。' },
    { title: '向小学生讲计算思维', hint: '在对话框向 NPC 解释「抽象」概念，至少 20 字。' },
    { title: 'Bug 矿洞：30 秒修', hint: '随机出 ImportError / NameError，30 秒内修完。' },
  ],
  difficulties: [1, 1, 2, 2, 2, 2, 3, 3, 3],
  allKcs: ['PY43-KC-001', 'PY43-KC-002', 'PY43-KC-003', 'PY43-KC-004', 'PY43-KC-005', 'PY43-KC-006', 'PY43-KC-007'],
};

// ───────────────────────────────────────────────────────
// 章 2：Python 语言基础(2)（KC-008..016）
// ───────────────────────────────────────────────────────
const CHAPTER_2: ChapterTemplate = {
  chapter: 2,
  name: '解释器学徒 · Python 语言守关',
  kcByLevel: [
    ['PY43-KC-008'], ['PY43-KC-010'], ['PY43-KC-011'], ['PY43-KC-009'],
    ['PY43-KC-012'], ['PY43-KC-013'], ['PY43-KC-014'], ['PY43-KC-015'],
    ['PY43-KC-016'],
  ],
  titlesAndHints: [
    { title: 'IDLE 装好了吗', hint: '读 PDF 1-5 页，回答 IDLE 启动后第 1 个提示符是哪个？' },
    { title: '变量与运算符填空', hint: '`a, b = 3, 4`，让 `a + b` 输出 7。' },
    { title: 'int / float / str 改错', hint: '一个 input 后忘转型的小程序，修复 TypeError。' },
    { title: '注释规范（简答）', hint: '解释 Python 注释 `#` 与 docstring `"""` 的区别。' },
    { title: '基础函数闭环', hint: '一个未 return 的函数，找出问题修复。' },
    { title: '双轨：input / print', hint: '读一行整数 n，输出 n × 2。' },
    { title: '3 段顺序结构小题', hint: '3 题依次：赋值 / 算术 / 类型转换。' },
    { title: '向 NPC 讲解解释器', hint: '解释「Python 是解释执行还是编译执行」，至少 20 字。' },
    { title: 'Bug 矿洞：缩进灾难', hint: '随机出 IndentationError，30 秒内修。' },
  ],
  difficulties: [1, 2, 2, 2, 2, 3, 3, 3, 4],
  allKcs: ['PY43-KC-008', 'PY43-KC-009', 'PY43-KC-010', 'PY43-KC-011', 'PY43-KC-012', 'PY43-KC-013', 'PY43-KC-014', 'PY43-KC-015', 'PY43-KC-016'],
};

// ───────────────────────────────────────────────────────
// 章 3：计算机信息表示及计算（KC-017..030）
// ───────────────────────────────────────────────────────
const CHAPTER_3: ChapterTemplate = {
  chapter: 3,
  name: '二进制独眼 · 信息表示之灵',
  kcByLevel: [
    ['PY43-KC-017'], ['PY43-KC-019'], ['PY43-KC-022'], ['PY43-KC-018'],
    ['PY43-KC-024'], ['PY43-KC-025'], ['PY43-KC-026'], ['PY43-KC-028'],
    ['PY43-KC-030'],
  ],
  titlesAndHints: [
    { title: '0 与 1 的计算思维', hint: '读 PDF 2-1，回答十进制 7 的二进制是？' },
    { title: '数值类型填空', hint: '`(1 + 2j) * 3` 等于多少？' },
    { title: '字符串方法改错', hint: '`s.split(",", 1).strip()` 哪里错了？' },
    { title: 'ASCII / Unicode（简答）', hint: '解释 ASCII 与 Unicode 的区别（≥ 20 字）。' },
    { title: '字符串切片闭环', hint: '`s = "Pythontopia"`，输出 "thon"。' },
    { title: '双轨：字符串拼接', hint: '读两个字符串 a, b，输出 a+b 长度。' },
    { title: '3 段格式化输出', hint: '依次：% 格式化 / format / f-string 各一题。' },
    { title: '向 NPC 讲进制转换', hint: '解释「二进制如何表示负数」，至少 25 字。' },
    { title: 'Bug 矿洞：编码地狱', hint: 'UnicodeDecodeError 突袭，30 秒内修。' },
  ],
  difficulties: [2, 2, 3, 3, 3, 3, 4, 4, 5],
  allKcs: ['PY43-KC-017', 'PY43-KC-018', 'PY43-KC-019', 'PY43-KC-020', 'PY43-KC-021', 'PY43-KC-022', 'PY43-KC-023', 'PY43-KC-024', 'PY43-KC-025', 'PY43-KC-026', 'PY43-KC-027', 'PY43-KC-028', 'PY43-KC-029', 'PY43-KC-030'],
};

// ───────────────────────────────────────────────────────
// 章 4：流程自动化（KC-031..038）
// ───────────────────────────────────────────────────────
const CHAPTER_4: ChapterTemplate = {
  chapter: 4,
  name: '流程钟楼 · 自动化锻造神',
  kcByLevel: [
    ['PY43-KC-038'], ['PY43-KC-033'], ['PY43-KC-031'], ['PY43-KC-032'],
    ['PY43-KC-035'], ['PY43-KC-037'], ['PY43-KC-036'], ['PY43-KC-034'],
    ['PY43-KC-031', 'PY43-KC-032'],
  ],
  titlesAndHints: [
    { title: '顺序结构第 1 步', hint: '读 PDF 4-1，按顺序写「打开冰箱 → 把大象关进去」。' },
    { title: 'if / elif 填空', hint: '依分数划等级 A/B/C，填空补全 elif。' },
    { title: 'for + range 改错', hint: '`for i in range(1, 10, 0)` 哪里错了？' },
    { title: 'while 与 break（简答）', hint: '解释 break 与 continue 的区别（≥ 25 字）。' },
    { title: '分支与条件闭环', hint: '一个未处理 else 的判分程序，修复让所有用例覆盖。' },
    { title: '双轨：累加 1..n', hint: '读 n，输出 1+2+…+n 之和。' },
    { title: '3 段嵌套循环', hint: '依次：99 乘法表 / 倒三角 / 棋盘格各一题。' },
    { title: '向 NPC 讲 for vs while', hint: '解释「什么时候用 for，什么时候用 while」，至少 30 字。' },
    { title: 'Bug 矿洞：死循环陷阱', hint: 'while 写错变量没更新，30 秒内修。' },
  ],
  difficulties: [2, 2, 3, 3, 3, 3, 4, 4, 5],
  allKcs: ['PY43-KC-031', 'PY43-KC-032', 'PY43-KC-033', 'PY43-KC-034', 'PY43-KC-035', 'PY43-KC-036', 'PY43-KC-037', 'PY43-KC-038'],
};

// ───────────────────────────────────────────────────────
// 章 5：数据结构（KC-039..047）
// ───────────────────────────────────────────────────────
const CHAPTER_5: ChapterTemplate = {
  chapter: 5,
  name: '集合棱镜 · 数据结构灵兽',
  kcByLevel: [
    ['PY43-KC-039'], ['PY43-KC-040'], ['PY43-KC-042'], ['PY43-KC-044'],
    ['PY43-KC-045'], ['PY43-KC-046'], ['PY43-KC-047'], ['PY43-KC-041'],
    ['PY43-KC-043'],
  ],
  titlesAndHints: [
    { title: '列表与 random', hint: '读 PDF 5-1，写一行用 random.choice 抽取列表元素。' },
    { title: 'list 基本操作填空', hint: '`xs = [3,1,2]`, 排序后 `xs[1]` 是？' },
    { title: '字典 CRUD 改错', hint: '`d = {"a":1}; d.add("b", 2)` 哪里错了？' },
    { title: 'tuple 不可变（简答）', hint: '解释 tuple 与 list 的核心区别（≥ 25 字）。' },
    { title: '序列共性闭环', hint: '用 in/len/slice 操作一个 tuple，输出指定区间。' },
    { title: '双轨：集合去重', hint: '读 N 个整数，输出去重后的个数。' },
    { title: '3 段字典遍历', hint: '依次：遍历 keys / values / items 各一题。' },
    { title: '向 NPC 讲 dict vs json', hint: '解释 dict 与 json 字符串的关系（≥ 25 字）。' },
    { title: 'Bug 矿洞：KeyError 突袭', hint: '随机 KeyError，30 秒内修。' },
  ],
  difficulties: [2, 3, 3, 3, 4, 4, 4, 4, 5],
  allKcs: ['PY43-KC-039', 'PY43-KC-040', 'PY43-KC-041', 'PY43-KC-042', 'PY43-KC-043', 'PY43-KC-044', 'PY43-KC-045', 'PY43-KC-046', 'PY43-KC-047'],
};

// ───────────────────────────────────────────────────────
// 章 6：数据存取与分析（KC-048..053）
// ───────────────────────────────────────────────────────
const CHAPTER_6: ChapterTemplate = {
  chapter: 6,
  name: '文档守护神 · 文件存取灵',
  kcByLevel: [
    ['PY43-KC-051'], ['PY43-KC-048'], ['PY43-KC-049'], ['PY43-KC-050'],
    ['PY43-KC-052'], ['PY43-KC-053'], ['PY43-KC-051'], ['PY43-KC-049'],
    ['PY43-KC-052'],
  ],
  titlesAndHints: [
    { title: '文件路径基本功', hint: '读 PDF 6-1，区分绝对路径与相对路径。' },
    { title: 'open() 填空', hint: 'with open(...) as f：模式 "r" / "w" / "a" 该选哪个？' },
    { title: 'read vs readlines 改错', hint: '一段读全文却只取首行的代码，找错。' },
    { title: 'with 语句（简答）', hint: '解释 with open 比 try-finally 好在哪（≥ 25 字）。' },
    { title: '词频统计闭环', hint: '读一段文本，输出最频繁的 3 个词。' },
    { title: '双轨：CSV 行计数', hint: '读 N 行 csv，输出行数。' },
    { title: '3 段文件 IO', hint: '依次：写文件 / 追加 / 读全文各一题。' },
    { title: '向 NPC 讲编码', hint: '解释 utf-8 与 gbk 的区别（≥ 25 字）。' },
    { title: 'Bug 矿洞：FileNotFoundError', hint: '路径写错，30 秒内修。' },
  ],
  difficulties: [3, 3, 3, 3, 4, 4, 4, 5, 5],
  allKcs: ['PY43-KC-048', 'PY43-KC-049', 'PY43-KC-050', 'PY43-KC-051', 'PY43-KC-052', 'PY43-KC-053'],
};

// ───────────────────────────────────────────────────────
// 章 7：归纳与抽象（KC-054..065）
// ───────────────────────────────────────────────────────
const CHAPTER_7: ChapterTemplate = {
  chapter: 7,
  name: '归纳之镜 · 面向对象与抽象终极',
  kcByLevel: [
    ['PY43-KC-054'], ['PY43-KC-056'], ['PY43-KC-058'], ['PY43-KC-060'],
    ['PY43-KC-061'], ['PY43-KC-062'], ['PY43-KC-063'], ['PY43-KC-064'],
    ['PY43-KC-065'],
  ],
  titlesAndHints: [
    { title: '类的概念', hint: '读 PDF 7-1，回答 class 与 object 的关系。' },
    { title: '继承填空', hint: '`class Dog(Animal):` 缺什么才能调父类构造？' },
    { title: '多态改错', hint: '一段重载方法不生效的代码，找错。' },
    { title: '封装（简答）', hint: '解释 Python 的「双下划线」属性约定（≥ 25 字）。' },
    { title: 'lambda 闭环', hint: '用 lambda + filter 抽出大于 10 的数。' },
    { title: '双轨：装饰器', hint: '写一个统计函数调用次数的 decorator。' },
    { title: '3 段递归', hint: '依次：阶乘 / fib / 二叉树深度各一题。' },
    { title: '向 NPC 讲抽象', hint: '解释「抽象」在工程中的意义（≥ 30 字）。' },
    { title: 'Bug 矿洞：无限递归', hint: 'RecursionError 突袭，30 秒内修。' },
  ],
  difficulties: [3, 3, 4, 4, 4, 5, 5, 5, 5],
  allKcs: ['PY43-KC-054', 'PY43-KC-055', 'PY43-KC-056', 'PY43-KC-057', 'PY43-KC-058', 'PY43-KC-059', 'PY43-KC-060', 'PY43-KC-061', 'PY43-KC-062', 'PY43-KC-063', 'PY43-KC-064', 'PY43-KC-065'],
};

const TEMPLATES: readonly ChapterTemplate[] = [
  CHAPTER_1, CHAPTER_2, CHAPTER_3, CHAPTER_4, CHAPTER_5, CHAPTER_6, CHAPTER_7,
];

function buildLevels(): ArenaLevel[] {
  const out: ArenaLevel[] = [];
  for (const tpl of TEMPLATES) {
    // 前 9 关
    for (let i = 0; i < 9; i += 1) {
      const idx = i + 1;
      out.push({
        id: `${tpl.chapter}-${idx}`,
        chapter: tpl.chapter,
        index: idx,
        kind: LEVEL_KIND_BY_INDEX[i] ?? 'pdf_question',
        title: tpl.titlesAndHints[i]?.title ?? `第 ${tpl.chapter} 章 · 关 ${idx}`,
        hint: tpl.titlesAndHints[i]?.hint ?? '',
        kcCodes: tpl.kcByLevel[i] ?? [],
        difficulty: tpl.difficulties[i] ?? 2,
        rewards: [],
      });
    }
    // 第 10 关：boss
    const bossKcRef = tpl.allKcs[0] ?? 'PY43-KC-001';
    out.push({
      id: `${tpl.chapter}-10`,
      chapter: tpl.chapter,
      index: 10,
      kind: 'boss',
      title: `章末 BOSS：${tpl.name}`,
      hint: '通关前 9 关后挑战，4 阶段副本，AI 五重审查出题。',
      kcCodes: tpl.allKcs,
      difficulty: 5,
      rewards: [{ kind: 'kc_mastery', amount: 15, ref: bossKcRef }],
    });
  }
  return out;
}

export const ARENA_LEVELS: readonly ArenaLevel[] = Object.freeze(buildLevels());

const _byChapter = new Map<number, readonly ArenaLevel[]>();
const _byId = new Map<string, ArenaLevel>();
for (const level of ARENA_LEVELS) {
  _byId.set(level.id, level);
  const list = _byChapter.get(level.chapter);
  if (list) {
    (_byChapter as Map<number, ArenaLevel[]>).set(level.chapter, [...list, level]);
  } else {
    _byChapter.set(level.chapter, [level]);
  }
}

export function arenaLevelsByChapter(chapter: number): readonly ArenaLevel[] {
  return _byChapter.get(chapter) ?? [];
}

export function findArenaLevel(id: string): ArenaLevel | undefined {
  return _byId.get(id);
}
