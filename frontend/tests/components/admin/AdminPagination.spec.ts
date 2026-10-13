import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AdminPagination from '@/pages/admin/AdminPagination.vue';

describe('AdminPagination', () => {
  it('shows numeric pages and emits the zero-based selected page', async () => {
    const wrapper = mount(AdminPagination, {
      props: {
        page: 0,
        totalPages: 12,
        pageSize: 10,
        totalElements: 120,
        itemLabel: '条记录',
      },
    });

    expect(wrapper.get('[data-testid="admin-pagination-page-1"]').attributes('aria-current'))
      .toBe('page');
    await wrapper.get('[data-testid="admin-pagination-page-3"]').trigger('click');
    expect(wrapper.emitted('page-change')).toEqual([[2]]);
  });

  it('keeps the current page visible between ellipses for large lists', () => {
    const wrapper = mount(AdminPagination, {
      props: {
        page: 6,
        totalPages: 12,
        pageSize: 10,
        totalElements: 120,
        itemLabel: '条记录',
      },
    });

    expect(wrapper.findAll('button[data-testid^="admin-pagination-page-"]')
      .map((button) => button.text()))
      .toEqual(['1', '5', '6', '7', '8', '9', '12']);
    expect(wrapper.findAll('.admin-pagination__ellipsis')).toHaveLength(2);
  });
});
