import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import AdminUsersPage from '@/pages/admin/AdminUsersPage.vue';
import { searchAdminUsers, setAdminUserStatus } from '@/api/adminUsers';
import { useSessionStore } from '@stores/session';

vi.mock('@/api/adminUsers', () => ({
  searchAdminUsers: vi.fn(),
  setAdminUserStatus: vi.fn(),
}));

const activeUser = {
  id: 7,
  username: 'student7',
  displayName: '学生七号',
  role: 'STUDENT' as const,
  status: 'ACTIVE' as const,
};

describe('AdminUsersPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useSessionStore().user = {
      id: 1,
      username: 'admin',
      displayName: '管理员',
      role: 'ADMIN',
    };
    vi.mocked(searchAdminUsers).mockResolvedValue({
      content: [activeUser],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(setAdminUserStatus).mockResolvedValue({ ...activeUser, status: 'DISABLED' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('loads real paged users and lets an administrator disable an account', async () => {
    const wrapper = mount(AdminUsersPage);
    await flushPromises();

    expect(searchAdminUsers).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(wrapper.text()).toContain('学生七号');
    expect(wrapper.text()).toContain('student7');

    await wrapper.get('[data-testid="admin-user-status-7"]').trigger('click');
    await flushPromises();

    expect(window.confirm).toHaveBeenCalled();
    expect(setAdminUserStatus).toHaveBeenCalledWith(7, 'DISABLED');
    expect(searchAdminUsers).toHaveBeenCalledTimes(2);
  });

  it('filters the real user list by role', async () => {
    const wrapper = mount(AdminUsersPage);
    await flushPromises();

    await wrapper.get('[data-testid="admin-users-role-filter"]').setValue('TEACHER');
    await flushPromises();

    expect(searchAdminUsers).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      role: 'TEACHER',
    });
  });

  it('does not offer the current administrator a self-disable action', async () => {
    vi.mocked(searchAdminUsers).mockResolvedValueOnce({
      content: [{
        id: 1,
        username: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
        status: 'ACTIVE',
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const wrapper = mount(AdminUsersPage);
    await flushPromises();

    expect(wrapper.get('[data-testid="admin-user-status-1"]').attributes('disabled')).toBeDefined();
  });

  it('keeps full display name and username available when long cells are truncated', async () => {
    const displayName = '学生'.repeat(30);
    const username = 'student'.repeat(20);
    vi.mocked(searchAdminUsers).mockResolvedValueOnce({
      content: [{ ...activeUser, displayName, username }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const wrapper = mount(AdminUsersPage);
    await flushPromises();

    const cells = wrapper.findAll('tbody td');
    expect(cells[1].attributes('title')).toBe(displayName);
    expect(cells[2].attributes('title')).toBe(username);
  });
});
