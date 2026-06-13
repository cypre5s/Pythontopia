/**
 * 代码剧场（Code Theater）「可富动画 Python 构造子集」—— C-curriculum 归属
 * （见 docs/plans/2026-06-13-parallel-build-contracts.md §2 文件归属矩阵 / §5 共享契约）。
 *
 * 作用：在 packId=43《Python 语言基础》范围内，明确剧场"能演到什么程度"。它是
 * C-trace-engine（产轨迹）/ C-mappers（事件→渲染指令）/ C-stage-render（Phaser 演出）/
 * C-quest-sample（选样板任务）共同的**选材与降级判定数据依据**。
 *
 * 设计原则（呼应 types.ts 顶注与 Bret Victor「Learnable Programming」/ notional machine）：
 *   诚实优先。剧场永远能"诚实地走完每一行 + 显示真实 stdout"，但**绝不**为不在
 *   子集内的语义编造专属动画——超出子集即优雅降级（见 OUT_OF_SUBSET_DEGRADATION）。
 *
 * 红线：
 *   - 事件种类 `TraceEventKind`、舞台动作 `RenderAction` 只读引用自共享契约 types.ts，
 *     禁止在本文件重定义。
 *   - 所有 `kcCodes` 均引用 data/course-pack-43/json/course_kc.json 中**真实存在**的
 *     65 个 KC（PY43-KC-001..065），不伪造课件/知识点。
 *   - 本文件只含数据 + 纯函数，不引入 Phaser / 不触碰运行逻辑。
 *
 * 配套文档（KC 映射 + 按前置图排出的具身任务序列）：
 *   docs/plans/2026-06-13-code-theater-curriculum.md
 */
import type { RenderAction, TraceEventKind } from '@/services/codeTheater/types';

/** 某个构造在舞台上的动画保真度。 */
export type AnimationFidelity =
  | 'rich' // 有专属 TraceEvent + 专属舞台动作，完整演出
  | 'partial' // 借用近邻事件/动作部分演出，剩余语义降级
  | 'fallback'; // 仅 line 高亮 + stdout（即降级本身）

/** 子集内构造的稳定标识：供 quest / stage / mapper 做类型安全引用，避免散字符串。 */
export type AnimatableConstructId =
  | 'sequential-step'
  | 'variable-assignment'
  | 'arithmetic'
  | 'io-console'
  | 'branch'
  | 'while-loop'
  | 'for-loop'
  | 'list-index'
  | 'dict'
  | 'function-call-stack'
  | 'string-basics'
  | 'exception-surface';

/** 一个「可（部分）富动画」的 Python 构造及其剧场契约。 */
export interface AnimatableConstruct {
  /** 稳定 id（kebab-case）。 */
  readonly id: AnimatableConstructId;
  /** 面向学生的中文名（不暴露原始 KC 编码）。 */
  readonly label: string;
  /** 典型 Python 语法形态（文档/选材用，非穷举）。 */
  readonly syntax: string;
  /** 可富动画时对应的 TraceEvent 种类（types.ts §5.1，可多种）。 */
  readonly eventKinds: readonly TraceEventKind[];
  /** 舞台主要渲染动作（types.ts RenderAction，给 mapper/stage 的提示）。 */
  readonly renderActions: readonly RenderAction[];
  /** notional machine 舞台隐喻（一句话，傀儡在机关广场上"怎么演"）。 */
  readonly stageMetaphor: string;
  /** 动画保真度。 */
  readonly fidelity: AnimationFidelity;
  /** 是否 MVP 必达（首批演示链路必须能演）。 */
  readonly isMvp: boolean;
  /** 对应 packId=43 真实 KC code（按相关度排序，首个为主锚 KC）。 */
  readonly kcCodes: readonly string[];
  /** 能演到什么程度 + 明确边界（诚实，不夸大）。 */
  readonly coverage: string;
}

