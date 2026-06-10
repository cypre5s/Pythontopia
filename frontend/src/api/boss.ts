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
import { findBossByChapter } from '@/data/mainQuests';

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
  return {
    problemId: `golden-c${chapter}-s${stage}${preferredKc ? `-${preferredKc}` : ''}`,
    chapter,
    stage,
    difficulty: Math.min(5, stage),
    kcCode: template.kcCode,
    statement: `${template.title}\n\n${template.statement}`,
    hints: [],
    sampleInput: template.sampleInput,
    sampleOutput: template.sampleOutput,
    totalTestCases: 3,
    reviewed: false,
    aiGenerated: false,
  };
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
    );
    if (response.data && typeof response.data.problemId === 'string') {
      return response.data;
    }
  } catch (error) {
    if (typeof console !== 'undefined') {
      const status = error instanceof ApiClientError ? error.status ?? 0 : 0;
      const msg = error instanceof Error ? error.message : String(error);
      console.warn(`[boss] backend status=${status} ${msg} — falling back to golden`);
    }
  }
  return buildGoldenProblem(chapter, req.stage, req.preferredKc);
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
