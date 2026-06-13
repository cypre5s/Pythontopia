/**
 * 代码剧场「机关广场」纯状态机（无 Phaser / 无 DOM / 无副作用）。
 *
 * 职责：把一串 `RenderCommand`（见 services/codeTheater/types.ts §5.3）折叠成一份可被
 * 任何渲染器消费的「舞台快照」——当前发光行、变量牌、循环跑道圈数、调用栈深、公告板、
 * 分支岔路、列表箱、字典格子墙、绊倒状态。Phaser 绘制层只读本快照，不反向写状态。
 *
 * 设计取舍（呼应 Bret Victor「可见数据 / 无隐藏状态」）：`applyCommand` 是纯归约函数，
 * 返回**新对象**，便于时间轴播放器做「回退 / 跳转 = 从头重放」而无需记录逆操作。
 *
 * ── payload 字段约定（集成契约，C-mappers 须对齐；types.ts 仅冻结
 *    `payload: Record<string, string|number|boolean>`，未冻结各 action 的键名）──
 * 已与现网 C-mappers（services/codeTheater/mappers）的实际产出对齐：set_var 主键用
 * `varName`（同时兼容别名 `name`）；speak 的 input 通道用 `prompt`/`value` 兜底成一行。
 *   move_to_line : { line:number }
 *   set_var      : { varName:string (别名 name), value:string, scope?:'global'|'local', line?:number }
 *   walk_loop    : { loopId:string, loopType?:'for'|'while', iter?:number, lap?:number,
 *                    varName?:string, value?:string, line?:number }
 *   take_branch  : { result:boolean, lineTaken:number, cond?:string }
 *   enter_call   : { funcName:string, depth?:number, args?:string, line?:number }
 *   exit_call    : { funcName?:string, value?:string, depth?:number, line?:number }
 *   list_push    : { target:string, value:string, line?:number }
 *   list_pop     : { target:string, position?:number, line?:number }
 *   list_index   : { target:string, position:number, line?:number }
 *   dict_set     : { target:string, key:string, value:string, line?:number }
 *   speak        : { text?:string | prompt?:string + value?:string, line?:number }
 *   stumble      : { excType:string, message:string, line?:number }
 *   world_effect : { effect?:string, label?:string, line?:number }
 * 通用：任何 payload 若含数值 `line`，先据此推进发光行（再被各 action 的专有行覆盖）。
 */
import type { RenderAction, RenderCommand } from '@/services/codeTheater/types';

type Payload = RenderCommand['payload'];

/** 变量牌：举牌/翻牌显示某个变量的当前值。 */
export interface VariableCell {
  name: string;
  /** 已 repr 的可显示字符串（契约层不放任意对象）。 */
  value: string;
  scope: 'global' | 'local';
  /** 最近一次被赋值的 step，用于「翻牌」高亮。 */
  updatedAtStep: number;
}

/** 单条循环跑道的运行态。 */
export interface LoopRuntimeState {
  loopId: string;
  loopType: 'for' | 'while';
  /** 已跑圈数（= 已发生的迭代轮次）。 */
  laps: number;
  /** for 的循环变量名（while 可空）。 */
  varName?: string;
  /** 循环变量当前值（可显示字符串）。 */
  value?: string;
}

/** 最近一次分支选路。 */
export interface BranchRuntimeState {
  cond: string;
  result: boolean;
  lineTaken: number;
  atStep: number;
}

/** 列表箱（环形/排成一列的箱子）。 */
export interface ListBox {
  value: string;
}

/** 绊倒（运行时异常）信息。 */
export interface StumbleState {
  excType: string;
  message: string;
  line: number;
}

