import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AdminPanel from '@/pages/admin/AdminPanel.vue';

describe('AdminPanel', () => {
  it('renders its heading, toolbar, body and footer in a consistent order', () => {
    const wrapper = mount(AdminPanel, {
      props: { title: '用户管理', description: '查看账号状态' },
      slots: {
        actions: '<button>刷新</button>',
        toolbar: '<form data-testid="filters"><input aria-label="搜索"></form>',
        default: '<table aria-label="用户列表"></table>',
        footer: '<nav aria-label="列表分页"></nav>',
      },
    });

    const childClasses = Array.from(wrapper.element.children)
      .map((element) => (element as HTMLElement).className);
    expect(childClasses).toEqual([
      'admin-panel__header',
      'admin-panel__toolbar',
      'admin-panel__body',
      'admin-panel__footer',
    ]);
    expect(wrapper.get('.admin-panel__header h2').text()).toBe('用户管理');
    expect(wrapper.get('.admin-panel__toolbar [data-testid="filters"]').exists()).toBe(true);
    expect(wrapper.get('.admin-panel__body table').attributes('aria-label')).toBe('用户列表');
    expect(wrapper.get('.admin-panel__footer nav').attributes('aria-label')).toBe('列表分页');
  });
});
