/**
 * 调试剧场 · DebugAgent 引导式诊断阶梯（纯逻辑层）。
 *
 * 把诊断拆成三阶「换述 → 拆解 → 指方向」（rephrase / decompose / point），逐阶引导学生
 * 自己定位 bug。**红线**：绝不输出可直接复制提交的完整修正代码。
 *
 * 优先消费 DebugAgent / 后端 HINT_LAYER_* 已生成的三层提示（{@link DebugHintLayerInput}）；
 * 缺省时回退到「苏格拉底式脚手架」——只用元认知追问 + 焦点信息引导，不含课件内容、不含答案。
 * 每一阶都过 {@link redactSolutionLeak} 守卫：疑似泄漏可运行解法即脱敏。
 */

import type { DiagnosticFocus, DiagnosticFocusKind } from './diagnosticFocus';

/** 三阶引导阶段：换述 → 拆解 → 指方向。 */
export type DebugLadderStage = 'rephrase' | 'decompose' | 'point';

/** DebugAgent 渐进提示输入（对齐 graph DebugAgentOutput.hint_layers / 后端 HINT_LAYER_*）。 */
export interface DebugHintLayerInput {
  /** 1=换述, 2=拆解, 3=指方向。 */
  layer: 1 | 2 | 3;
  headline: string;
  body: string;
}

/** 单阶引导步骤（已过红线守卫）。 */
export interface DebugGuideStep {
  stage: DebugLadderStage;
  /** 阶梯序号 1..3。 */
  order: 1 | 2 | 3;
  headline: string;
  body: string;
  /** 红线守卫命中：原文疑似泄漏可运行解法，已被脱敏。 */
  redacted: boolean;
  /** 该阶是否来自 DebugAgent（false 表示回退脚手架）。 */
  fromAgent: boolean;
}

/** {@link buildGuideLadder} 输入。 */
export interface BuildGuideLadderInput {
  focus: DiagnosticFocus;
  /** 来自 DebugAgent 的三层提示（可空：未接入时回退脚手架）。 */
  hintLayers?: readonly DebugHintLayerInput[];
}

const STAGE_BY_ORDER: Record<1 | 2 | 3, DebugLadderStage> = {
  1: 'rephrase',
  2: 'decompose',
  3: 'point',
};

const SOLUTION_REDACTED_NOTICE =
  '（此处疑似出现可直接复制的完整代码，已按「不泄答」红线脱敏：回到上一阶提示，先自己写一版再来对照。）';

const ORDERS: readonly (1 | 2 | 3)[] = [1, 2, 3];

/**
 * 构造三阶引导阶梯。有 DebugAgent 提示则按层映射（缺失层用脚手架补齐），否则整体回退脚手架。
 * 所有 body 都经 {@link redactSolutionLeak}，保证不泄漏完整解法。
 */
export function buildGuideLadder(input: BuildGuideLadderInput): DebugGuideStep[] {
  const scaffold = buildScaffold(input.focus);
  const layers = input.hintLayers;
  if (!layers || layers.length === 0) {
    return scaffold;
  }

  const byOrder = new Map<1 | 2 | 3, DebugHintLayerInput>();
  for (const layer of layers) {
    if ((layer.layer === 1 || layer.layer === 2 || layer.layer === 3) && !byOrder.has(layer.layer)) {
      byOrder.set(layer.layer, layer);
    }
  }

  return ORDERS.map((order) => {
    const layer = byOrder.get(order);
    if (!layer) {
      return scaffold[order - 1]!;
    }
    const guarded = redactSolutionLeak(layer.body);
    return {
      stage: STAGE_BY_ORDER[order],
      order,
      headline: layer.headline,
      body: guarded.text,
      redacted: guarded.redacted,
      fromAgent: true,
    };
  });
}

/**
 * 红线守卫：检测文本是否疑似含可直接复制的完整解法，命中即替换为脱敏提示。
 * 判据：① 含多行 fenced 代码块；② 连续 ≥3 行 Python 关键字行（与 graph DebugAgent 同源启发）。
 */
