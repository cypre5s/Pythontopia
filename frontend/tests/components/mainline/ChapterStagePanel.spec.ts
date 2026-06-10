import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ChapterStagePanel from '@/components/mainline/ChapterStagePanel.vue';
import type { MainlineChapterDto, MainlineStageDto } from '@api/mainline';

function stage(overrides: Partial<MainlineStageDto> = {}): MainlineStageDto {
  return {
    id: 1,
    index: 1,
    title: 's',
    videoWatchedSec: 0,
    requiredVideoSec: 240,
    pagesRead: 0,
    requiredPages: 2,
    examplesDone: 0,
    requiredExamples: 2,
    completed: false,
    ...overrides,
  };
}

function chapter(overrides: Partial<MainlineChapterDto> = {}): MainlineChapterDto {
  return {
    chapter: 3,
    title: '信息表示',
    unlocked: true,
    stars: 1,
    bestTestScore: 72,
    stages: [
      stage({ id: 31, index: 1, title: '数字与进制', videoWatchedSec: 120, pagesRead: 1, examplesDone: 0, completed: false }),
      stage({ id: 32, index: 2, title: '字符与编码', videoWatchedSec: 240, pagesRead: 2, examplesDone: 2, completed: true }),
    ],
    testStatus: 'READY',
    starBreakdown: { test: 1, stage: 0, mastery: 0, recovered: [] },
    recovery: { errorbookDone: false, endlessDone: false },
    ...overrides,
  };
}

describe('ChapterStagePanel', () => {
  it('renders chapter title, test status, best score and stage rows', () => {
    const wrapper = mount(ChapterStagePanel, { props: { chapter: chapter() } });
    expect(wrapper.get('[data-testid="mainline-chapter-title"]').text()).toContain('信息表示');
    expect(wrapper.get('[data-testid="mainline-test-status"]').text()).toContain('可应考');
    expect(wrapper.get('[data-testid="mainline-best-score"]').text()).toContain('72');
    expect(wrapper.get('[data-testid="mainline-chapter-stage-progress"]').text()).toContain('1/2');
    expect(wrapper.findAll('[data-testid^="mainline-stage-3"]').length).toBeGreaterThanOrEqual(2);
  });

  it('renders三件套 progress widths from stage data', () => {
    const wrapper = mount(ChapterStagePanel, { props: { chapter: chapter() } });
    const videoFill = wrapper.get('[data-testid="mainline-stage-31-video"] .piece__fill');
    expect(videoFill.attributes('style')).toContain('width: 50%');
    const exFill = wrapper.get('[data-testid="mainline-stage-31-examples"] .piece__fill');
    expect(exFill.attributes('style')).toContain('width: 0%');
    expect(wrapper.get('[data-testid="mainline-stage-32"]').attributes('data-done')).toBe('true');
  });

  it('emits enter-stage / open-test / open-endless / claim-errorbook', async () => {
    const wrapper = mount(ChapterStagePanel, { props: { chapter: chapter() } });
    await wrapper.get('[data-testid="mainline-enter-stage-31"]').trigger('click');
    expect(wrapper.emitted('enter-stage')?.[0]).toEqual([31]);

    await wrapper.get('[data-testid="mainline-open-test"]').trigger('click');
    expect(wrapper.emitted('open-test')?.[0]).toEqual([3]);

    await wrapper.get('[data-testid="mainline-claim-errorbook"]').trigger('click');
    expect(wrapper.emitted('claim-errorbook')?.[0]).toEqual([3]);

    await wrapper.get('[data-testid="mainline-open-endless"]').trigger('click');
    expect(wrapper.emitted('open-endless')?.[0]).toEqual([3]);
  });

  it('shows recovery as done when already awarded', () => {
    const wrapper = mount(ChapterStagePanel, {
      props: { chapter: chapter({ recovery: { errorbookDone: true, endlessDone: false } }) },
    });
    expect(wrapper.find('[data-testid="mainline-claim-errorbook"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="mainline-errorbook-done"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="mainline-open-endless"]').exists()).toBe(true);
  });

  it('hides recovery + shows full-star when stars=3', () => {
    const wrapper = mount(ChapterStagePanel, { props: { chapter: chapter({ stars: 3 }) } });
    expect(wrapper.find('[data-testid="mainline-recovery"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="mainline-full-star"]').exists()).toBe(true);
  });

  it('locked chapter shows notice, disables enter and hides actions', () => {
    const locked = chapter({
      chapter: 4,
      title: '流程自动化',
      unlocked: false,
      stars: 0,
      bestTestScore: null,
      testStatus: 'NONE',
      stages: [stage({ id: 41, index: 1, title: '顺序结构' })],
      recovery: { errorbookDone: false, endlessDone: false },
    });
    const wrapper = mount(ChapterStagePanel, { props: { chapter: locked } });
    expect(wrapper.find('[data-testid="mainline-locked-notice"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="mainline-enter-stage-41"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="mainline-open-test"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="mainline-recovery"]').exists()).toBe(false);
  });
});
