// W4-CHAPTER-NAV：恢复 R-11 时 FieldPage 的章节导航数据源。
//
// `kcCode` 字段沿用 R-11 命名（含义为「章节 slug」），用作：
//   1. 测试 testid 标识符（chapter-nav-<kcCode>）
//   2. icons_kc/<kcCode>/ 图标资产目录的 key
//
// 真实的 7 章 KC 编号已由 data/course-pack-43/json/course_kc.json 校对：
//   ch1=PY43-KC-001~007  ch2=PY43-KC-008~016  ch3=PY43-KC-017~030
//   ch4=PY43-KC-031~038  ch5=PY43-KC-039~047  ch6=PY43-KC-048~053
//   ch7=PY43-KC-054~065  （共 65 个 KC，与课程种子 65 KC 一致）
//
// 章节显示名已与真实课程 data/course-pack-43/json/course_chapter.json 对齐
// （取短名以适配侧栏 ~88px 图标按钮）。此前 R-11 旧名（容器结构/条件分支/循环结构…）
// 与各章真实 KC 内容错位（本课程先讲「流程自动化」后讲「数据结构」），已修正。
// 注意：`kcCode` slug 是 testid + icons_kc/<slug>/ 资产目录键，必须保持稳定，
// 故只改 `name`，不改 slug（slug 与新名语义不再字面对应，是已知取舍）。

export interface ChapterEntry {
  /** 章节 slug，等价于 icons_kc/<kcCode>/ 资产目录与 testid 标识符。 */
  kcCode: string;
  /** 侧栏显示文案。 */
  name: string;
  /** 图标资产相对 public 根的路径。 */
  iconPath: string;
  /** 章节序号（1~7），用于排序。 */
  order: number;
  /** PY43-KC-NNN 范围起点（含）。 */
  startKc: number;
  /** PY43-KC-NNN 范围终点（含）。 */
  endKc: number;
}

export const CHAPTERS: ReadonlyArray<ChapterEntry> = [
  {
    kcCode: 'chapter_basics',
    name: '第 1 章 · 计算思维',
    iconPath: '/assets/pixel/icons_kc/chapter_basics/lorc__brain.png',
    order: 1,
    startKc: 1,
    endKc: 7,
  },
  {
    kcCode: 'variable_assignment',
    name: '第 2 章 · 语言基础',
    iconPath: '/assets/pixel/icons_kc/variable_assignment/lorc__padlock.png',
    order: 2,
    startKc: 8,
    endKc: 16,
  },
  {
    kcCode: 'data_types_number_string_bool',
    name: '第 3 章 · 信息表示',
    iconPath: '/assets/pixel/icons_kc/data_types_number_string_bool/lorc__light-bulb.png',
    order: 3,
    startKc: 17,
    endKc: 30,
  },
  {
    kcCode: 'list_tuple_set_dict',
    name: '第 4 章 · 流程自动化',
    // R-11 spec 用 delapouite__chemical-tank.png，但该图标在 public 资产里不存在；
    // 改用 delapouite__stack.png（同目录现存且语义吻合「堆栈/容器」）。
    iconPath: '/assets/pixel/icons_kc/list_tuple_set_dict/delapouite__stack.png',
    order: 4,
    startKc: 31,
    endKc: 38,
  },
  {
    kcCode: 'control_flow_if_else',
    name: '第 5 章 · 数据结构',
    iconPath: '/assets/pixel/icons_kc/control_flow_if_else/delapouite__choice.png',
    order: 5,
    startKc: 39,
    endKc: 47,
  },
  {
    kcCode: 'loop_for_while',
    name: '第 6 章 · 数据存取分析',
    iconPath: '/assets/pixel/icons_kc/loop_for_while/lorc__cycle.png',
    order: 6,
    startKc: 48,
    endKc: 53,
  },
  {
    kcCode: 'function_def_args_return',
    name: '第 7 章 · 归纳与抽象',
    iconPath: '/assets/pixel/icons_kc/function_def_args_return/lorc__gears.png',
    order: 7,
    startKc: 54,
    endKc: 65,
  },
];

const KC_CODE_PATTERN = /^PY43-KC-(\d{3})$/;

/** 把 `PY43-KC-NNN` 解析成数字 NNN；非法返回 null。 */
export function parseKcNumber(kcCode: string | null | undefined): number | null {
  if (!kcCode) {
    return null;
  }
  const match = KC_CODE_PATTERN.exec(kcCode);
  if (!match) {
    return null;
  }
  return Number.parseInt(match[1]!, 10);
}

/** 把数字 NNN 还原成 `PY43-KC-NNN`（带 3 位 0 填充）。 */
export function formatKcNumber(num: number): string {
  return `PY43-KC-${num.toString().padStart(3, '0')}`;
}

/** 给定某 KC code，找出它属于哪个章节；非法/越界返回 null。 */
export function findChapterByKcCode(
  kcCode: string | null | undefined,
): ChapterEntry | null {
  const num = parseKcNumber(kcCode);
  if (num === null) {
    return null;
  }
  return CHAPTERS.find((ch) => num >= ch.startKc && num <= ch.endKc) ?? null;
}
