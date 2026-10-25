import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SubmissionDetailDto } from '@/api/dto/Submission';
import SubmissionResultModal from '../SubmissionResultModal.vue';

const { getSubmissionPerformance } = vi.hoisted(() => ({ getSubmissionPerformance: vi.fn() }));

vi.mock('@/api/submissions', () => ({ getSubmissionPerformance }));

function detail(verdict: 'AC' | 'WA' = 'AC'): SubmissionDetailDto {
  return {
    submission: {
      id: 42,
      userId: 7,
      problemId: 476,
      questId: null,
      source: 'TRAINING',
      language: 'PYTHON_3_11',
      code: 'print(1)',
      submittedAt: '2026-10-25T10:00:00Z',
      status: verdict === 'AC' ? 'PASSED' : 'FAILED',
      runtimeMs: 12,
      memoryKb: 128,
    },
    judgeResult: {
      id: 3,
      submissionId: 42,
      verdict,
      passedCases: verdict === 'AC' ? 3 : 1,
      totalCases: 3,
      failureReason: verdict === 'WA' ? '第 2 个隐藏用例输出不匹配' : null,
      trace: {},
      createdAt: '2026-10-25T10:00:01Z',
    },
  };
}

const mounted: ReturnType<typeof mount>[] = [];

function render(verdict: 'AC' | 'WA' = 'AC') {
  const wrapper = mount(SubmissionResultModal, {
    attachTo: document.body,
    props: { open: true, detail: detail(verdict), canOpenBugMine: true },
  });
  mounted.push(wrapper);
  return wrapper;
}

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount());
  vi.clearAllMocks();
  document.body.style.overflow = '';
});

describe('SubmissionResultModal', () => {
  it('shows runtime and memory Beats when each metric has enough AC submissions', async () => {
    getSubmissionPerformance.mockResolvedValue({
      runtimeBeatsPercent: 80,
      runtimeSampleCount: 12,
      memoryBeatsPercent: 50,
      memorySampleCount: 10,
    });

    render();
    await flushPromises();
    const renderedText = document.body.textContent ?? '';

    expect(renderedText).toContain('答案通过');
    expect(renderedText).toContain('12 ms');
    expect(renderedText).toContain('128 KB');
    expect(renderedText).toContain('快于 80%');
    expect(renderedText).toContain('少于 50%');
    expect(renderedText).toContain('12 条 AC 提交');
  });

  it('keeps absolute measurements and hides rank copy below the threshold', async () => {
    getSubmissionPerformance.mockResolvedValue({
      runtimeBeatsPercent: null,
      runtimeSampleCount: null,
      memoryBeatsPercent: null,
      memorySampleCount: null,
    });

    render();
    await flushPromises();
    const renderedText = document.body.textContent ?? '';

    expect(renderedText).toContain('12 ms');
    expect(renderedText).toContain('128 KB');
    expect(renderedText).not.toContain('Beats');
    expect(renderedText).not.toContain('样本');
  });

  it('shows safe failure feedback and offers the existing Bug Mine action', () => {
    render('WA');

    expect(document.body.textContent).toContain('答案错误');
    expect(document.body.textContent).toContain('第 2 个隐藏用例输出不匹配');
    expect(document.body.querySelector('[data-testid="submission-open-bug-mine"]')).not.toBeNull();
    expect(document.body.textContent).not.toContain('期望输出');
  });

  it('closes on Escape and backdrop while keeping an explicit close button', async () => {
    getSubmissionPerformance.mockResolvedValue({
      runtimeBeatsPercent: null,
      runtimeSampleCount: null,
      memoryBeatsPercent: null,
      memorySampleCount: null,
    });
    const wrapper = render();

    expect(document.body.querySelector('[data-testid="submission-result-close"]')).not.toBeNull();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('close')).toHaveLength(1);

    document.body
      .querySelector('[data-testid="submission-result-backdrop"]')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(wrapper.emitted('close')).toHaveLength(2);
  });

  it('restores page scrolling when the open modal switches to another submission', async () => {
    getSubmissionPerformance.mockResolvedValue({
      runtimeBeatsPercent: null,
      runtimeSampleCount: null,
      memoryBeatsPercent: null,
      memorySampleCount: null,
    });
    const wrapper = render();
    const nextDetail = detail();
    nextDetail.submission.id = 43;
    await flushPromises();
    await wrapper.setProps({ detail: nextDetail });
    await flushPromises();

    await wrapper.setProps({ open: false });

    expect(document.body.style.overflow).toBe('');
  });

  it('keeps keyboard focus inside the modal', async () => {
    const wrapper = render('WA');
    await flushPromises();
    const closeButton = document.body.querySelector<HTMLButtonElement>(
      '[data-testid="submission-result-close"]',
    );
    const doneButton = document.body.querySelector<HTMLButtonElement>(
      '[data-testid="submission-result-done"]',
    );
    expect(closeButton).not.toBeNull();
    expect(doneButton).not.toBeNull();

    closeButton?.focus();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true }));
    expect(document.activeElement).toBe(doneButton);

    doneButton?.focus();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
    expect(document.activeElement).toBe(closeButton);
    wrapper.unmount();
  });
});
