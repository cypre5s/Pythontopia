import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminUsagePage from '../AdminUsagePage.vue';

const { fetchAdminUsage } = vi.hoisted(() => ({ fetchAdminUsage: vi.fn() }));
vi.mock('@/api/adminUsage', () => ({ fetchAdminUsage }));

beforeEach(() => {
  vi.clearAllMocks();
  fetchAdminUsage.mockResolvedValue({
    content: [
      { id: 1, eventType: 'LOOP_RUN', userId: 4, username: 'student4', label: '学习闭环 #1', occurredAt: '2026-10-20T12:00:00Z', status: '已记录', inputTokens: 90, outputTokens: 20 },
      { id: 2, eventType: 'RESOURCE_RUN', userId: 4, username: 'student4', label: 'WORKSHOP', occurredAt: '2026-10-20T11:00:00Z', status: 'SUCCESS', inputTokens: null, outputTokens: null },
    ],
    days: 30, totalEvents: 2, inputTokens: 90, outputTokens: 20, unknownTokenEvents: 1,
  });
});

describe('AdminUsagePage', () => {
  it('shows real activity and distinguishes recorded token usage from unknown history', async () => {
    const wrapper = mount(AdminUsagePage);
    await flushPromises();
    expect(wrapper.text()).toContain('student4');
    expect(wrapper.text()).toContain('90');
    expect(wrapper.text()).toContain('未采集');
    expect(wrapper.text()).toContain('不统计历史费用');
  });

  it('filters history by username and selected date range', async () => {
    const wrapper = mount(AdminUsagePage);
    await flushPromises();
    await wrapper.get('[data-testid="admin-usage-username"]').setValue(' student4 ');
    await wrapper.get('[data-testid="admin-usage-days"]').setValue('7');
    await wrapper.get('[data-testid="admin-usage-search"]').trigger('click');
    await flushPromises();
    expect(fetchAdminUsage).toHaveBeenLastCalledWith({ days: 7, username: 'student4' });
  });
});
