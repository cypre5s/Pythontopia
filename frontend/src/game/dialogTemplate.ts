/**
 * NPC 对话模板渲染器。
 *
 * data/npcs/<key>/dialog.json 的 text 字段里允许出现四种占位符，
 * 由 {@link renderDialogText} 统一替换为玩家真实数据：
 *
 * 1. `{{playerName}}` —— 玩家显示名。
 * 2. `{{stats.xxx}}` —— DialogContext.stats[xxx]，未提供时回退到 '—'，
 *    保证 UI 不会出现 `{{stats.xxx}}` 字面量泄露。
 * 3. `{{profileAdaptive:A | B}}` —— profileType === 'practical' 走 A，否则走 B。
 * 4. `{{styleAdaptive:视觉文案|逻辑文案|试错文案}}` —— 按 cognitiveStyle 三分支选择
 *    （'visual' 走第 1 段、'logical' 走第 2 段、'trial' 走第 3 段，'unknown'/其它兜底首段视觉文案），
 *    用于「看阶段」按认知风格因材施教。
 *
 * 支持任意层级嵌套，例如：
 *   `{{profileAdaptive:走吧 | 你还在第 {{stats.currentChapterOrder}} 章}}`
 * 渲染时从最内层占位符开始替换，每一遍 pass 只处理"不再含 {{ 的"占位符。
 *
 * 不抛异常：未知变量、未闭合占位符、超深嵌套都回退到合理字面量。
 */

export interface DialogTemplateContext {
  /** 玩家显示名，缺失时模板渲染器默认填 '学习者'。 */
  playerName: string;
  /** 'practical' 走 profileAdaptive 的 A 分支；'cautious' 或其他走 B 分支。 */
  profileType: string;
  /**
   * 认知风格三态：'visual' | 'logical' | 'trial' | 'unknown'，驱动 styleAdaptive 三分支。
   * 缺省 'unknown' 时按视觉型首分支兜底。
   */
  cognitiveStyle: string;
  /** 各类 stats key → 真实字符串。 */
  stats: Record<string, string>;
}

const DEFAULT_FALLBACK = '—';
/** 最大嵌套迭代轮次：实际对话嵌套不会超过 2 层，留 8 防御异常输入。 */
const MAX_PASSES = 8;

/**
 * 渲染对话模板字符串。
 *
 * 多次 pass 替换最内层占位符（即整体不再包含 `{{` 的 `{{...}}`）。
 * 若 8 次 pass 后仍有未解析的占位符，强制把残余 `{{xxx}}` 替换成
 * '—' 兜底，避免 UI 泄露字面量。
 */