/** 机关广场完整快照。 */
export interface TheaterState {
  /** 傀儡所在的发光行（1 基）；-1 = 尚未开始。 */
  currentLine: number;
  /** 变量牌表：name → cell。 */
  variables: Record<string, VariableCell>;
  /** 变量首次出现顺序（保证渲染稳定，不随对象键序抖动）。 */
  variableOrder: string[];
  /** 循环跑道：loopId → 运行态。 */
  loops: Record<string, LoopRuntimeState>;
  /** 调用栈深（侧屋层数）。 */
  callDepth: number;
  /** 调用栈帧（函数名，自底向上）。 */
  callStack: string[];
  /** 公告板输出（print / output）逐行追加。 */
  noticeLines: string[];
  /** 最近一次分支选择（null = 尚无分支）。 */
  lastBranch: BranchRuntimeState | null;
  /** 列表箱：target → 箱子序列。 */
  lists: Record<string, ListBox[]>;
  /** 字典格子墙：target → key → value。 */
  dicts: Record<string, Record<string, string>>;
  /** 是否处于绊倒态（异常未恢复）。 */
  stumbled: boolean;
  /** 绊倒详情（null = 未绊倒）。 */
  stumble: StumbleState | null;
  /** 列表「走到第 i 号箱」的瞬时高亮（每步重置）。 */
  listFocus: { target: string; position: number } | null;
  /** 最近一次 world_effect 的标签（MVP 留桩，仅记录）。 */
  lastWorldEffect: string | null;
  /** 最近一条已应用指令的 action（驱动渲染层做对应动画）。 */
  lastAction: RenderAction | null;
  /** 最近一条已应用指令的 step；-1 = 尚未开始。 */
  lastStep: number;
  /** 本步刚被赋值/翻牌的变量名（每步重置，供翻牌高亮）。 */
  changedVar: string | null;
}

/** 全新的空舞台快照。 */
export function initialTheaterState(): TheaterState {
  return {
    currentLine: -1,
    variables: {},
    variableOrder: [],
    loops: {},
    callDepth: 0,
    callStack: [],
    noticeLines: [],
    lastBranch: null,
    lists: {},
    dicts: {},
    stumbled: false,
    stumble: null,
    listFocus: null,
    lastWorldEffect: null,
    lastAction: null,
    lastStep: -1,
    changedVar: null,
  };
}

