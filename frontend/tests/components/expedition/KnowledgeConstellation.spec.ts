import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import KnowledgeConstellation from '@/components/expedition/KnowledgeConstellation.vue';

describe('KnowledgeConstellation', () => {
  it('renders all 65 KC stars and counts the lit ones (mastery >= 0.6)', () => {
    const mastery = {
      'PY43-KC-001': 0.8,
      'PY43-KC-002': 0.7,
      'PY43-KC-003': 0.3, // 学习中，不计点亮
    };
    const wrapper = mount(KnowledgeConstellation, { props: { mastery } });
    expect(wrapper.findAll('circle.kc-star')).toHaveLength(65);
    expect(wrapper.get('.kc-constellation__cap strong').text()).toBe('2');
  });

  it('defaults to zero lit when no mastery is provided', () => {
    const wrapper = mount(KnowledgeConstellation);
    expect(wrapper.get('.kc-constellation__cap strong').text()).toBe('0');
  });
});