export function redactSolutionLeak(text: string): { text: string; redacted: boolean } {
  if (containsRunnableBlock(text)) {
    return { text: SOLUTION_REDACTED_NOTICE, redacted: true };
  }
  return { text, redacted: false };
}

const CODE_KEYWORD_RE =
  /^(def\s|class\s|for\s|while\s|if\s|elif\s|else\s*:|try\s*:|except|finally\s*:|return\b|import\s|from\s|with\s|print\s*\(|break\b|continue\b|pass\b)/;
const ASSIGNMENT_RE = /^[A-Za-z_][\w.[\]]*\s*(?:\+|-|\*|\/|\/\/|%|\*\*)?=\s*\S/;
const CALL_STATEMENT_RE = /^[A-Za-z_][\w.]*\s*\([^)]*\)\s*$/;

/** 单行是否「看起来像可运行 Python 语句」：关键字行 / 赋值 / 纯函数调用。 */
function looksLikeCodeLine(raw: string): boolean {
  const line = raw.trim();
  if (line.length === 0) {
    return false;
  }
  if (CODE_KEYWORD_RE.test(line)) {
    return true;
  }
  if (line.includes('==')) {
    return false; // 比较表达式不当作赋值，避免把「x == 5 时」误判
  }
  return ASSIGNMENT_RE.test(line) || CALL_STATEMENT_RE.test(line);
}

function containsRunnableBlock(text: string): boolean {
  const fenceMatch = /```[\s\S]*?```/.exec(text);
  if (fenceMatch) {
    const inner = fenceMatch[0].replace(/```/g, '');
    const codeLines = inner.split('\n').filter((line) => line.trim().length > 0);
    if (codeLines.length >= 2) {
      return true;
    }
  }

  let run = 0;
  for (const raw of text.split('\n')) {
    if (looksLikeCodeLine(raw)) {
      run += 1;
      if (run >= 3) {
        return true;
      }
    } else {
      run = 0;
    }
  }
  return false;
}

function buildScaffold(focus: DiagnosticFocus): DebugGuideStep[] {
  const lineRef = focus.line > 0 ? `第 ${focus.line} 行` : '出错处';
  return [
    {
      stage: 'rephrase',
      order: 1,
      headline: '第 1 阶 · 换个说法',
      body:
        `先用你自己的话复述一遍：这段代码本来想让傀儡做什么？现在它在${lineRef}卡住 / 跑偏了。` +
        '把「我以为它会……，结果它……」这句话补完整。',
      redacted: false,
      fromAgent: false,
    },
    {
      stage: 'decompose',
      order: 2,
      headline: '第 2 阶 · 拆开看',
      body:
        `把${lineRef}拆成更小的问题：对照变量快照，这一刻每个变量到底是什么值？` +
        '它符合你刚才的预期吗？第一个「对不上」的变量，往往就是线索。',
      redacted: false,
      fromAgent: false,
    },
    {
      stage: 'point',
      order: 3,
      headline: '第 3 阶 · 指个方向',
      body: `${pointDirectionByKind(focus.kind)}先只动这一处，再重试一次——别等完整答案，你离它只差一步。`,
      redacted: false,
      fromAgent: false,
    },
  ];
}

function pointDirectionByKind(kind: DiagnosticFocusKind): string {
  switch (kind) {
    case 'infinite_loop':
      return '对照「现象」那句话：问题更像「循环的结束条件永远不成立」——去检查循环变量有没有在每一轮真正变化、边界是不是写反了。';
    case 'exception':
      return '对照「现象」那句话：问题更像「某个值不是你以为的类型 / 范围」——回到抛错那一行，看它用到的变量此刻究竟是什么。';
    case 'output_divergence':
      return '对照「现象」那句话：问题更像「算得对但说得不对」——核对输出的格式、顺序、小数位是否和题面要求一致。';
    case 'last_step':
      return '对照「现象」那句话：没抛错不代表对——沿变量快照倒着看，找出第一个和你预期不符的中间结果。';
    default:
      return '对照「现象」那句话，先给问题归个类：是「循环停不下来」「变量被覆盖」还是「输出格式不符」？';
  }
}