// ── payload 安全读取（noUncheckedIndexedAccess：取值均为 T | undefined） ──
function readString(payload: Payload, key: string, fallback = ''): string {
  const value = payload[key];
  return typeof value === 'string' ? value : fallback;
}
function readNumber(payload: Payload, key: string, fallback = 0): number {
  const value = payload[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
function readBoolean(payload: Payload, key: string, fallback = false): boolean {
  const value = payload[key];
  return typeof value === 'boolean' ? value : fallback;
}
function optionalString(payload: Payload, key: string): string | undefined {
  const value = payload[key];
  return typeof value === 'string' ? value : undefined;
}
function optionalNumber(payload: Payload, key: string): number | undefined {
  const value = payload[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

/**
 * 纯归约：把单条渲染指令应用到上一帧快照，返回新帧。
 * 不修改入参；嵌套结构按需浅拷贝。未知 action 安全 no-op（仅推进 line/step）。
 */
export function applyCommand(prev: TheaterState, cmd: RenderCommand): TheaterState {
  const p = cmd.payload;
  const next: TheaterState = {
    ...prev,
    lastStep: cmd.step,
    lastAction: cmd.action,
    changedVar: null,
    listFocus: null,
  };

  // 通用：大多数 mapper 会在 payload 带 line，先据此推进发光行。
  const lineFromPayload = optionalNumber(p, 'line');
  if (lineFromPayload !== undefined) {
    next.currentLine = lineFromPayload;
  }

  switch (cmd.action) {
    case 'move_to_line': {
      next.currentLine = readNumber(p, 'line', prev.currentLine);
      break;
    }
    case 'set_var': {
      // C-mappers 主键是 varName；保留 name 作别名兼容自造 fixture / 其他生产者。
      const name = readString(p, 'varName') || readString(p, 'name');
      if (name) {
        const scope = readString(p, 'scope') === 'local' ? 'local' : 'global';
        const cell: VariableCell = {
          name,
          value: readString(p, 'value'),
          scope,
          updatedAtStep: cmd.step,
        };
        next.variables = { ...prev.variables, [name]: cell };
        next.variableOrder =
          prev.variables[name] !== undefined
            ? prev.variableOrder
            : [...prev.variableOrder, name];
        next.changedVar = name;
      }
      break;
    }
    case 'walk_loop': {
      const loopId = readString(p, 'loopId', 'loop');
      const prevLoop = prev.loops[loopId];
      const iter = optionalNumber(p, 'iter');
      const laps = iter !== undefined ? iter + 1 : (prevLoop?.laps ?? 0) + 1;
      next.loops = {
        ...prev.loops,
        [loopId]: {
          loopId,
          loopType: readString(p, 'loopType') === 'while' ? 'while' : 'for',
          laps,
          varName: optionalString(p, 'varName'),
          value: optionalString(p, 'value'),
        },
      };
      break;
    }
    case 'take_branch': {
      const lineTaken = readNumber(p, 'lineTaken', next.currentLine);
      next.lastBranch = {
        cond: readString(p, 'cond'),
        result: readBoolean(p, 'result'),
        lineTaken,
        atStep: cmd.step,
      };
      next.currentLine = lineTaken;
      break;
    }
    case 'enter_call': {
      const depth = optionalNumber(p, 'depth');
      next.callDepth = depth !== undefined ? depth : prev.callDepth + 1;
      next.callStack = [...prev.callStack, readString(p, 'funcName', 'fn')];
      break;
    }
    case 'exit_call': {
      const depth = optionalNumber(p, 'depth');
      next.callDepth = depth !== undefined ? depth : Math.max(0, prev.callDepth - 1);
      next.callStack = prev.callStack.slice(0, Math.max(0, prev.callStack.length - 1));
      break;
    }
    case 'list_push': {
      const target = readString(p, 'target', 'list');
      next.lists = {
        ...prev.lists,
        [target]: [...(prev.lists[target] ?? []), { value: readString(p, 'value') }],
      };
      break;
    }
    case 'list_pop': {
      const target = readString(p, 'target', 'list');
      const boxes = prev.lists[target] ?? [];
      const position = optionalNumber(p, 'position');
      const nextBoxes =
        position !== undefined && position >= 0 && position < boxes.length
          ? boxes.filter((_, i) => i !== position)
          : boxes.slice(0, Math.max(0, boxes.length - 1));
      next.lists = { ...prev.lists, [target]: nextBoxes };
      break;
    }
    case 'list_index': {
      next.listFocus = {
        target: readString(p, 'target', 'list'),
        position: readNumber(p, 'position', 0),
      };
      break;
    }
    case 'dict_set': {
      const target = readString(p, 'target', 'dict');
      const key = readString(p, 'key');
      if (key) {
        next.dicts = {
          ...prev.dicts,
          [target]: { ...(prev.dicts[target] ?? {}), [key]: readString(p, 'value') },
        };
      }
      break;
    }
    case 'speak': {
      // output 通道带 text；input 通道（NPC 递纸条）带 prompt/value，拼成一行兜底。
      let text = readString(p, 'text');
      if (!text) {
        const prompt = readString(p, 'prompt');
        const value = readString(p, 'value');
        text = prompt ? `${prompt}${value ? ` ${value}` : ''}` : value;
      }
      next.noticeLines = [...prev.noticeLines, text];
      break;
    }
    case 'stumble': {
      const stumble: StumbleState = {
        excType: readString(p, 'excType', 'Error'),
        message: readString(p, 'message'),
        line: readNumber(p, 'line', next.currentLine),
      };
      next.stumbled = true;
      next.stumble = stumble;
      next.currentLine = stumble.line;
      break;
    }
    case 'world_effect': {
      next.lastWorldEffect = optionalString(p, 'effect') ?? readString(p, 'label', 'effect');
      break;
    }
    default: {
      // 未知 action：保持已应用的通用 line/step 推进，不抛错（向前兼容新 action 桩）。
      break;
    }
  }

  return next;
}

/**
 * 从初始态折叠前 `count` 条指令，得到对应帧。
 * 时间轴播放器的「回退 / 跳转」用它从头重放（指令量 ≤ MAX_TRACE_STEPS，重放成本可接受）。
 */
export function computeStateAt(commands: readonly RenderCommand[], count: number): TheaterState {
  const clamped = Math.max(0, Math.min(Math.floor(count), commands.length));
  let state = initialTheaterState();
  for (let i = 0; i < clamped; i += 1) {
    const cmd = commands[i];
    if (cmd) {
      state = applyCommand(state, cmd);
    }
  }
  return state;
}
