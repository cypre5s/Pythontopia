// Boss 副本前端 API。
//
// 优先调真后端 `/api/world/boss/...`；后端 404 / 网络失败时降级使用本地 mock 题
// （saves dev/演示场景，避免阻塞 UI）。
//
// 真后端契约：
//   POST /api/world/boss/{chapter}/problem  body: { stage }  → BossProblemDto
//   POST /api/world/boss/{chapter}/attempt  body: BossAttemptRequest  → BossAttemptResultDto

import { http, ApiClientError } from './http';
import type {
  BossAttemptRequest,
  BossAttemptResultDto,
  BossProblemDto,
  BossProblemRequest,
} from './dto/Boss';
import { findBossByChapter } from '@/data/bossCatalog';

const BOSS_PROBLEM_TIMEOUT_MS = 180_000;

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
 * - 任意 4xx/5xx/网络异常 → 退化到本地 golden 题（不阻塞玩家流程）
 * - golden 兜底支持 preferredKc：若给定 KC 命中，从该 KC 对应的 template 抽题；
 *   否则按 stage 取默认 template（保留旧行为）。
 */
export async function requestBossProblem(
  chapter: number,
  arg: number | BossProblemRequest,
): Promise<BossProblemDto> {
  const req: BossProblemRequest = typeof arg === 'number' ? { stage: arg } : arg;
  try {
    const response = await http.post<BossProblemDto>(
      `/api/world/boss/${chapter}/problem`,
      req,
      {
        timeout: BOSS_PROBLEM_TIMEOUT_MS,
      },
    );
    if (response.data && typeof response.data.problemId === 'string') {
      return normalizeProblemForKind(response.data, req.preferredKind);
    }
  } catch (error) {
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
 * 提交 boss 答案。
 * - 优先调真后端 /api/world/boss/{chapter}/attempt
 * - 真后端失败时本地用 Pyodide 评测（已在 useBossSession 中跑），这里只返回兜底空结果
 */
export async function submitBossAttempt(
  payload: BossAttemptRequest,
): Promise<BossAttemptResultDto> {
  try {
    const response = await http.post<BossAttemptResultDto>(
      `/api/world/boss/${payload.chapter}/attempt`,
      payload,
    );
    if (response.data && typeof response.data.problemId === 'string') {
      return response.data;
    }
  } catch (error) {
    if (typeof console !== 'undefined') {
      const msg = error instanceof Error ? error.message : String(error);
      console.warn('[boss] backend submit failed, using local mock result:', msg);
    }
  }
  return {
    problemId: payload.problemId,
    passed: false,
    passedTestCases: 0,
    totalTestCases: 3,
    feedback: '后端未连接 / mock 评测：请用浏览器内 Pyodide 验证后再实际提交。',
    score: 0,
  };
}

/**
 * 触发后台「预生成下一题」：POST /api/world/boss/{chapter}/prefetch。
 *
 * best-effort fire-and-forget——后端异步生成并落库 (status=READY)，本调用立即 resolve；任何失败
 * （4xx/5xx/网络）只 warn 不抛、不弹全局 toast（suppressNetworkToast），因为 nextProblem 会自然回退
 * 现场出题，预取仅为加速、非必需。
 */
export async function prefetchBossProblem(
  chapter: number,
  req: BossProblemRequest,
): Promise<void> {
  try {
    await http.post(`/api/world/boss/${chapter}/prefetch`, req, {
      suppressNetworkToast: true,
    });
  } catch (error) {
    if (typeof console !== 'undefined') {
      const msg = error instanceof Error ? error.message : String(error);
      console.warn('[boss] prefetch failed (ignored):', msg);
    }
  }
}
