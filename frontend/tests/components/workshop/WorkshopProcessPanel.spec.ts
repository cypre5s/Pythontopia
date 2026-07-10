import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import WorkshopProcessPanel from '@/components/workshop/WorkshopProcessPanel.vue';
import { useWorkshopActivityStore } from '@stores/workshopActivity';

function mountPanel() {
  const pinia = createPinia();
  setActivePinia(pinia);
  return mount(WorkshopProcessPanel, { global: { plugins: [pinia] } });
}

describe('WorkshopProcessPanel', () => {
  it('idle 时只给出简短的用户提示', () => {
    const wrapper = mountPanel();

    expect(wrapper.text()).toContain('还没有处理记录');
    expect(wrapper.text()).toContain('课件命中情况');
    expect(wrapper.text()).not.toContain('MCP');
    expect(wrapper.text()).not.toContain('Runtime');
  });

  it('流式状态展示任务、可读步骤和课件命中结果', async () => {
    const wrapper = mountPanel();
    const activity = useWorkshopActivityStore();
    activity.start('ask', '答疑：for 循环');
    activity.syncTraces('ask', [
      {
        node: 'evidence_fetcher',
        phase: 'finish',
        status: 'ok',
        elapsed_ms: 120,
        chunks: 3,
        note: 'internal_trace_id=abc',
      },
    ]);
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain('正在处理');
    expect(wrapper.text()).toContain('答疑：for 循环');
    expect(wrapper.text()).toContain('证据检索');
    expect(wrapper.text()).toContain('命中 3 段课件');
    expect(wrapper.text()).not.toContain('internal_trace_id');
  });

  it('错误状态给出重试动作，不渲染原始错误 note', async () => {
    const wrapper = mountPanel();
    const activity = useWorkshopActivityStore();
    const token = activity.start('ask', '答疑');
    activity.syncTraces('ask', [
      {
        node: 'TutorAgent',
        phase: 'finish',
        status: 'fail',
        elapsed_ms: 30,
        note: 'stacktrace: provider timeout',
      },
    ], token);
    activity.finish(false, token);
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain('处理未完成');
    expect(wrapper.text()).toContain('请回到对话重试');
    expect(wrapper.text()).toContain('这一步未完成，请重试');
    expect(wrapper.text()).not.toContain('stacktrace');
  });
});