/**
 * 可富动画子集（12 项，覆盖 types.ts 全部 11 种 TraceEventKind）。
 *
 * MVP 必达（8 项，含 line 基线）：sequential-step / variable-assignment / arithmetic /
 *   io-console / branch / while-loop / for-loop / list-index——覆盖第 2~5 章
 *   「看→学」核心，足以独立讲清"可见数据 + 动态行为"。
 * Phase 2（高价值后置）：dict / function-call-stack（递归是杀手级 demo，但视觉系统更重）。
 * 部分/降级：string-basics（复用近邻事件）/ exception-surface（错误浮现，服务 Bug 矿洞）。
 */
export const ANIMATABLE_SUBSET: readonly AnimatableConstruct[] = [
  {
    id: 'sequential-step',
    label: '顺序执行（逐行步进）',
    syntax: '自上而下逐条语句；空操作/表达式语句',
    eventKinds: ['line'],
    renderActions: ['move_to_line'],
    stageMetaphor: '傀儡沿"脚本梯"一级一级往下走，当前行始终发光。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-038'],
    coverage:
      '任何代码都至少有逐行步进（line 是缺省回退）；这是剧场的"地基"动画，永不缺席。',
  },
  {
    id: 'variable-assignment',
    label: '变量与赋值',
    syntax: 'x = 5；x = x + 1（重绑定）',
    eventKinds: ['assign'],
    renderActions: ['set_var'],
    stageMetaphor: '傀儡把值写到一块"变量名牌"上；重新赋值就擦掉旧值翻成新牌面。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-010', 'PY43-KC-038', 'PY43-KC-011'],
    coverage:
      '标量/字符串绑定与同名重绑定可完整演（翻牌）；多目标赋值、序列解包降级为多张牌依次翻面。',
  },
  {
    id: 'arithmetic',
    label: '算术运算',
    syntax: '+  -  *  /  //  %  **',
    eventKinds: ['assign'],
    renderActions: ['set_var'],
    stageMetaphor: '在"演算台"把数合成一个结果，再交给变量名牌。',
    fidelity: 'partial',
    isMvp: true,
    kcCodes: ['PY43-KC-010', 'PY43-KC-019', 'PY43-KC-025'],
    coverage:
      '顶层表达式结果可演（绑定到变量时翻牌）；嵌套子表达式的求值次序**不逐步拆解**' +
      '（无对应 TraceEvent），只显示最终值——这是诚实边界。',
  },
  {
    id: 'io-console',
    label: '输入 / 输出（input / print）',
    syntax: 'print(...)；input(...)',
    eventKinds: ['output', 'input'],
    renderActions: ['speak', 'set_var'],
    stageMetaphor: 'print = 往小镇公告板贴一行字；input = NPC 递来纸条，傀儡读后把值写上名牌。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-012', 'PY43-KC-013', 'PY43-KC-023'],
    coverage:
      'print 文本、input 取值与回填变量可完整演；format()/f-string 的复杂格式槽只演最终文本，不演格式推导。',
  },
  {
    id: 'branch',
    label: '分支（if / elif / else）',
    syntax: 'if cond:  elif cond:  else:',
    eventKinds: ['branch'],
    renderActions: ['take_branch'],
    stageMetaphor: '傀儡走到岔路口，举起条件真/假牌，按结果选一条岔路走。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-033', 'PY43-KC-035', 'PY43-KC-034'],
    coverage:
      '单/双/多分支选路与嵌套选路可演；复合布尔（and/or/not）只演最终真假，不演短路的逐项求值过程。',
  },
  {
    id: 'while-loop',
    label: 'while 循环',
    syntax: 'while cond:',
    eventKinds: ['loop_iter', 'branch'],
    renderActions: ['walk_loop', 'take_branch'],
    stageMetaphor: '傀儡绕环形跑道，每圈先看"条件灯"（绿=继续/红=停），跑一圈计数 +1。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-032'],
    coverage:
      '正常迭代与正常退出可演；死循环触发 MAX_TRACE_STEPS 截断后，舞台诚实演"原地无限踱步"提示，而非假装跑完。',
  },
  {
    id: 'for-loop',
    label: 'for 循环与遍历（含 range）',
    syntax: 'for i in range(...)：；for x in seq:',
    eventKinds: ['loop_iter'],
    renderActions: ['walk_loop', 'set_var'],
    stageMetaphor: '傀儡沿一排站点逐格前进，循环变量名牌每到一站翻成当前元素。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-031', 'PY43-KC-037', 'PY43-KC-036'],
    coverage:
      'range 与遍历序列每轮可演；break/continue 演为"提前离场/跳过本格"；嵌套循环可演但视觉拥挤，建议任务限两层内。',
  },
  {
    id: 'list-index',
    label: '列表与索引',
    syntax: '[...]；lst[i]；append/insert/pop/remove',
    eventKinds: ['list_op'],
    renderActions: ['list_push', 'list_pop', 'list_index'],
    stageMetaphor: '列表是一排带编号的箱子：append 长出新箱、pop 搬走箱、索引 = 走到第 i 号箱。',
    fidelity: 'rich',
    isMvp: true,
    kcCodes: ['PY43-KC-040', 'PY43-KC-044', 'PY43-KC-045'],
    coverage:
      'index/append/pop/insert/set/remove 与长度变化可演（覆盖 ListOpEvent.op 全集）；' +
      '切片 [M:N:K] 只演结果箱排不逐元素拷贝；sort 只演前后排列不演比较过程。',
  },
  {
    id: 'dict',
    label: '字典',
    syntax: '{k: v}；d[k]；get/set/del',
    eventKinds: ['dict_op'],
    renderActions: ['dict_set'],
    stageMetaphor: '字典是一面"格子墙"，每格写 key→value：查 = 点亮某格，写 = 粉笔填格，删 = 擦格。',
    fidelity: 'rich',
    isMvp: false,
    kcCodes: ['PY43-KC-043', 'PY43-KC-042', 'PY43-KC-041'],
    coverage:
      'get/set/del 单键操作可演（覆盖 DictOpEvent.op 全集）；遍历 keys()/values()/items() 降级为多次点亮；哈希内部/视图对象不演。',
  },
  {
    id: 'function-call-stack',
    label: '函数与调用栈（含递归）',
    syntax: 'def f(...)：return；递归调用',
    eventKinds: ['call', 'return', 'assign'],
    renderActions: ['enter_call', 'exit_call', 'set_var'],
    stageMetaphor: '每次调用走进一间"侧屋"（栈 +1），带返回值走出来（栈 -1）；递归 = 一串嵌套侧屋。',
    fidelity: 'rich',
    isMvp: false,
    kcCodes: ['PY43-KC-061', 'PY43-KC-060', 'PY43-KC-059', 'PY43-KC-012'],
    coverage:
      '调用入栈/返回出栈/栈深、局部 vs 全局作用域（scope）可演，递归靠 depth 自然成立；' +
      '闭包、默认可变参数陷阱、装饰器不演。注：pack 43 无独立「def 定义函数」KC，演出锚定 KC-061(递归)/KC-060(作用域)。',
  },
  {
    id: 'string-basics',
    label: '字符串基础',
    syntax: 's[i]；s[M:N]；for ch in s；split/strip/replace',
    eventKinds: ['loop_iter', 'assign', 'output'],
    renderActions: ['walk_loop', 'set_var', 'speak'],
    stageMetaphor: '字符串是一条"字符珠链"：遍历 = 逐颗珠子点亮，索引 = 跳到第 i 颗。',
    fidelity: 'partial',
    isMvp: false,
    kcCodes: ['PY43-KC-024', 'PY43-KC-022', 'PY43-KC-023', 'PY43-KC-020'],
    coverage:
      '逐字符 for 遍历与索引/切片结果可演（复用 loop_iter/assign，无字符串专属事件）；' +
      '.split/.strip/.replace 只演调用前后值对比，chr/ord 演为值翻牌，内部扫描不演。',
  },
  {
    id: 'exception-surface',
    label: '异常浮现（运行时错误）',
    syntax: 'TypeError / IndexError / ZeroDivisionError / NameError …（运行时抛出）',
    eventKinds: ['exception'],
    renderActions: ['stumble'],
    stageMetaphor: '傀儡在出错那一行"绊倒"冒火花，停在原地——一眼看出哪行炸了。',
    fidelity: 'partial',
    isMvp: false,
    kcCodes: ['PY43-KC-013', 'PY43-KC-032', 'PY43-KC-040'],
    coverage:
      '只演"绊倒点 + 错误类型/消息"，服务 Bug 矿洞（debug-theater）的错误浮现。' +
      '**重要**：pack 43 无 try/except/raise KC，故不演异常处理控制流；上列 KC 是"易出错点"' +
      '（类型转换 ValueError / 死循环 / 列表越界 IndexError），非异常处理教学点。',
  },
];

