/**
 * 代码剧场（Code Theater）共享类型 —— 跨 C-trace-engine / C-mappers / C-stage-render /
 * C-debug-theater / C-quest-sample 的唯一权威契约（见
 * docs/plans/2026-06-13-parallel-build-contracts.md §5）。
 *
 * 数据流：学生真实 Python --(Pyodide 插桩)--> Trace --(纯函数 TraceMapper)-->
 *         RenderCommand[] --(Phaser CodeTheaterScene)--> 傀儡把执行过程"走"给你看。
 *
 * 设计原则（Bret Victor「Learnable Programming」/ notional machine）：
 * 可见数据、动态行为、无隐藏状态。所有 C agent 必须 import 本文件，禁止各自重定义。
 */

/** 轨迹事件种类：覆盖 packId=43 初学者可动画子集。 */
export type TraceEventKind =
  | 'line' // 通用「执行到某行」（无富语义时回退）
  | 'assign' // 变量赋值 / 重绑定
  | 'loop_iter' // for/while 的一轮迭代
  | 'branch' // if/elif/else 条件求值与选路
  | 'call' // 函数调用（入栈）
  | 'return' // 函数返回（出栈）
  | 'list_op' // 列表操作（index/append/pop/...）
  | 'dict_op' // 字典操作（get/set/del）
  | 'output' // print / 写公告板
  | 'input' // input / NPC 递纸条
  | 'exception'; // 运行时异常（绊倒）

/** 所有事件共有字段。 */
export interface TraceEventBase {
  /** 自增步号，从 0 开始，对齐 RenderCommand.step。 */
  step: number;
  /** 1 基源码行号。 */
  line: number;
  kind: TraceEventKind;
}

export interface LineEvent extends TraceEventBase {
  kind: 'line';
}

export interface AssignEvent extends TraceEventBase {
  kind: 'assign';
  varName: string;
  /** 值的可显示字符串（已 repr，避免把任意对象塞进类型）。 */
  value: string;
  scope: 'global' | 'local';
}

export interface LoopIterEvent extends TraceEventBase {
  kind: 'loop_iter';
  /** 循环唯一标识（如 `for@2`），区分嵌套循环。 */
  loopId: string;
  loopType: 'for' | 'while';
  /** 第几轮（0 基）。 */
  iter: number;
  /** for 的循环变量名（while 可空）。 */
  varName?: string;
  /** 循环变量当前值的可显示字符串（while 可空）。 */
  value?: string;
}

export interface BranchEvent extends TraceEventBase {
  kind: 'branch';
  /** 条件表达式源文本。 */
  cond: string;
  result: boolean;
  /** 选中分支的行号。 */
  lineTaken: number;
}

export interface CallEvent extends TraceEventBase {
  kind: 'call';
  funcName: string;
  /** 实参的可显示字符串列表（已 repr）。 */
  args: string[];
  /** 调用后栈深（1 基）。 */
  depth: number;
}

export interface ReturnEvent extends TraceEventBase {
  kind: 'return';
  funcName: string;
  value: string;
  /** 返回后栈深。 */
  depth: number;
}

export interface ListOpEvent extends TraceEventBase {
  kind: 'list_op';
  op: 'index' | 'append' | 'pop' | 'insert' | 'set' | 'remove';
  target: string;
  /** 涉及的下标（index/set/insert/pop 时有）。 */
  position?: number;
  /** 涉及的值（可显示字符串）。 */
  value?: string;
  lengthAfter: number;
}

export interface DictOpEvent extends TraceEventBase {
  kind: 'dict_op';
  op: 'get' | 'set' | 'del';
  target: string;
  key: string;
  value?: string;
}

export interface OutputEvent extends TraceEventBase {
  kind: 'output';
  text: string;
}

export interface InputEvent extends TraceEventBase {
  kind: 'input';
  prompt: string;
  value: string;
}

export interface ExceptionEvent extends TraceEventBase {
  kind: 'exception';
  excType: string;
  message: string;
}

export type TraceEvent =
  | LineEvent
  | AssignEvent
  | LoopIterEvent
  | BranchEvent
  | CallEvent
  | ReturnEvent
  | ListOpEvent
  | DictOpEvent
  | OutputEvent
  | InputEvent
  | ExceptionEvent;

/** 轨迹生成默认步数预算（呼应 harness 预算理论：防死循环打爆内存/前端）。 */
export const MAX_TRACE_STEPS = 2000;

/** 一次执行的完整结构化轨迹。 */
export interface Trace {
  version: 1;
  /** 学生原始代码。 */
  source: string;
  events: TraceEvent[];
  stdout: string;
  /** 是否因超步数预算（死循环等）被截断。 */
  truncated: boolean;
  /** = events.length。 */
  stepCount: number;
}

/** 舞台渲染动作：傀儡在机关广场上「能做的事」。 */
export type RenderAction =
  | 'move_to_line' // 傀儡走到脚本梯的某一行（当前行发光）
  | 'set_var' // 举牌/翻牌，显示变量当前值
  | 'walk_loop' // 绕环形跑道一圈，计数 +1
  | 'take_branch' // 在岔路口按 true/false 选路
  | 'enter_call' // 走进调用侧屋（栈 +1）
  | 'exit_call' // 带返回值走出侧屋（栈 -1）
  | 'list_push' // 列表长出新箱
  | 'list_pop' // 列表移除箱
  | 'list_index' // 走到第 i 号箱
  | 'dict_set' // 在格子墙写入 key→value
  | 'stumble' // 异常：在某行绊倒冒火花
  | 'speak' // 公告板/对话（print/output）
  | 'world_effect'; // 任务世界条件的可见变化

/** 单条渲染指令：stage 顺序消费，按 durationMs 播放。 */
export interface RenderCommand {
  /** 对齐 TraceEvent.step。 */
  step: number;
  action: RenderAction;
  /** 仅承载可序列化基元，避免把 Phaser 对象/any 渗进契约层。 */
  payload: Record<string, string | number | boolean>;
  durationMs: number;
}

/** 映射上下文：mapper 把单个事件转成渲染指令时可读取的累积状态（只读）。 */
export interface MapContext {
  /** 已知变量当前值（mapper 不可变更，更新由引擎负责）。 */
  readonly variables: Readonly<Record<string, string>>;
  /** 当前调用栈深。 */
  readonly callDepth: number;
  /** 每条指令的默认时长（可被 mapper 覆盖）。 */
  readonly defaultDurationMs: number;
}

/** 纯函数：单个轨迹事件 → 渲染指令序列（C-mappers 实现，无副作用、可 fixture 测试）。 */
export type TraceMapper = (event: TraceEvent, ctx: MapContext) => RenderCommand[];

/** kind → mapper 注册表；缺省 kind 由引擎回退为单条 move_to_line。 */
export type MapperRegistry = Partial<Record<TraceEventKind, TraceMapper>>;
