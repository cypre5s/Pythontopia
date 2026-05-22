import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';
import FieldPage from '@/pages/FieldPage.vue';

describe('FieldPage (M2 redirect 占位)', () => {
  it('挂载后 replace 到 /world', async () => {
    setActivePinia(createPinia());
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/field', component: FieldPage },
        { path: '/world', component: { template: '<div data-testid="world" />' } },
      ],
    });
    await router.push('/field');
    mount(FieldPage, { global: { plugins: [router] } });
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('渲染 legacy-redirect 占位元素', () => {
    setActivePinia(createPinia());
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/field', component: FieldPage },
        { path: '/world', component: { template: '<div />' } },
      ],
    });
    const wrapper = mount(FieldPage, { global: { plugins: [router] } });
    expect(wrapper.find('.legacy-redirect').exists()).toBe(true);
  });
});