/** 超出子集时的优雅降级策略（诚实边界的可执行声明）。 */
export interface DegradationPolicy {
  /** 降级规则说明。 */
  readonly description: string;
  /** 降级时仍记录/演出的事件种类。 */
  readonly fallbackEventKinds: readonly TraceEventKind[];
  /** 降级时舞台仍执行的动作。 */
  readonly fallbackRenderActions: readonly RenderAction[];
  /** pack 43 中真实存在、但剧场**不**富动画（仅降级）的代表性 KC 与原因。 */
  readonly outOfSubsetExamples: readonly { readonly kcCode: string; readonly reason: string }[];
}

/**
 * 凡不在 ANIMATABLE_SUBSET（且非其 coverage 已覆盖）的 Python 语义，剧场不编造专属动画，
 * 一律降级为 line 逐行高亮 + 真实 stdout。即"诚实走完每一行 + 显示真实输出"，不假装能演任意代码。
 */
export const OUT_OF_SUBSET_DEGRADATION: DegradationPolicy = {
  description:
    '不在子集内的语义 → 降级为 move_to_line 逐行步进 + speak 显示真实 stdout，' +
    '不为其编造专属舞台动作。剧场承诺"演得诚实"，不承诺"演得全"。',
  fallbackEventKinds: ['line', 'output'],
  fallbackRenderActions: ['move_to_line', 'speak'],
  outOfSubsetExamples: [
    { kcCode: 'PY43-KC-064', reason: '类与对象：对象图/方法解析超出傀儡舞台原语' },
    { kcCode: 'PY43-KC-065', reason: '封装/继承/多态：动态分派不富动画' },
    { kcCode: 'PY43-KC-048', reason: '文件读写：I/O 副作用无对应舞台道具，只演 stdout' },
    { kcCode: 'PY43-KC-054', reason: 'lambda/匿名函数：表达式级，不单开调用屋' },
    { kcCode: 'PY43-KC-047', reason: '集合类型：无序去重语义，list_op/dict_op 均不贴切' },
    { kcCode: 'PY43-KC-062', reason: '模块与导入：导入副作用/命名空间不富动画' },
  ],
};

/** MVP 必达构造（首批演示链路必须能演）。 */
export function mvpConstructs(): readonly AnimatableConstruct[] {
  return ANIMATABLE_SUBSET.filter((construct) => construct.isMvp);
}

/** 某个 KC code 关联到的可动画构造（一个 KC 可触及多个构造）。 */
export function constructsForKc(kcCode: string): readonly AnimatableConstruct[] {
  return ANIMATABLE_SUBSET.filter((construct) => construct.kcCodes.includes(kcCode));
}

/** 某个 TraceEvent 种类由哪些构造承载（一种事件可被多个构造复用，如 assign）。 */
export function constructsForEventKind(kind: TraceEventKind): readonly AnimatableConstruct[] {
  return ANIMATABLE_SUBSET.filter((construct) => construct.eventKinds.includes(kind));
}

/** 子集引用到的全部真实 KC code（去重升序），供校验"未伪造 KC"与覆盖统计。 */
export const ALL_REFERENCED_KC_CODES: readonly string[] = Array.from(
  new Set(ANIMATABLE_SUBSET.flatMap((construct) => construct.kcCodes)),
).sort();
