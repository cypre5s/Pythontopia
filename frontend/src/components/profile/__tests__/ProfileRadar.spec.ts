import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import type { ProfileDimensionDto } from '@/api/learner';
import ProfileRadar from '../ProfileRadar.vue';

function dimensions(assessedCount: number): ProfileDimensionDto[] {
  return Array.from({ length: 8 }, (_, index) => ({
    key: `dimension_${index}`,
    label: `维度 ${index}`,
    score: 0.7,
    confidence: index < assessedCount ? 0.5 : 0.1,
    evidenceCount: index < assessedCount ? 4 : 0,
    selfEditable: false,
    tags: [],
  }));
}

describe('ProfileRadar', () => {
  it('shows only assessed dimensions and avoids drawing a misleading partial area', () => {
    const wrapper = mount(ProfileRadar, { props: { dimensions: dimensions(1) } });

    expect(wrapper.findAll('.radar__dot')).toHaveLength(1);
    expect(wrapper.findAll('.radar__area')).toHaveLength(0);
    expect(wrapper.text()).toContain('尚未评估');
  });

  it('draws the area only after every displayed dimension has enough evidence', () => {
    const wrapper = mount(ProfileRadar, { props: { dimensions: dimensions(8) } });

    expect(wrapper.findAll('.radar__dot')).toHaveLength(8);
    expect(wrapper.findAll('.radar__area')).toHaveLength(1);
  });
});
