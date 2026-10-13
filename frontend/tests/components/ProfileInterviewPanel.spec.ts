import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import ProfileInterviewPanel from '@/components/profile/ProfileInterviewPanel.vue';
import { useLearningStore } from '@/stores/learning';

describe('ProfileInterviewPanel', () => {
  it('shows completion action when the four interview dimensions are filled', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useLearningStore();
    store.profileInterview.sessionId = 'session-1';
    store.profileInterview.done = true;
    store.profileInterview.dimensionsFilled = [
      'professional_background',
      'knowledge_base',
      'learning_goal',
      'learning_preference',
    ];
    store.finishInterview = vi.fn(async () => undefined);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/profile', component: { template: '<div />' } }],
    });
    await router.push('/profile');

    const wrapper = mount(ProfileInterviewPanel, {
      global: { plugins: [pinia, router] },
    });

    expect(wrapper.text()).toContain('4 / 4');
    expect(wrapper.get('button.finish').text()).toContain('完成访谈');
  });

  it('keeps the answer and shows an error when sending a turn fails', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useLearningStore();
    store.profileInterview.sessionId = 'session-1';
    store.sendTurn = vi.fn().mockRejectedValue(new Error('网络暂时不可用'));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/interview', component: { template: '<div />' } },
        { path: '/profile', component: { template: '<div />' } },
      ],
    });
    await router.push('/interview');

    const wrapper = mount(ProfileInterviewPanel, { global: { plugins: [pinia, router] } });
    const textarea = wrapper.get('textarea');
    await textarea.setValue('我的回答');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect((textarea.element as HTMLTextAreaElement).value).toBe('我的回答');
    expect(wrapper.get('[data-testid="profile-interview-error"]').text()).toContain(
      '网络暂时不可用',
    );
  });

  it('shows an error and stays on the interview when finishing fails', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const store = useLearningStore();
    store.profileInterview.done = true;
    store.finishInterview = vi.fn().mockRejectedValue(new Error('保存画像失败'));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/interview', component: { template: '<div />' } },
        { path: '/profile', component: { template: '<div />' } },
      ],
    });
    await router.push('/interview');

    const wrapper = mount(ProfileInterviewPanel, { global: { plugins: [pinia, router] } });
    await wrapper.get('button.finish').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/interview');
    expect(wrapper.get('[data-testid="profile-interview-error"]').text()).toContain(
      '保存画像失败',
    );
  });
});