export function renderDialogText(template: string, ctx: DialogTemplateContext): string {
  if (typeof template !== 'string' || template.length === 0) {
    return '';
  }
  // 非贪婪 + 负前瞻 "{{ ... 中间不再含 {{ ... }}" => 每次只匹配最内层占位符。
  const innerPattern = /\{\{((?:(?!\{\{).)+?)\}\}/g;
  let current = template;
  for (let pass = 0; pass < MAX_PASSES; pass += 1) {
    if (!current.includes('{{')) {
      return current;
    }
    const next = current.replace(innerPattern, (match, body: string) =>
      evaluatePlaceholder(body, ctx, match),
    );
    if (next === current) {
      break;
    }
    current = next;
  }
  // 兜底：清理残余无法解析的占位符，避免 UI 泄露 `{{...}}` 字面量。
  return current.replace(/\{\{[^{}]*\}\}/g, DEFAULT_FALLBACK);
}

/**
 * 解析单个占位符的内容（不含两侧 `{{` `}}`），返回真实字符串。
 *
 * `original` 是原始 `{{...}}`，仅用于 debug；通常不需要回退到原始字面量
 * （我们坚决不让占位符泄露），但保留参数让未来诊断更方便。
 */
function evaluatePlaceholder(
  body: string,
  ctx: DialogTemplateContext,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  original: string,
): string {
  const trimmed = body.trim();
  if (trimmed.length === 0) {
    return '';
  }

  // 二分支语法：profileAdaptive:
  const branchMatch = /^([a-zA-Z][a-zA-Z0-9]*)\s*:\s*(.*)$/s.exec(trimmed);
  if (branchMatch) {
    const tag = branchMatch[1] ?? '';
    const payload = branchMatch[2] ?? '';
    if (tag === 'profileAdaptive') {
      const [a, b] = splitFirstPipe(payload);
      return ctx.profileType === 'practical' ? a : b;
    }
    if (tag === 'styleAdaptive') {
      // 三分支：视觉 | 逻辑 | 试错。未知/缺省（unknown）回退首段视觉文案；
      // 某段为空时也回退视觉文案，避免作者漏写导致空气泡。
      const [visual, logical, trial] = splitThreeBranches(payload);
      switch (ctx.cognitiveStyle) {
        case 'logical':
          return logical || visual;
        case 'trial':
          return trial || visual;
        case 'visual':
        default:
          return visual;
      }
    }
    // 未来若扩展更多 tag（seasonAdaptive: ... | ... 等）可以在此追加，
    // 现阶段未知 tag 视为静默兜底，不让模板异常打断对话。
    return DEFAULT_FALLBACK;
  }

  if (trimmed === 'playerName') {
    return ctx.playerName && ctx.playerName.length > 0 ? ctx.playerName : '学习者';
  }

  if (trimmed.startsWith('stats.')) {
    const key = trimmed.slice('stats.'.length).trim();
    if (key.length === 0) {
      return DEFAULT_FALLBACK;
    }
    const value = ctx.stats[key];
    if (value === undefined || value === null || value === '') {
      return DEFAULT_FALLBACK;
    }
    return value;
  }

  // 直接的变量名（无前缀）兜底：把 {{foo}} 当 stats.foo 找一次，找不到给 '—'。
  const fallback = ctx.stats[trimmed];
  if (fallback !== undefined && fallback !== null && fallback !== '') {
    return fallback;
  }
  return DEFAULT_FALLBACK;
}

/**
 * 在第一个 `|` 处把字符串切成两段。
 *
 * profileAdaptive 的 payload 形如 `A | B`，其中 A、B 都可能
 * 已经被内层 pass 替换为含有数字 / 中文逗号的真实文本；只有最外层一道 `|`
 * 是分隔符，所以这里只切第一个，保留 B 内部出现的所有 `|`（极少见但允许）。
 */
function splitFirstPipe(s: string): [string, string] {
  const idx = s.indexOf('|');
  if (idx === -1) {
    return [s.trim(), ''];
  }
  return [s.slice(0, idx).trim(), s.slice(idx + 1).trim()];
}

/**
 * 在前两个 `|` 处把字符串切成三段（styleAdaptive 的 视觉|逻辑|试错）。
 *
 * 与 {@link splitFirstPipe} 同理：只认最外层前两道 `|` 为分隔符，第三段保留其内部可能出现的
 * 所有 `|`（极少见但允许）。缺段时对应位置回退空串，交由调用方按视觉首段兜底。
 */
function splitThreeBranches(s: string): [string, string, string] {
  const first = s.indexOf('|');
  if (first === -1) {
    return [s.trim(), '', ''];
  }
  const rest = s.slice(first + 1);
  const second = rest.indexOf('|');
  if (second === -1) {
    return [s.slice(0, first).trim(), rest.trim(), ''];
  }
  return [s.slice(0, first).trim(), rest.slice(0, second).trim(), rest.slice(second + 1).trim()];
}

/**
 * 工厂：把后端 `DialogContextDto` 适配为渲染器需要的纯本地结构。
 *
 * 抽出来是为了让 store / 组件不直接耦合 DTO 名空间，
 * 同时也方便测试构造最小上下文。
 */
export function createDialogContext(input: Partial<DialogTemplateContext>): DialogTemplateContext {
  return {
    playerName: input.playerName ?? '学习者',
    profileType: input.profileType ?? 'cautious',
    cognitiveStyle: input.cognitiveStyle ?? 'unknown',
    stats: input.stats ?? {},
  };
}
