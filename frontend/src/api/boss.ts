// Boss 副本前端 API。
//
// 优先调真后端 `/api/world/boss/...`；出题与判题默认都 fail closed。仅隔离开发测试可由调用方
// 显式 allowGoldenFallback=true 使用本地 golden 题，生产页面绝不自动把模板冒充真实业务题。
// **判题不降级**：submitBossAttempt 失败一律抛出，
// 由调用方按「本次不计分」处理，绝不返回伪造判定。
//
// 真后端契约：
//   POST /api/world/boss/{chapter}/problem  body: { stage }  → BossProblemDto
//   POST /api/world/boss/{chapter}/attempt  body: BossAttemptRequest  → BossAttemptResultDto

import { http, ApiClientError } from './http';
import type {
  BossAttemptRequest,
  BossAttemptResultDto,
  BossPrefetchStatusDto,
  BossProblemDto,
  BossProblemRequest,
} from './dto/Boss';
import { findBossByChapter } from '@/data/bossCatalog';

const BOSS_PROBLEM_TIMEOUT_MS = 15_000;
let pendingAttempt: { fingerprint: string; attemptId: string } | null = null;

function newAttemptId(): string {
  const cryptoApi = globalThis.crypto;
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID();
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function attemptIdFor(payload: BossAttemptRequest): string {
  if (payload.attemptId) return payload.attemptId;
  const fingerprint = JSON.stringify([
    payload.problemId,
    payload.chapter,
    payload.stage,
    payload.code,
    payload.elapsedSec,
  ]);
  if (pendingAttempt?.fingerprint === fingerprint) return pendingAttempt.attemptId;
  pendingAttempt = { fingerprint, attemptId: newAttemptId() };
  return pendingAttempt.attemptId;
}

const GOLDEN_TEMPLATES: Record<number, GoldenTemplate[]> = {
  1: [
    {
      title: 'Hello, NameError！',
      statement:
        '输入两个由空格分隔的整数 a b（无需 input prompt），输出 `a + b` 的值。\n\n例：输入 `2 3`，输出 `5`。',
      sampleInput: '2 3',
      sampleOutput: '5',
      kcCode: 'PY43-KC-001',
      solution: 'a, b = map(int, input().split())\nprint(a + b)',
    },
    {
      title: '请回答自己的名字',
      statement: '读入一行字符串 name，输出 `Hello, {name}!`。',
      sampleInput: 'Py',
      sampleOutput: 'Hello, Py!',
      kcCode: 'PY43-KC-002',
      solution: 'name = input()\nprint(f"Hello, {name}!")',
    },
    {
      title: '注释行计数',
      statement:
        '读入 N 行字符串（首行是 N），输出其中以 `#` 起头的注释行行数。',
      sampleInput: '3\n# hello\nprint(1)\n# world',
      sampleOutput: '2',
      kcCode: 'PY43-KC-005',
      solution:
        'n = int(input())\ncount = 0\nfor _ in range(n):\n    if input().lstrip().startswith("#"):\n        count += 1\nprint(count)',
    },
    {
      title: '综合：温度换算',
      statement:
        '读入一个浮点数 c 表示摄氏度，输出对应的华氏度 (c * 9/5 + 32)，保留 1 位小数。',
      sampleInput: '36.6',
      sampleOutput: '97.9',
      kcCode: 'PY43-KC-004',
      solution: 'c = float(input())\nprint(f"{c * 9 / 5 + 32:.1f}")',
    },
  ],
};

interface GoldenTemplate {
  title: string;
  statement: string;
  sampleInput: string;
  sampleOutput: string;
  kcCode: string;
  solution: string;
}

function buildGoldenProblem(
  chapter: number,
  stage: number,
  preferredKc?: string,
  preferredKind?: string,
): BossProblemDto {
  const templates = GOLDEN_TEMPLATES[chapter];
  const fallback: GoldenTemplate = {
    title: '占位题目（待 graph 服务接入）',
    statement:
      '当前章节没有可用的 golden test 题。请联系教师补充，或等待 graph 服务接通后由 AI 生成。',
    sampleInput: '0',
    sampleOutput: '0',
    kcCode: findBossByChapter(chapter)?.kcRange.startCode ?? 'PY43-KC-001',
    solution: 'print(input())',
  };
  // 若 preferredKc 命中本章某 template，优先用之让玩家"刷弱项"有效果。
  let template: GoldenTemplate;
  if (preferredKc && templates) {
    template = templates.find((t) => t.kcCode === preferredKc) ?? templates[Math.max(0, Math.min(stage - 1, templates.length - 1))] ?? fallback;
  } else {
    const idx = Math.max(0, Math.min(stage - 1, (templates?.length ?? 1) - 1));
    template = templates?.[idx] ?? fallback;
  }
  const kindPatch = buildKindPatch(template, preferredKind);
  return {
    problemId: `golden-c${chapter}-s${stage}${preferredKc ? `-${preferredKc}` : ''}`,
    chapter,
    stage,
    difficulty: Math.min(5, stage),
    kcCode: template.kcCode,
    statement: kindPatch.statement,
    hints: [],
    sampleInput: template.sampleInput,
    sampleOutput: template.sampleOutput,
    totalTestCases: 3,
    reviewed: false,
    aiGenerated: false,
    ...kindPatch.extra,
  };
}

function buildKindPatch(
  template: GoldenTemplate,
  preferredKind?: string,
): { statement: string; extra: Partial<BossProblemDto> } {
  const baseStatement = `${template.title}\n\n${template.statement}`;
  const buggyCode = buildBuggyCode(template);
  switch (preferredKind) {
    case 'debug':
      return {
        statement: [
          `${template.title} · 改错`,
          '',
          '下面的程序与题目要求不一致。请先定位可疑行，再提交修复后的完整代码。',
          '',
          `题目要求：${template.statement}`,
        ].join('\n'),
        extra: { buggyCode, starterCode: buggyCode },
      };
    case 'bug_mine':
      return {
        statement: [
          `${template.title} · Bug 矿洞`,
          '',
          '30 秒内修复下面程序的关键 Bug。提交修复后的完整代码，样例必须通过。',
          '',
          `题目要求：${template.statement}`,
        ].join('\n'),
        extra: { buggyCode, starterCode: buggyCode },
      };
    case 'fill_blank':
      return {
        statement: `${template.title} · 代码填空\n\n${buildFillBlankCode(template)}`,
        extra: {},
      };
    case 'closed_loop':
      return {
        statement: [
          `${template.title} · 调试闭环`,
          '',
          '阶段 1 阅读：程序运行结果与样例不一致。',
          '',
          buggyCode,
          '',
          `样例输入：${template.sampleInput}`,
          `期望输出：${template.sampleOutput}`,
          '请完成定位并提交修复后的完整代码。',
        ].join('\n'),
        extra: { buggyCode, starterCode: buggyCode },
      };
    case 'short_answer':
      return {
        statement: `${template.title} · 概念简答\n\n请用自己的话说明这道题涉及的 Python 知识点，并解释为什么样例输出是 ${template.sampleOutput}。`,
        extra: {},
      };
    case 'pdf_question':
      return {
        statement: `${template.title} · 课件问答\n\n结合课程包 43 的相关知识，说明这道题应如何处理输入、转换和输出格式。\n\n题目背景：${template.statement}`,
        extra: {},
      };
    case 'multi_round':
      return {
        statement: [
          `${template.title} · 组合挑战`,
          '',
          `第 1 题：解释输入样例 ${JSON.stringify(template.sampleInput)} 中每个值的含义。`,
          '---',
          `第 2 题：写出能得到样例输出 ${JSON.stringify(template.sampleOutput)} 的完整 Python 程序。`,
          '---',
          '第 3 题：说明你的程序如何处理边界情况。',
        ].join('\n'),
        extra: { starterCode: template.solution },
      };
    case 'dual_track':
    default:
      return { statement: baseStatement, extra: { starterCode: '' } };
  }
}

function buildFillBlankCode(template: GoldenTemplate): string {
  const lines = template.solution.split('\n');
  if (lines.length === 0) return template.statement;
  const target = lines.find((line) => line.includes('print')) ?? lines[lines.length - 1] ?? '';
  return lines.map((line) => (line === target ? line.replace(/[A-Za-z_][A-Za-z0-9_]*(?=\()/, '___') : line)).join('\n');
}

function buildBuggyCode(template: GoldenTemplate): string {
  if (template.kcCode === 'PY43-KC-001') {
    return 'a, b = input().split()\nprint(a + b)\n';
  }
  if (template.kcCode === 'PY43-KC-002') {
    return 'name = input\nprint("Hello, name!")\n';
  }
  if (template.solution.includes('count += 1')) {
    return template.solution.replace('count += 1', 'count = 1');
  }
  if (template.solution.includes(':.1f')) {
    return template.solution.replace(':.1f', '');
  }
  const lines = template.solution.split('\n');
  return lines.length > 1 ? lines.slice(0, -1).join('\n') : template.solution;
}

/**
 * 请求 boss / endless / arena 题目。
 *
 * 第 2 参数可以是 `number`（兼容旧调用，仅 stage）或 `BossProblemRequest`
 * （含 stage + preferredKc + preferredKind + profileSignals）。后者让 PracticeAgent
 * 真正基于学生画像偏好出题，不再随机抽 KC。
 *
 * - 优先调真后端 /api/world/boss/{chapter}/problem
 * - 只接收后端审核通过的题目；AI 生成或后端课程题均可
 * - 任意 4xx/5xx/网络异常默认向上抛出；只有隔离开发测试显式开启时才用本地模板
 */
export async function requestBossProblem(
  chapter: number,
  arg: number | BossProblemRequest,
  options: { allowGoldenFallback?: boolean } = {},
): Promise<BossProblemDto> {
  const req: BossProblemRequest = typeof arg === 'number' ? { stage: arg } : arg;
  const allowGoldenFallback = options.allowGoldenFallback === true;
  try {
    const response = await http.post<BossProblemDto>(
      `/api/world/boss/${chapter}/problem`,
      req,
      {
        suppressNetworkToast: true,
        timeout: BOSS_PROBLEM_TIMEOUT_MS,
      },
    );
    if (response.data && typeof response.data.problemId === 'string') {
      if (!response.data.reviewed) {
        throw new Error('生成题未通过审核，未进入远征');
      }
      return normalizeProblemForKind(response.data, req.preferredKind);
    }
    throw new Error('出题服务返回了不完整的题目');
  } catch (error) {
    if (!allowGoldenFallback) {
      throw error instanceof Error ? error : new Error(String(error));
    }
    if (typeof console !== 'undefined') {
      const status = error instanceof ApiClientError ? error.status ?? 0 : 0;
      const msg = error instanceof Error ? error.message : String(error);
      console.warn(`[boss] backend status=${status} ${msg} — falling back to golden`);
    }
  }
  return buildGoldenProblem(chapter, req.stage, req.preferredKc, req.preferredKind);
}

function normalizeProblemForKind(problem: BossProblemDto, preferredKind?: string): BossProblemDto {
  if (preferredKind !== 'debug' && preferredKind !== 'bug_mine' && preferredKind !== 'closed_loop') {
    return problem;
  }
  if ((problem.buggyCode?.trim() || problem.starterCode?.trim())) {
    const code = problem.buggyCode?.trim() ? problem.buggyCode : problem.starterCode;
    return { ...problem, buggyCode: code, starterCode: problem.starterCode?.trim() ? problem.starterCode : code };
  }
  const synthesized = extractCodeBlock(problem.statement) ?? synthesizeBuggyCode(problem);
  return {
    ...problem,
    buggyCode: synthesized,
    starterCode: synthesized,
  };
}

function extractCodeBlock(markdown: string): string | null {
  const fenced = /```(?:python|py)?\s*\n([\s\S]*?)```/i.exec(markdown);
  if (fenced?.[1]?.trim()) return `${fenced[1].trimEnd()}\n`;
  return null;
}

function synthesizeBuggyCode(problem: BossProblemDto): string {
  const sampleParts = problem.sampleInput.trim().split(/\s+/).filter(Boolean);
  if (sampleParts.length >= 2 && sampleParts.slice(0, 2).every((p) => /^-?\d+(\.\d+)?$/.test(p))) {
    return 'a, b = input().split()\nprint(a + b)\n';
  }
  if (problem.sampleInput.trim()) {
    return 'value = input\nprint(value)\n';
  }
  return 'print(result)\n';
}

/**
 * 提交 boss 答案（正式判题，唯一真源是后端沙箱）。
 *
 * **绝不返回伪造结果**：判题不可达时抛出，由调用方决定「本次不计分 / 不入历史 / 可重试」。
 * 早期这里在 catch 里返回 `{passed:false, feedback:'后端未连接 / mock 评测…'}`，
 * 使得网络故障被当成学生真答错——扣体力、清连胜、错题入库，还会污染远征成绩。
 * 补星由后端按正式判题记录计算。调用方（useEndlessRun / rogueRun）已备好失败分支，这里必须 throw。
 */
export async function submitBossAttempt(
  payload: BossAttemptRequest,
): Promise<BossAttemptResultDto> {
  const attemptId = attemptIdFor(payload);
  let response: Awaited<ReturnType<typeof http.post<BossAttemptResultDto>>>;
  try {
    response = await http.post<BossAttemptResultDto>(
      `/api/world/boss/${payload.chapter}/attempt`,
      { ...payload, attemptId },
    );
  } catch (error) {
    if (typeof console !== 'undefined') {
      const msg = error instanceof Error ? error.message : String(error);
      console.warn('[boss] backend submit failed:', msg);
    }
    throw error instanceof Error ? error : new Error(String(error));
  }
  if (!response.data || typeof response.data.problemId !== 'string') {
    throw new Error('判题服务返回了不完整的结果');
  }
  if (pendingAttempt?.attemptId === attemptId) pendingAttempt = null;
  return response.data;
}

/**
 * 触发后台「预生成下一题」：POST /api/world/boss/{chapter}/prefetch。
 *
 * 后端先持久化 PENDING 并返回 job id；生成成功才转 READY，失败转 FAILED。相同维度再次 POST
 * 会重试 FAILED 任务。调用方必须处理 reject，避免把网络失败误报为就绪。
 */
export async function prefetchBossProblem(
  chapter: number,
  req: BossProblemRequest,
): Promise<BossPrefetchStatusDto> {
  const response = await http.post<BossPrefetchStatusDto>(
    `/api/world/boss/${chapter}/prefetch`,
    req,
    { suppressNetworkToast: true },
  );
  if (!response.data || !['PENDING', 'READY', 'FAILED'].includes(response.data.status)) {
    throw new Error('预加载服务返回了无效状态');
  }
  return response.data;
}

/** Query the persisted state for exactly the same KC/kind/level/difficulty dimensions. */
export async function fetchBossPrefetchStatus(
  chapter: number,
  req: Pick<
    BossProblemRequest,
    'stage' | 'preferredKc' | 'preferredKind' | 'arenaLevel' | 'difficulty' | 'expeditionRunId'
  >,
): Promise<BossPrefetchStatusDto> {
  if (req.difficulty == null) throw new Error('查询预加载状态必须提供 difficulty');
  const response = await http.get<BossPrefetchStatusDto>(
    `/api/world/boss/${chapter}/prefetch`,
    {
      params: {
        stage: req.stage,
        preferredKc: req.preferredKc,
        preferredKind: req.preferredKind,
        arenaLevel: req.arenaLevel,
        difficulty: req.difficulty,
        expeditionRunId: req.expeditionRunId,
      },
      suppressNetworkToast: true,
    },
  );
  if (!response.data || !['IDLE', 'PENDING', 'READY', 'FAILED'].includes(response.data.status)) {
    throw new Error('预加载状态查询返回了无效结果');
  }
  return response.data;
}
