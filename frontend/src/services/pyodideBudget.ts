/**
 * Pyodide 预算与防滥用骨架（W4 第一阶段，纯逻辑层）。
 *
 * 参考设计：docs/gameplay/r12-deepen/08-pyodide-budget.md §2 资源预算 / §3 黑名单 / §4 静态扫描。
 * 仅实现"闸门"判定：是否允许执行、命中哪些规则、累计执行次数；不负责 Worker / Pyodide 加载，
 * 那部分由后续 `workerHost.ts` / `audit.ts` 等拆分文件接入（详见设计文档 §12.1）。
 *
 * 单一真相由后端 `pyodide_execution` 表保管（V116 迁移），本前端模块只做"本地节流 + 教育提示"。
 */

export interface PyodideBudgetConfig {
  /** 单次执行最长 CPU 时长，超时由 Worker.terminate 兜底（设计 §2 5s）。 */
  cpuTimeoutMs: number;
  /** 单次执行允许的 Pyodide heap 上限，仅作记录与 OOM 提示使用（设计 §2 64MB）。 */
  memoryLimitMb: number;
  /** 滚动 60s 窗口内允许的执行次数（设计 §2 20 次/分钟）。 */
  perMinuteLimit: number;
  /** UTC+8 自然日允许的执行次数（设计 §2 200 次/日）。 */
  perDayLimit: number;
}

export const DEFAULT_PYODIDE_BUDGET: PyodideBudgetConfig = {
  cpuTimeoutMs: 5_000,
  memoryLimitMb: 64,
  perMinuteLimit: 20,
  perDayLimit: 200,
};

export type PyodideExecutionStatus = 'ok' | 'timeout' | 'error';

export type PyodideDenyReason = 'minute_limit_exceeded' | 'day_limit_exceeded';

export interface CanExecuteResult {
  allowed: boolean;
  reason?: PyodideDenyReason;
}

/**
 * 静态扫描结果：
 * - `blocked` 命中即拒绝执行，前端在编辑器顶部弹教育模态框。
 * - `warnings` 仍允许执行，但在结果区上方加黄色提示横幅（设计 §4.1 Block / Warn 两档）。
 */
export interface StaticScanResult {
  warnings: string[];
  blocked: string[];
}

/**
 * 静态扫描禁用模块的顶级名。设计 §3.2 完整列表更长，骨架仅覆盖任务要求的 5 个高危项；
 * 后续补齐 `os` / `multiprocessing` / `asyncio` / `ctypes` 时在此一处追加即可。
 */
const BLOCKED_TOP_MODULES = new Set<string>([
  'socket',
  'urllib',
  'requests',
  'subprocess',
  'threading',
]);

const ONE_MINUTE_MS = 60_000;
const UTC_PLUS_8_OFFSET_MS = 8 * 60 * 60_000;

interface ExecutionRecord {
  at: number;
  durationMs: number;
  status: PyodideExecutionStatus;
}

/**
 * 把毫秒时间戳折算到 UTC+8 自然日的 `YYYY-MM-DD`。
 * 设计 §2 明确"UTC+8 自然日"，避免按本地时区计数导致海外学生当地凌晨被异常清零。
 */
function utcPlus8DayKey(timestampMs: number): string {
  const shifted = new Date(timestampMs + UTC_PLUS_8_OFFSET_MS);
  return shifted.toISOString().slice(0, 10);
}

function minuteBucket(timestampMs: number): number {
  return Math.floor(timestampMs / ONE_MINUTE_MS);
}

export class PyodideBudgetGuard {
  readonly config: PyodideBudgetConfig;

  /** key: epoch minute（Math.floor(ts/60000)），value: 该分钟已执行次数。 */
  private readonly minuteCounter = new Map<number, number>();
  /** key: UTC+8 `YYYY-MM-DD`，value: 当日已执行次数。 */
  private readonly dayCounter = new Map<string, number>();
  /** 仅供调试与教师 dashboard 后续聚合复用的轻量历史，骨架阶段不持久化。 */
  private readonly history: ExecutionRecord[] = [];

  constructor(config?: Partial<PyodideBudgetConfig>) {
    this.config = { ...DEFAULT_PYODIDE_BUDGET, ...config };
  }

  canExecute(): CanExecuteResult {
    const now = Date.now();
    const minuteCount = this.minuteCounter.get(minuteBucket(now)) ?? 0;
    if (minuteCount >= this.config.perMinuteLimit) {
      return { allowed: false, reason: 'minute_limit_exceeded' };
    }
    const dayCount = this.dayCounter.get(utcPlus8DayKey(now)) ?? 0;
    if (dayCount >= this.config.perDayLimit) {
      return { allowed: false, reason: 'day_limit_exceeded' };
    }
    return { allowed: true };
  }

