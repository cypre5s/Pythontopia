import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import BrowseTimeline from '@/components/workshop/BrowseTimeline.vue';
import type { BrowseRunDetailDto } from '@/api/browse';

function makeRun(partial: Partial<BrowseRunDetailDto> = {}): BrowseRunDetailDto {
  return {
    id: 1,
    goal: '查 sorted 是否稳定',
    status: 'SUCCESS',
    finalSummary: 'sorted 是稳定排序。',
    truncated: false,
    error: '',
    createdAt: '2026-06-17T05:00:00Z',
    steps: [
      {
        index: 0,
        action: 'navigate',
        url: 'https://docs.python.org/3/howto/sorting.html',
        summary: '打开官方排序指南',
        title: 'Sorting HOW TO',
        snippet: 'Sorts are guaranteed to be stable.',
      },
      {
        index: 1,
        action: 'read',
        url: '',
        summary: '读到 sorts are guaranteed to be stable',
        title: '',
        snippet: '读到 sorts are guaranteed to be stable',
      },
    ],
    citations: [
      {
        url: 'https://docs.python.org/3/howto/sorting.html',
        label: '外部内容，非课件',
      },
    ],
    ...partial,
  };
}

describe('BrowseTimeline', () => {
  it('run 为 null → 不渲染', () => {
    const wrapper = mount(BrowseTimeline, { props: { run: null } });
    expect(wrapper.find('[data-testid="browse-timeline"]').exists()).toBe(false);
  });

  it('渲染步骤：动作中文 + 可点外链（target/_blank + rel noopener）+ 摘要', () => {
    const wrapper = mount(BrowseTimeline, { props: { run: makeRun() } });

    const steps = wrapper.findAll('[data-testid="browse-step"]');
    expect(steps).toHaveLength(2);

    // 第一步 navigate → 「打开」+ 安全外链。
    expect(steps[0]?.get('[data-testid="browse-step-action"]').text()).toBe('打开');
    const link = steps[0]?.get('[data-testid="browse-step-url"]');
    expect(link?.attributes('href')).toBe('https://docs.python.org/3/howto/sorting.html');
    expect(link?.attributes('target')).toBe('_blank');
    expect(link?.attributes('rel')).toContain('noopener');
    expect(steps[0]?.get('[data-testid="browse-step-title"]').text()).toBe('Sorting HOW TO');
    expect(steps[0]?.get('[data-testid="browse-step-snippet"]').text()).toContain(
      'guaranteed to be stable',
    );

    // 第二步 read 且 url 为空 → 无链接，仅摘要。
    expect(steps[1]?.get('[data-testid="browse-step-action"]').text()).toBe('阅读');
    expect(steps[1]?.find('[data-testid="browse-step-url"]').exists()).toBe(false);
    expect(steps[1]?.get('[data-testid="browse-step-snippet"]').text()).toContain(
      'guaranteed to be stable',
    );
  });

  it('引用 url 渲染为可点外链，且统一标注「外部内容，非课件」（顶部 banner + 引用分区 + aria）', () => {
    const wrapper = mount(BrowseTimeline, { props: { run: makeRun() } });

    expect(wrapper.get('[data-testid="browse-external-notice"]').text()).toContain(
      '外部内容，非课件',
    );
    expect(wrapper.get('[data-testid="browse-citations-notice"]').text()).toContain(
      '外部内容，非课件',
    );

    const cites = wrapper.findAll('[data-testid="browse-citation"]');
    expect(cites).toHaveLength(1);
    expect(cites[0]?.attributes('href')).toBe('https://docs.python.org/3/howto/sorting.html');
    expect(cites[0]?.attributes('rel')).toContain('noopener');
    expect(cites[0]?.attributes('aria-label')).toContain('外部内容，非课件');
  });

  it('截断态：truncated=true → 步数上限提示 + 状态徽章', () => {
    const wrapper = mount(BrowseTimeline, {
      props: { run: makeRun({ truncated: true, status: 'TRUNCATED' }) },
    });

    expect(wrapper.get('[data-testid="browse-truncated"]').text()).toContain('步数上限');
    expect(wrapper.get('[data-testid="browse-status"]').text()).toBe('已达步数上限');
  });

  it('错误态：error 非空 → 红色错误块（role=alert）+ 回退 VIDEO 提示，不静默吞', () => {
    const wrapper = mount(BrowseTimeline, {
      props: {
        run: makeRun({ status: 'FAILED', error: '目标域不在白名单', steps: [], citations: [] }),
      },
    });

    const err = wrapper.get('[data-testid="browse-error"]');
    expect(err.text()).toContain('目标域不在白名单');
    expect(err.attributes('role')).toBe('alert');
    expect(wrapper.get('[data-testid="browse-fallback"]').text()).toContain('看课视频');
  });

  it('空步骤边界：steps=[] → 显示「暂无浏览步骤」，仍渲染整体结论', () => {
    const wrapper = mount(BrowseTimeline, { props: { run: makeRun({ steps: [], citations: [] }) } });

    expect(wrapper.findAll('[data-testid="browse-step"]')).toHaveLength(0);
    expect(wrapper.get('[data-testid="browse-empty-steps"]').text()).toContain('暂无浏览步骤');
    expect(wrapper.get('[data-testid="browse-final-summary"]').text()).toContain('稳定排序');
    // 无引用时不渲染引用分区。
    expect(wrapper.find('[data-testid="browse-citations"]').exists()).toBe(false);
  });

  it('不安全 url（javascript:）→ 不渲染为可点链接（防注入），citation 退化为纯文本', () => {
    const wrapper = mount(BrowseTimeline, {
      props: {
        run: makeRun({
          steps: [
            {
              index: 0,
              action: 'navigate',
              url: 'javascript:alert(1)',
              summary: '坏链接',
              title: '',
              snippet: '坏链接',
            },
          ],
          citations: [{ url: 'javascript:alert(1)', label: '外部内容，非课件' }],
        }),
      },
    });

    expect(wrapper.find('[data-testid="browse-step-url"]').exists()).toBe(false);
    const cite = wrapper.get('[data-testid="browse-citation"]');
    expect(cite.element.tagName).toBe('SPAN');
    expect(cite.attributes('href')).toBeUndefined();
  });

  it('旧记录缺少 title/snippet 时回退 summary，不出现空白证据', () => {
    const legacy = makeRun({
      steps: [
        {
          index: 0,
          action: 'read',
          url: 'https://docs.python.org/3/',
          summary: 'legacy summary',
          title: undefined as unknown as string,
          snippet: undefined as unknown as string,
        },
      ],
    });

    const wrapper = mount(BrowseTimeline, { props: { run: legacy } });

    expect(wrapper.find('[data-testid="browse-step-title"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="browse-step-snippet"]').text()).toBe('legacy summary');
  });

  it('状态徽章可读（文字双通道，不靠颜色）+ role=status', () => {
    const wrapper = mount(BrowseTimeline, { props: { run: makeRun() } });

    const badge = wrapper.get('[data-testid="browse-status"]');
    expect(badge.text()).toBe('查证完成');
    expect(badge.attributes('role')).toBe('status');
  });
});
