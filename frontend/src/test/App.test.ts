import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { vi } from 'vitest';
import App from '@/App.vue';
import { router } from '@/app/router';

vi.mock('@/components/game/GameCanvas.vue', () => ({
  default: { template: '<div class="game-canvas-mock" />' },
}));

vi.mock('@/components/game/FieldCanvas.vue', () => ({
  default: { template: '<div class="field-canvas-mock" />' },
}));

test('renders the login entry when unauthenticated', async () => {
  const pinia = createPinia();
  setActivePinia(pinia);
  localStorage.clear();
  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  });
  await router.push('/');
  await router.isReady();

  expect(wrapper.text()).toContain('Pythontopia');
  expect(wrapper.text()).toContain('登录');
});
