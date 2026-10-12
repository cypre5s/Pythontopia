import { describe, expect, it } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import ProfileRoomPage from '@/pages/ProfileRoomPage.vue';
import { useLearningStore } from '@/stores/learning';
import { useWorkshopUiStore } from '@/stores/workshopUi';

const promptText = '基于本周证据复习 PY43-KC-001';

describe('ProfileRoomPage Learner Twin handoff', () => {
  it('explains the real profile interview instead of showing placeholder copy', async () => {
    setActivePinia(createPinia());
    useLearningStore().profileInterview.sessionId = 'existing-session';
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/profile-room', component: ProfileRoomPage }],
    });
    await router.push('/profile-room');
    const wrapper = mount(ProfileRoomPage, {
      global: {
        plugins: [router],
        stubs: { AppLayout: { template: '<div><slot /></div>' }, ProfileInterviewPanel: true },
      },
    });

    expect(wrapper.text()).not.toContain('[占位]');
    expect(wrapper.text()).toContain('通过右侧访谈建立你的学习画像');
  });

  it('stores a Twin prompt for the workshop and navigates there', async () => {
    setActivePinia(createPinia());
    useLearningStore().profileInterview.sessionId = 'existing-session';
    const ui = useWorkshopUiStore();
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/profile-room', component: ProfileRoomPage },
        { path: '/workshop', component: { template: '<div />' } },
      ],
    });
    await router.push('/profile-room');

    const wrapper = mount(ProfileRoomPage, {
      global: {
        plugins: [router],
        stubs: {
          AppLayout: { template: '<div><slot /></div>' },
          ProfileInterviewPanel: true,
          AgentNotebook: true,
          TwinWeekly: {
            emits: ['prompt'],
            template: '<button data-testid="emit-prompt" @click="$emit(\'prompt\', promptText)">prompt</button>',
            setup() {
              return { promptText };
            },
          },
        },
      },
    });

    await wrapper.get('.weekly-btn').trigger('click');
    await wrapper.get('[data-testid="emit-prompt"]').trigger('click');
    await flushPromises();

    expect(ui.pendingPrompt).toBe(promptText);
    expect(router.currentRoute.value.path).toBe('/workshop');
  });
});
