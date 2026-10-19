import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ChapterStagePanel from '@/components/mainline/ChapterStagePanel.vue';
import type { MainlineChapterDto, MainlineStageDto } from '@api/mainline';

function stage(overrides: Partial<MainlineStageDto> = {}): MainlineStageDto {
  return {
    id: 1,
    index: 1,
    title: 's',
    kcCodes: [],
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
  it('高亮第一个未完成小节为「继续学习」断点续学 CTA', () => {
    const wrapper = mount(ChapterStagePanel, { props: { chapter: chapter() } });
    const resume = wrapper.get('[data-testid="mainline-stage-31"]');
    expect(resume.attributes('data-resume')).toBe('true');
    expect(resume.text()).toContain('当前');
    expect(wrapper.get('[data-testid="mainline-enter-stage-31"]').text()).toContain('继续学习');
    // 已完成小节保持普通入口，不抢焦点。
    const done = wrapper.get('[data-testid="mainline-stage-32"]');
    expect(done.attributes('data-resume')).toBe('false');
    expect(wrapper.get('[data-testid="mainline-enter-stage-32"]').text()).toContain('进入学习');
  });

  it('锁定章节没有断点续学焦点', () => {
    const wrapper = mount(ChapterStagePanel, {
      props: { chapter: chapter({ unlocked: false }) },
    });
    expect(wrapper.get('[data-testid="mainline-stage-31"]').attributes('data-resume')).toBe(
      'false',
    );
  });

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

  it('required=0 的维度显示「—」空槽而不是 100% 满绿条', () => {
    // R-14 起 requiredExamples 常为 0：旧 ratio() 对 required<=0 返回 1，把 0/0 画成满格，
    // 学生看到三条全绿却没有 ✓、按钮还锁着。
    const wrapper = mount(ChapterStagePanel, {
      props: {
        chapter: chapter({
          stages: [stage({ id: 31, index: 1, requiredExamples: 0, examplesDone: 0, requiredPages: 0, pagesRead: 0 })],
        }),
      },
    });

    const examples = wrapper.get('[data-testid="mainline-stage-31-examples"]');
    expect(examples.attributes('data-na')).toBe('true');
    expect(examples.get('.piece__fill').attributes('style')).toContain('width: 0%');
    expect(examples.text()).not.toContain('0/0');
    expect(examples.text()).toContain('本节无要求');

    const pages = wrapper.get('[data-testid="mainline-stage-31-pages"]');
    expect(pages.get('.piece__fill').attributes('style')).toContain('width: 0%');
    // 有要求的维度照常按比例渲染。
    expect(wrapper.get('[data-testid="mainline-stage-31-video"]').attributes('data-na')).toBe('false');
  });

  it('三件套计数全满但服务端未判完成时，点出「客观题全对」这个隐藏维度', () => {
    const wrapper = mount(ChapterStagePanel, {
      props: {
        chapter: chapter({
          stages: [
            stage({
              id: 31,
              index: 1,
              videoWatchedSec: 240,
              pagesRead: 2,
              examplesDone: 2,
              completed: false,
            }),
          ],
        }),
      },
    });

    expect(wrapper.find('[data-testid="mainline-stage-31-question-gap"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="mainline-stage-31-question-gap"]').text()).toContain('客观题');
    // 「参加大测试」的说明同样点名客观题维度。
    expect(wrapper.get('[data-testid="mainline-open-test-hint"]').text()).toContain('客观题');
  });

  it('小节已完成时不显示客观题提示', () => {
    const wrapper = mount(ChapterStagePanel, {
      props: {
        chapter: chapter({
          stages: [stage({ id: 31, index: 1, videoWatchedSec: 240, pagesRead: 2, examplesDone: 2, completed: true })],
        }),
      },
    });
    expect(wrapper.find('[data-testid="mainline-stage-31-question-gap"]').exists()).toBe(false);
  });

  it('章末 BOSS 入口仅在本章 ≥1★ 时渲染并 emit open-boss', async () => {
    const locked = mount(ChapterStagePanel, { props: { chapter: chapter({ stars: 0 }) } });
    expect(locked.find('[data-testid="mainline-open-boss"]').exists()).toBe(false);

    const wrapper = mount(ChapterStagePanel, { props: { chapter: chapter({ stars: 1 }) } });
    await wrapper.get('[data-testid="mainline-open-boss"]').trigger('click');
    expect(wrapper.emitted('open-boss')?.[0]).toEqual([3]);
  });

  it('补星申领中禁用「错题复盘补星」按钮（防连点连弹互斥提示）', () => {
    const wrapper = mount(ChapterStagePanel, {
      props: { chapter: chapter(), claimingErrorbook: true },
    });
    const button = wrapper.get('[data-testid="mainline-claim-errorbook"]');
    expect(button.attributes('disabled')).toBeDefined();
    expect(button.text()).toContain('申领中');
  });

  it('emits enter-stage / open-test / open-endless / claim-errorbook', async () => {
    const ready = chapter({
      stages: [
        stage({ id: 31, index: 1, title: '数字与进制', videoWatchedSec: 240, pagesRead: 2, examplesDone: 2, completed: true }),
        stage({ id: 32, index: 2, title: '字符与编码', videoWatchedSec: 240, pagesRead: 2, examplesDone: 2, completed: true }),
      ],
    });
    const wrapper = mount(ChapterStagePanel, { props: { chapter: ready } });
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
