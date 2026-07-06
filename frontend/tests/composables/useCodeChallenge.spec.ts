/**
 * useCodeChallenge 测试 —— 共享代码挑战状态机（Wave 1 基座）。
 *
 * mock 共享 Pyodide 单例，验证：
 *   - runPreview 对首个样例 trim 比对 stdout（非权威）；
 *   - submit 委托注入的 judge，phase/result 随权威结果流转；
 *   - 本地预览通过但后端 judge 返回 failed 时，一律以 judge 为准；
 *   - reset 清空状态。
 * 全程无 any。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import type { ChallengeResult, CodeChallenge } from '@/api/dto/CodeChallenge';
import type { PyodideRunResult } from '@/game/adapters/pyodide';

const mockRun = vi.fn<(code: string, stdin?: string) => Promise<PyodideRunResult>>();

vi.mock('@/game/adapters/pyodide', () => ({
  getPyodideRunner: () => ({
    run: mockRun,
    init: vi.fn(async () => undefined),
    dispose: vi.fn(),
  }),
}));

import { useCodeChallenge } from '@/composables/useCodeChallenge';

function makeChallenge(overrides: Partial<CodeChallenge> = {}): CodeChallenge {
  return {
    id: 'c1',
    kind: 'free',
    kcCodes: ['KC-PY-001'],
    statement: '打印 42',
    samples: [{ input: '', output: '42' }],
    difficulty: 1,
    mode: 'judge',
    ...overrides,
  };
}

function pyodideResult(overrides: Partial<PyodideRunResult> = {}): PyodideRunResult {
  return { stdout: '', stderr: '', durationMs: 1, isError: false, ...overrides };
}

function acResult(overrides: Partial<ChallengeResult> = {}): ChallengeResult {
  return {
    passed: true,
    passedCases: 3,
    totalCases: 3,
    verdict: 'AC',
    feedback: '全部通过',
    ...overrides,
  };
}

describe('useCodeChallenge', () => {
  beforeEach(() => {
    mockRun.mockReset();
    mockRun.mockResolvedValue(pyodideResult());
  });

  it('initialises code from starterCode and starts idle', () => {
    const c = useCodeChallenge({
      challenge: makeChallenge({ starterCode: 'print(0)' }),
      judge: async () => acResult(),
    });
    expect(c.code.value).toBe('print(0)');
    expect(c.phase.value).toBe('idle');
    expect(c.result.value).toBeNull();
    expect(c.preview.value).toBeNull();
  });

  it('runPreview trims and compares stdout against the first sample', async () => {
    mockRun.mockResolvedValue(pyodideResult({ stdout: '  42 \n' }));
    const c = useCodeChallenge({
      challenge: makeChallenge({ samples: [{ input: '3\n', output: '42' }] }),
      judge: async () => acResult(),
    });
    c.code.value = 'print(42)';

    const preview = await c.runPreview();

    expect(mockRun).toHaveBeenCalledWith('print(42)', '3\n');
    expect(preview.matched).toBe(true);
    expect(c.preview.value?.matched).toBe(true);
    // 预览非权威：不得置 passed/failed。
    expect(c.phase.value).toBe('idle');
    expect(c.result.value).toBeNull();
  });

  it('runPreview reports mismatch when stdout differs', async () => {
    mockRun.mockResolvedValue(pyodideResult({ stdout: '7' }));
    const c = useCodeChallenge({
      challenge: makeChallenge({ samples: [{ input: '', output: '42' }] }),
      judge: async () => acResult(),
    });

    const preview = await c.runPreview();

    expect(preview.matched).toBe(false);
    expect(c.phase.value).toBe('idle');
  });

  it('submit delegates to the injected judge and flips phase to passed', async () => {
    const judge = vi.fn(async (_code: string): Promise<ChallengeResult> => acResult());
    const c = useCodeChallenge({ challenge: makeChallenge(), judge });
    c.code.value = 'print(42)';

    const res = await c.submit();

    expect(judge).toHaveBeenCalledWith('print(42)');
    expect(res?.verdict).toBe('AC');
    expect(c.phase.value).toBe('passed');
    expect(c.result.value?.passed).toBe(true);
  });

  it('reflects a backend "failed" verdict even after a passing preview', async () => {
    mockRun.mockResolvedValue(pyodideResult({ stdout: '42' }));
    const judge = vi.fn(
      async (): Promise<ChallengeResult> =>
        acResult({ passed: false, passedCases: 1, totalCases: 3, verdict: 'WA', feedback: '隐藏用例未过', firstHint: '注意边界' }),
    );
    const c = useCodeChallenge({
      challenge: makeChallenge({ samples: [{ input: '', output: '42' }] }),
      judge,
    });
    c.code.value = 'print(42)';

    const preview = await c.runPreview();
    expect(preview.matched).toBe(true); // 本地预览通过

    const res = await c.submit();

    // 后端判题权威：phase/result 一律以 judge 为准。
    expect(res?.passed).toBe(false);
    expect(c.phase.value).toBe('failed');
    expect(c.result.value?.verdict).toBe('WA');
    expect(c.result.value?.firstHint).toBe('注意边界');
  });

  it('invokes onResult with the authoritative judge result', async () => {
    const onResult = vi.fn();
    const c = useCodeChallenge({ challenge: makeChallenge(), judge: async () => acResult(), onResult });

    await c.submit();

    expect(onResult).toHaveBeenCalledTimes(1);
    expect(onResult.mock.calls[0]?.[0]).toMatchObject({ passed: true, verdict: 'AC' });
  });

  it('surfaces a judge rejection as the error phase', async () => {
    const c = useCodeChallenge({
      challenge: makeChallenge(),
      judge: async () => {
        throw new Error('后端 500');
      },
    });

    const res = await c.submit();

    expect(res).toBeNull();
    expect(c.phase.value).toBe('error');
    expect(c.error.value).toBe('后端 500');
  });

  it('reset clears result, preview, error and returns to idle', async () => {
    mockRun.mockResolvedValue(pyodideResult({ stdout: '42' }));
    const c = useCodeChallenge({
      challenge: makeChallenge({ starterCode: 'pass', samples: [{ input: '', output: '42' }] }),
      judge: async () => acResult({ passed: false, verdict: 'WA' }),
    });
    c.code.value = 'print(1)';
    await c.runPreview();
    await c.submit();
    expect(c.result.value).not.toBeNull();
    expect(c.preview.value).not.toBeNull();

    c.reset();

    expect(c.phase.value).toBe('idle');
    expect(c.result.value).toBeNull();
    expect(c.preview.value).toBeNull();
    expect(c.error.value).toBeNull();
    expect(c.code.value).toBe('pass');
  });

  it('accepts a reactive challenge ref', async () => {
    const challenge = ref<CodeChallenge>(makeChallenge({ id: 'reactive', starterCode: 'x' }));
    const c = useCodeChallenge({ challenge, judge: async () => acResult() });
    expect(c.challenge.value.id).toBe('reactive');
    expect(c.code.value).toBe('x');
  });
});
