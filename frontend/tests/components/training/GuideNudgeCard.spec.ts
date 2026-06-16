import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import GuideNudgeCard from '@/components/training/GuideNudgeCard.vue';

describe('GuideNudgeCard', () => {
  it('renders AC nudge with review and transfer choice', () => {
    const wrapper = mount(GuideNudgeCard, { props: { verdict: 'AC' } });

    expect(wrapper.text()).toContain('这题过了');
    expect(wrapper.find('[data-testid="guide-nudge-ac-review"]').exists()).toBe(true);
  });

  it('renders WA nudge and waits for user click', () => {
    const wrapper = mount(GuideNudgeCard, { props: { verdict: 'WA' } });

    expect(wrapper.text()).toContain('这次没过');
    expect(wrapper.emitted('continue')).toBeUndefined();
  });

  it('emits AC_REVIEW for AC and ERROR_FEEDBACK for non-AC verdicts', async () => {
    const ac = mount(GuideNudgeCard, { props: { verdict: 'AC' } });
    await ac.get('[data-testid="guide-nudge-ac-review"]').trigger('click');
    expect(ac.emitted('continue')?.[0]).toEqual(['AC_REVIEW']);

    const wa = mount(GuideNudgeCard, { props: { verdict: 'WA' } });
    await wa.get('[data-testid="guide-nudge-error-analysis"]').trigger('click');
    expect(wa.emitted('continue')?.[0]).toEqual(['ERROR_FEEDBACK']);
  });
});
