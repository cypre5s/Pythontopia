import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import StarBadge from '@/components/mainline/StarBadge.vue';

describe('StarBadge', () => {
  it('renders filled/empty stars and an accessible label', () => {
    const wrapper = mount(StarBadge, { props: { stars: 2 } });
    const badge = wrapper.get('[data-testid="mainline-star-badge"]');
    expect(badge.attributes('aria-label')).toBe('2 / 3 星');
    expect(badge.attributes('data-filled')).toBe('2');
    expect(wrapper.findAll('.star-badge__star')).toHaveLength(3);
    expect(wrapper.findAll('.star-badge__star--on')).toHaveLength(2);
    expect(wrapper.findAll('.star-badge__star--off')).toHaveLength(1);
  });

  it('clamps out-of-range stars to 0..max', () => {
    const over = mount(StarBadge, { props: { stars: 9 } });
    expect(over.get('[data-testid="mainline-star-badge"]').attributes('data-filled')).toBe('3');
    const under = mount(StarBadge, { props: { stars: -1 } });
    expect(under.get('[data-testid="mainline-star-badge"]').attributes('data-filled')).toBe('0');
  });

  it('honours a custom max', () => {
    const wrapper = mount(StarBadge, { props: { stars: 4, max: 5 } });
    expect(wrapper.findAll('.star-badge__star')).toHaveLength(5);
    expect(wrapper.get('[data-testid="mainline-star-badge"]').attributes('aria-label')).toBe('4 / 5 星');
  });
});