  recordExecution(durationMs: number, status: PyodideExecutionStatus): void {
    const now = Date.now();
    const minuteKey = minuteBucket(now);
    const dayKey = utcPlus8DayKey(now);
    this.minuteCounter.set(minuteKey, (this.minuteCounter.get(minuteKey) ?? 0) + 1);
    this.dayCounter.set(dayKey, (this.dayCounter.get(dayKey) ?? 0) + 1);
    this.history.push({ at: now, durationMs, status });
  }

  staticScanCode(code: string): StaticScanResult {
    const warnings: string[] = [];
    const blocked: string[] = [];

    for (const moduleName of extractImportedModules(code)) {
      const topLevel = moduleName.split('.')[0] ?? '';
      if (BLOCKED_TOP_MODULES.has(topLevel)) {
        blocked.push(`PB-001: forbidden import \`${moduleName}\``);
      }
    }
    // `os.system` 是函数调用而非 import 路径，需要单独按字面调用模式拦截。
    if (/\bos\.system\s*\(/.test(code)) {
      blocked.push('PB-001: forbidden call `os.system(...)`');
    }

    for (const occurrence of findWhileTrueLoops(code)) {
      if (!/\b(break|return|raise)\b/.test(occurrence.body)) {
        warnings.push('PB-004: `while True:` without break/return/raise');
      }
    }

    const rangeRegex = /\brange\s*\(\s*10\s*\*\*\s*(\d+)\s*\)/g;
    let rangeMatch: RegExpExecArray | null;
    while ((rangeMatch = rangeRegex.exec(code)) !== null) {
      const exponentRaw = rangeMatch[1];
      if (!exponentRaw) continue;
      const exponent = Number(exponentRaw);
      if (Number.isFinite(exponent) && exponent >= 7) {
        warnings.push(`PB-005: range(10**${exponent}) iteration may freeze browser`);
      }
    }

    return { warnings, blocked };
  }

  resetDaily(): void {
    this.dayCounter.clear();
  }

  resetMinute(): void {
    this.minuteCounter.clear();
  }

  /** 仅供测试与 dashboard 调试用，骨架阶段不在生产代码读取。 */
  snapshot(): {
    minuteBuckets: number;
    dayBuckets: number;
    historyLength: number;
  } {
    return {
      minuteBuckets: this.minuteCounter.size,
      dayBuckets: this.dayCounter.size,
      historyLength: this.history.length,
    };
  }
}

function extractImportedModules(code: string): string[] {
  const modules: string[] = [];
  // 同时覆盖 `import x` / `import x.y` / `import x as y` / `from x.y import z` 四种语法。
  const importRegex = /^[ \t]*(?:import[ \t]+([\w.]+)|from[ \t]+([\w.]+)[ \t]+import\b)/gm;
  let match: RegExpExecArray | null;
  while ((match = importRegex.exec(code)) !== null) {
    const moduleName = match[1] ?? match[2];
    if (moduleName) modules.push(moduleName);
  }
  return modules;
}

interface WhileTrueOccurrence {
  /** 头部 `while True:` 行的缩进字符串。 */
  indent: string;
  /** 循环体已抽取的代码片段，便于上层做 `break/return/raise` 检测。 */
  body: string;
}

/**
 * 简易抽取 `while True:` 循环体：以头部缩进为基线，连续吃所有缩进更深或空白的行。
 * 不是完整 AST 解析（设计 §4.2 列了 `ast.parse` 完整方案），骨架阶段先用启发式覆盖
 * `while True: pass`、`while True:\n  ...` 两种主流写法即可。
 */
function findWhileTrueLoops(code: string): WhileTrueOccurrence[] {
  const occurrences: WhileTrueOccurrence[] = [];
  const lines = code.split('\n');
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i] ?? '';
    const headMatch = /^([ \t]*)while[ \t]+True[ \t]*:/.exec(line);
    if (!headMatch) continue;
    const indent = headMatch[1] ?? '';
    const inlineBody = line.slice(headMatch[0].length).trim();
    const bodyParts: string[] = [];
    if (inlineBody.length > 0) bodyParts.push(inlineBody);
    for (let j = i + 1; j < lines.length; j += 1) {
      const next = lines[j] ?? '';
      if (next.trim().length === 0) {
        bodyParts.push('');
        continue;
      }
      const nextIndentMatch = /^([ \t]*)/.exec(next);
      const nextIndent = nextIndentMatch?.[1] ?? '';
      if (nextIndent.length <= indent.length) break;
      bodyParts.push(next);
    }
    occurrences.push({ indent, body: bodyParts.join('\n') });
  }
  return occurrences;
}
