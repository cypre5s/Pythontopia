import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ExpeditionLeaderboard from '@/components/expedition/ExpeditionLeaderboard.vue';

describe('ExpeditionLeaderboard', () => {
  it('labels verified answers without claiming server-verified map depth', () => {
    const wrapper = mount(ExpeditionLeaderboard, {
      props: {
        board: {
          entries: [
            { rank: 1, userId: 7, displayName: '小明', bestScore: 120, bestDepth: 3, self: true },
          ],
          selfRank: 1,
          selfBest: 120,
          inClass: true,
        },
      },
    });

    expect(wrapper.text()).toContain('通过题数 3');
    expect(wrapper.text()).not.toContain('深度 3');
  });

  it('describes an empty board as having no verified score yet', () => {
    const wrapper = mount(ExpeditionLeaderboard, {
      props: { board: { entries: [], selfRank: null, selfBest: 0, inClass: true } },
    });

    expect(wrapper.text()).toContain('本班还没有远征核验成绩');
    expect(wrapper.text()).not.toContain('完成远征');
  });
});
