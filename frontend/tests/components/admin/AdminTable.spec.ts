import { h } from 'vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AdminTable from '@/pages/admin/AdminTable.vue';

describe('AdminTable', () => {
  it('provides a labeled keyboard-scroll region around page-specific table markup', () => {
    const wrapper = mount(AdminTable, {
      props: { label: '用户列表', minWidth: 820 },
      slots: {
        default: () => h('table', { class: 'admin-users__table' }, [
          h('tbody', [h('tr', [h('td', '张同学')])]),
        ]),
      },
    });

    expect(wrapper.get('[role="region"]').attributes('aria-label')).toBe('用户列表');
    expect(wrapper.get('[role="region"]').attributes('tabindex')).toBe('0');
    expect(wrapper.get('table.admin-users__table').text()).toContain('张同学');
    expect(wrapper.attributes('style')).toContain('--admin-table-min-width: 820px');
  });
});
