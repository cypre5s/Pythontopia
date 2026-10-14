import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import AdminPanel from '../AdminPanel.vue';

describe('AdminPanel', () => {
  it('keeps toolbar controls in the panel header', () => {
    const wrapper = mount(AdminPanel, {
      props: { title: '用户管理' },
      slots: {
        toolbar: '<input aria-label="搜索用户">',
        actions: '<button>刷新</button>',
      },
    });

    const tools = wrapper.find('.admin-panel__header .admin-panel__tools');
    expect(tools.exists()).toBe(true);
    expect(tools.find('.admin-panel__toolbar input').exists()).toBe(true);
    expect(tools.find('.admin-panel__actions button').exists()).toBe(true);
    expect(wrapper.find('.admin-panel > .admin-panel__toolbar').exists()).toBe(false);
  });
});
