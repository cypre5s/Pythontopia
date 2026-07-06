/**
 * JudgeResultPanel 测试 —— 共享判题结果展示面板（纯展示）。
 *
 * 验证 pass/fail 横幅、通过用例计数、firstHint 渲染，以及 NFR-USE-003 的
 * role="status" + aria-live="polite" 常驻播报容器。无 any。
 */
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import JudgeResultPanel from '@/components/challenge/JudgeResultPanel.vue';
import type { ChallengeResult } from '@/api/dto/CodeChallenge';

function passResult(overrides: Partial<ChallengeResult> = {}): ChallengeResult {
  return {
    passed: true,
    passedCases: 5,
    totalCases: 5,
    verdict: 'AC',
    feedback: '全部通过，干得漂亮',
    ...overrides,
  };
}

describe('JudgeResultPanel', () => {
  it('always renders an aria-live status container so verdicts are announced', () => {
    const wrapper = mount(JudgeResultPanel, { props: { result: null, mode: 'judge' } });
    const panel = wrapper.get('[data-testid="judge-result-panel"]');

    expect(panel.attributes('role')).toBe('status');
    expect(panel.attributes('aria-live')).toBe('polite');
    // 无结果时容器仍在 DOM，只是显示占位（保证读屏稍后能播报新内容）。
    expect(wrapper.find('[data-testid="judge-result-empty"]').exists()).toBe(true);
  });

  it('renders a pass banner with verdict and case counts', () => {
    const wrapper = mount(JudgeResultPanel, { props: { result: passResult(), mode: 'judge' } });

    expect(wrapper.get('[data-testid="judge-result-banner"]').text()).toContain('通过');
    expect(wrapper.get('[data-testid="judge-result-verdict"]').text()).toBe('AC');
    expect(wrapper.get('[data-testid="judge-result-cases"]').text()).toContain('5/5');
    expect(wrapper.get('[data-testid="judge-result-panel"]').classes()).toContain('judge-result--success');
  });

  it('renders a fail banner for an unpassed result', () => {
    const wrapper = mount(JudgeResultPanel, {
      props: {
        result: passResult({ passed: false, passedCases: 2, totalCases: 5, verdict: 'WA' }),
        mode: 'judge',
      },
    });

    expect(wrapper.get('[data-testid="judge-result-banner"]').text()).toContain('未通过');
    expect(wrapper.get('[data-testid="judge-result-cases"]').text()).toContain('2/5');
    expect(wrapper.get('[data-testid="judge-result-panel"]').classes()).toContain('judge-result--danger');
  });

  it('renders firstHint only when present', () => {
    const withHint = mount(JudgeResultPanel, {
      props: { result: passResult({ passed: false, verdict: 'WA', firstHint: '检查越界' }), mode: 'judge' },
    });
    expect(withHint.find('[data-testid="judge-result-hint"]').exists()).toBe(true);
    expect(withHint.get('[data-testid="judge-result-hint"]').text()).toContain('检查越界');

    const withoutHint = mount(JudgeResultPanel, { props: { result: passResult(), mode: 'judge' } });
    expect(withoutHint.find('[data-testid="judge-result-hint"]').exists()).toBe(false);
  });

  it('shows the preview-mode label when mode is preview', () => {
    const wrapper = mount(JudgeResultPanel, { props: { result: passResult(), mode: 'preview' } });
    expect(wrapper.text()).toContain('本地预览');
  });
});
