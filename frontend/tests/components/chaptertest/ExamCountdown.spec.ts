// 倒计时组件单测：以 deadlineAt 为锚显示剩余时间；到点 emit('expire') 恰一次；active=false 不计时。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import ExamCountdown from '@/components/chaptertest/ExamCountdown.vue';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('ExamCountdown', () => {
  it('renders remaining mm:ss from deadlineAt', () => {
    const deadlineAt = new Date(Date.now() + 90_000).toISOString();
    const wrapper = mount(ExamCountdown, { props: { deadlineAt, active: true } });
    expect(wrapper.find('[data-testid="exam-countdown"]').text()).toContain('01:30');
  });

  it('emits expire exactly once when the deadline passes', async () => {
    const deadlineAt = new Date(Date.now() + 2_000).toISOString();
    const wrapper = mount(ExamCountdown, { props: { deadlineAt, active: true } });
    expect(wrapper.emitted('expire')).toBeUndefined();
    await vi.advanceTimersByTimeAsync(3_000);
    expect(wrapper.emitted('expire')).toHaveLength(1);
    // 继续推进不再重复触发。
    await vi.advanceTimersByTimeAsync(3_000);
    expect(wrapper.emitted('expire')).toHaveLength(1);
  });

  it('does not tick / expire while inactive', async () => {
    const deadlineAt = new Date(Date.now() + 1_000).toISOString();
    const wrapper = mount(ExamCountdown, { props: { deadlineAt, active: false } });
    await vi.advanceTimersByTimeAsync(5_000);
    expect(wrapper.emitted('expire')).toBeUndefined();
  });
});
