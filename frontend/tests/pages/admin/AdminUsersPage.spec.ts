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

function mountAdminUsersPage(attachTo?: HTMLElement) {
  return mount(AdminUsersPage, {
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    ...(attachTo ? { attachTo } : {}),
  });
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value(this: HTMLDialogElement) { this.setAttribute('open', ''); },
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value(this: HTMLDialogElement) { this.removeAttribute('open'); },
    });
  });

  it('loads real paged users and lets an administrator disable an account', async () => {
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    expect(searchAdminUsers).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(wrapper.text()).toContain('学生七号');
    expect(wrapper.text()).toContain('student7');
    expect(wrapper.get('.admin-panel__toolbar [data-testid="admin-users-search-form"]').exists()).toBe(true);
    expect(wrapper.get('.admin-panel__toolbar').findAll('.admin-control')).toHaveLength(3);
    expect(wrapper.get('.admin-panel__footer .admin-pagination').exists()).toBe(true);
    expect(wrapper.get('.admin-users__status').classes()).toContain('admin-status--positive');
    expect(wrapper.get('.admin-table-scroll').attributes('role')).toBe('region');

    await wrapper.get('[data-testid="admin-user-status-7"]').trigger('click');
    expect(setAdminUserStatus).not.toHaveBeenCalled();
    expect(wrapper.get('dialog.admin-dialog').attributes('open')).toBeDefined();
    await wrapper.get('[data-testid="admin-user-status-confirm"]').trigger('click');
    await flushPromises();

    expect(setAdminUserStatus).toHaveBeenCalledWith(7, 'DISABLED');
    expect(searchAdminUsers).toHaveBeenCalledTimes(2);
  });

  it('closes the themed account status dialog on Escape and restores focus', async () => {
    const wrapper = mountAdminUsersPage(document.body);
    await flushPromises();
    const opener = wrapper.get('[data-testid="admin-user-status-7"]').element as HTMLButtonElement;
    const focus = vi.spyOn(opener, 'focus');

    await wrapper.get('[data-testid="admin-user-status-7"]').trigger('click');
    await flushPromises();
    expect(document.activeElement).toBe(wrapper.get('[data-testid="admin-user-status-cancel"]').element);
    await wrapper.get('dialog.admin-dialog').trigger('cancel');
    await flushPromises();

    expect(wrapper.find('dialog.admin-dialog').exists()).toBe(false);
    expect(focus).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('keeps the status dialog open after a failed update and allows retry', async () => {
    vi.mocked(setAdminUserStatus)
      .mockRejectedValueOnce(new Error('状态更新失败'))
      .mockResolvedValueOnce({ ...activeUser, status: 'DISABLED' });
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    await wrapper.get('[data-testid="admin-user-status-7"]').trigger('click');
    await wrapper.get('[data-testid="admin-user-status-confirm"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('状态更新失败');
    expect(wrapper.find('dialog.admin-dialog').exists()).toBe(true);
    await wrapper.get('[data-testid="admin-user-status-confirm"]').trigger('click');
    await flushPromises();

    expect(setAdminUserStatus).toHaveBeenCalledTimes(2);
    expect(wrapper.find('dialog.admin-dialog').exists()).toBe(false);
  });

  it('removes old status actions while a list refresh is in flight', async () => {
    vi.mocked(setAdminUserStatus).mockClear();
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    let resolveRefresh!: (value: {
      content: typeof activeUser[];
      page: number;
      size: number;
      totalElements: number;
      totalPages: number;
    }) => void;
    vi.mocked(searchAdminUsers).mockImplementationOnce(
      () => new Promise((resolve) => { resolveRefresh = resolve; }),
    );

    const refresh = wrapper.get('[data-testid="admin-users-refresh"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="admin-user-status-7"]').exists()).toBe(false);

    resolveRefresh({
      content: [activeUser],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    await refresh;
    await flushPromises();
    expect(setAdminUserStatus).not.toHaveBeenCalled();
  });

  it('filters the real user list by role', async () => {
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    await wrapper.get('[data-testid="admin-users-role-filter"]').setValue('TEACHER');
    await flushPromises();

    expect(searchAdminUsers).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      role: 'TEACHER',
    });
  });

  it('supports page-size changes and numbered page selection through the API', async () => {
    vi.mocked(searchAdminUsers).mockResolvedValue({
      content: [activeUser], page: 0, size: 20, totalElements: 45, totalPages: 3,
    });
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    await wrapper.get('[data-testid="admin-pagination-size"]').setValue('50');
    await flushPromises();
    expect(searchAdminUsers).toHaveBeenLastCalledWith({ page: 0, size: 50 });

    await wrapper.get('[data-testid="admin-pagination-page-3"]').trigger('click');
    await flushPromises();
    expect(searchAdminUsers).toHaveBeenLastCalledWith({ page: 2, size: 50 });
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
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    expect(wrapper.get('[data-testid="admin-user-status-1"]').attributes('disabled')).toBeDefined();
  });

  it('keeps long display names and usernames readable in the table', async () => {
    const displayName = '学生'.repeat(30);
    const username = 'student'.repeat(20);
    vi.mocked(searchAdminUsers).mockResolvedValueOnce({
      content: [{ ...activeUser, displayName, username }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const wrapper = mountAdminUsersPage();
    await flushPromises();

    const cells = wrapper.findAll('tbody td');
    expect(cells[1].classes()).toContain('admin-users__readable');
    expect(cells[2].classes()).toContain('admin-users__readable');
    expect(cells[1].text()).toBe(displayName);
    expect(cells[2].text()).toBe(username);
  });
});
