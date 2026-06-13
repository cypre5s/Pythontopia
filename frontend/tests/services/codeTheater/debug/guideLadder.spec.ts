import { describe, expect, it } from 'vitest';

import { buildGuideLadder, redactSolutionLeak } from '@/services/codeTheater/debug';
import type {
  DebugHintLayerInput,
  DiagnosticFocus,
  DiagnosticFocusKind,
} from '@/services/codeTheater/debug';

function makeFocus(kind: DiagnosticFocusKind, line = 7): DiagnosticFocus {
  return {
    found: kind !== 'none',
    kind,
    step: 3,
    line,
    variables: { i: '0', total: '0' },
    phenomenon: '现象描述',
  };
}

describe('redactSolutionLeak · 不泄答红线守卫', () => {
  it('连续 3 行 Python 关键字代码 → 判定泄漏并脱敏', () => {
    const leak = ['for i in range(n):', '    total += i', 'print(total)'].join('\n');
    const result = redactSolutionLeak(leak);
    expect(result.redacted).toBe(true);
    expect(result.text).not.toContain('range(n)');
    expect(result.text).toContain('脱敏');
  });

  it('多行 fenced 代码块 → 判定泄漏并脱敏', () => {
    const leak = '思路如下：\n```python\nx = int(input())\nprint(x * 2)\n```';
    const result = redactSolutionLeak(leak);
    expect(result.redacted).toBe(true);
    expect(result.text).not.toContain('input()');
  });

  it('纯元认知引导文本 → 不脱敏，原样返回', () => {
    const safe = '先想想：如果输入是 5，你的程序应该按什么步骤产生输出？';
    const result = redactSolutionLeak(safe);
    expect(result.redacted).toBe(false);
    expect(result.text).toBe(safe);
  });

  it('仅 1~2 行 code-ish 不触发（避免误伤指方向提示）', () => {
    const borderline = '重点检查第 2 行附近：\nreturn 的位置可能写早了';
    const result = redactSolutionLeak(borderline);
    expect(result.redacted).toBe(false);
  });
});

describe('buildGuideLadder · 回退脚手架（无 DebugAgent 提示）', () => {
  it('产出三阶 换述→拆解→指方向，全部 fromAgent=false 且未脱敏', () => {
    const steps = buildGuideLadder({ focus: makeFocus('exception') });
    expect(steps).toHaveLength(3);
    expect(steps.map((step) => step.stage)).toEqual(['rephrase', 'decompose', 'point']);
    expect(steps.map((step) => step.order)).toEqual([1, 2, 3]);
    expect(steps.every((step) => step.fromAgent === false)).toBe(true);
    expect(steps.every((step) => step.redacted === false)).toBe(true);
    // 引用焦点行，但绝不含可运行解法
    expect(steps[0]?.body).toContain('第 7 行');
    expect(steps.every((step) => !step.body.includes('def '))).toBe(true);
  });

  it('指方向口吻随焦点类别变化', () => {
    const loop = buildGuideLadder({ focus: makeFocus('infinite_loop') });
    expect(loop[2]?.body).toContain('结束条件');
    const out = buildGuideLadder({ focus: makeFocus('output_divergence') });
    expect(out[2]?.body).toContain('格式');
  });
});

describe('buildGuideLadder · 消费 DebugAgent 三层提示', () => {
  const cleanLayers: DebugHintLayerInput[] = [
    { layer: 1, headline: '先想清楚一个问题', body: '给定输入应按什么步骤产生输出？' },
    { layer: 2, headline: '检查关键代码段', body: '重点检查第 2 行附近的循环边界。' },
    { layer: 3, headline: '改造方向', body: '先读取并校验输入，再按题面顺序计算。' },
  ];

  it('按 layer 映射到 stage，fromAgent=true', () => {
    const steps = buildGuideLadder({ focus: makeFocus('exception'), hintLayers: cleanLayers });
    expect(steps).toHaveLength(3);
    expect(steps[0]).toMatchObject({ stage: 'rephrase', headline: '先想清楚一个问题', fromAgent: true });
    expect(steps[1]).toMatchObject({ stage: 'decompose', fromAgent: true });
    expect(steps[2]).toMatchObject({ stage: 'point', fromAgent: true });
    expect(steps.every((step) => step.redacted === false)).toBe(true);
  });

  it('某层提示疑似泄漏完整代码 → 该层脱敏，不外泄答案', () => {
    const leakyLayers: DebugHintLayerInput[] = [
      cleanLayers[0]!,
      cleanLayers[1]!,
      {
        layer: 3,
        headline: '改造方向',
        body: ['n = int(input())', 'for i in range(n):', '    print(i)'].join('\n'),
      },
    ];
    const steps = buildGuideLadder({ focus: makeFocus('exception'), hintLayers: leakyLayers });
    expect(steps[2]?.redacted).toBe(true);
    expect(steps[2]?.body).not.toContain('range(n)');
    expect(steps[2]?.body).toContain('脱敏');
  });

  it('缺失的层用脚手架补齐（只给 layer 1）', () => {
    const steps = buildGuideLadder({
      focus: makeFocus('last_step'),
      hintLayers: [{ layer: 1, headline: 'A', body: 'B' }],
    });
    expect(steps).toHaveLength(3);
    expect(steps[0]?.fromAgent).toBe(true);
    expect(steps[1]?.fromAgent).toBe(false);
    expect(steps[2]?.fromAgent).toBe(false);
  });

  it('重复层去重，保留首个', () => {
    const steps = buildGuideLadder({
      focus: makeFocus('exception'),
      hintLayers: [
        { layer: 1, headline: '第一个', body: 'x' },
        { layer: 1, headline: '重复', body: 'y' },
        { layer: 2, headline: '二', body: 'z' },
        { layer: 3, headline: '三', body: 'w' },
      ],
    });
    expect(steps[0]?.headline).toBe('第一个');
  });
});
