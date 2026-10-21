import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminAiConfigPage from '../AdminAiConfigPage.vue';

const { fetchAdminAiConfig, testAdminAiConfig, updateAdminAiConfig } = vi.hoisted(() => ({
  fetchAdminAiConfig: vi.fn(), testAdminAiConfig: vi.fn(), updateAdminAiConfig: vi.fn(),
}));
vi.mock('@/api/adminAiConfig', () => ({ fetchAdminAiConfig, testAdminAiConfig, updateAdminAiConfig }));

beforeEach(() => {
  vi.clearAllMocks();
  fetchAdminAiConfig.mockResolvedValue({ provider: 'DeepSeek', model: 'deepseek-v4-pro', apiKeyConfigured: true, keyStored: false, updatedAt: null });
  testAdminAiConfig.mockResolvedValue({ success: true, model: 'deepseek-flash', message: 'DeepSeek 连接成功' });
  updateAdminAiConfig.mockResolvedValue({ provider: 'DeepSeek', model: 'deepseek-flash', apiKeyConfigured: true, keyStored: true, updatedAt: '2026-10-21T10:00:00Z' });
});

describe('AdminAiConfigPage', () => {
  it('shows only masked key state and tests the selected DeepSeek model', async () => {
    const wrapper = mount(AdminAiConfigPage);
    await flushPromises();
    expect(wrapper.text()).toContain('DeepSeek');
    expect(wrapper.text()).toContain('部署环境密钥');
    expect(wrapper.text()).not.toContain('deepseek-api-key');
    await wrapper.get('[data-testid="admin-ai-model"]').setValue('deepseek-flash');
    await wrapper.get('[data-testid="admin-ai-test"]').trigger('click');
    await flushPromises();
    expect(testAdminAiConfig).toHaveBeenCalledWith({ model: 'deepseek-flash' });
    expect(wrapper.text()).toContain('连接成功');
  });

  it('applies the selected model and optional new key', async () => {
    const wrapper = mount(AdminAiConfigPage);
    await flushPromises();
    await wrapper.get('[data-testid="admin-ai-model"]').setValue('deepseek-flash');
    await wrapper.get('[data-testid="admin-ai-key"]').setValue('deepseek-api-key');
    await wrapper.get('.admin-ai-config__form').trigger('submit');
    await flushPromises();
    expect(updateAdminAiConfig).toHaveBeenCalledWith({ model: 'deepseek-flash', apiKey: 'deepseek-api-key' });
    expect(wrapper.find('[data-testid="admin-ai-key"]').element).toHaveProperty('value', '');
  });
});
