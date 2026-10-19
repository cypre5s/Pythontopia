import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import TwinWeekly from '../TwinWeekly.vue';
import * as twinApi from '@/api/twin';

vi.mock('@/api/twin', () => ({ fetchWeeklyReport: vi.fn() }));

const mockReport: twinApi.WeeklyReport = {
  periodStart: '2026-06-03', periodEnd: '2026-06-09',
  memoryBrief: { totalEvents: 12, failedPractices: 3, succeededPractices: 5, topKcCodes: ['KC01'], highlights: ['x'] },
  insights: [{ id: 1, text: '加强错误处理', dimensionKey: 'error_pattern', tags: ['KC01'], createdAt: '2026-06-08T00:00:00Z' }],
  masteryDeltas: [{
    kcCode: 'KC01', currentScore: 0.75, confidence: 0.8, weeklyDelta: 0.1,
    evidenceCount: 2, evidenceSources: ['PRACTICE_PASS', 'DECAY'],
  }],
  resourceRuns: [{ agentName: 'practice', scenario: '靶向练习', status: 'SUCCESS', evidencePageCount: 3, createdAt: '2026-06-08T10:00:00Z' }],
};

describe('TwinWeekly', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('shows loading state', () => {
    vi.mocked(twinApi.fetchWeeklyReport).mockReturnValue(new Promise(() => {}));
    expect(mount(TwinWeekly).text()).toContain('...');
  });

  it('shows empty state', async () => {
    vi.mocked(twinApi.fetchWeeklyReport).mockResolvedValue(null as unknown as twinApi.WeeklyReport);
    const w = mount(TwinWeekly); await nextTick(); await nextTick();
    expect(w.text()).toContain('暂无数据');
  });

  it('renders report cards', async () => {
    vi.mocked(twinApi.fetchWeeklyReport).mockResolvedValue(mockReport);
    const w = mount(TwinWeekly); await nextTick(); await nextTick(); await nextTick();
    const t = w.text();
    expect(t).toContain('Learner Twin');
    expect(t).toContain('12'); expect(t).toContain('5'); expect(t).toContain('3');
    expect(t).toContain('错题复盘建议'); expect(t).toContain('加强错误处理');
    // 掌握度变化必须渲染（修复前 masteryDeltas 在 UI 缺席）。
    expect(t).toContain('KC01'); expect(t).toContain('掌握'); expect(t).toContain('证据');
    expect(t).toContain('置信指标 80%');
    expect(w.find('[data-testid="twin-confidence-metric"]').attributes('title'))
      .toContain('遗忘衰减变化');
    // SUCCESS 的 run 必须显示 ✓（修复前判 'OK' 恒不命中，真实数据显字面 SUCCESS）。
    expect(t).toContain('✓');
  });

  it('emits actionable prompts from weekly plan, insight and mastery rows', async () => {
    vi.mocked(twinApi.fetchWeeklyReport).mockResolvedValue(mockReport);
    const w = mount(TwinWeekly); await nextTick(); await nextTick(); await nextTick();

    await w.get('[data-testid="twin-weekly-plan"]').trigger('click');
    await w.get('[data-testid="twin-insight-prompt-1"]').trigger('click');
    await w.get('[data-testid="twin-mastery-prompt-KC01"]').trigger('click');

    const prompts = w.emitted('prompt')?.map((event) => event[0] as string) ?? [];
    expect(prompts).toHaveLength(3);
    expect(prompts[0]).toContain('看、学、练、测');
    expect(prompts[1]).toContain('加强错误处理');
    expect(prompts[2]).toContain('KC01');
    expect(prompts[2]).toContain('2 条可信学习证据');
    expect(prompts[2]).toContain('置信指标 80%');
    expect(prompts[2]).toContain('不是答题概率');
    expect(prompts[2]).toContain('本周证据贡献');
  });

  it('shows error state on API failure', async () => {
    vi.mocked(twinApi.fetchWeeklyReport).mockRejectedValue(new Error('nope'));
    const w = mount(TwinWeekly); await nextTick(); await nextTick(); await nextTick();
    expect(w.text()).toContain('无法加载');
  });
});
