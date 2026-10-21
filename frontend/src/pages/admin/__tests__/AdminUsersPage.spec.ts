import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AdminUserDto, AdminUserPageDto } from '@/api/adminUsers';
import AdminUsersPage from '../AdminUsersPage.vue';

const { searchAdminUsers, setAdminUserStatus, toastShow } = vi.hoisted(() => ({
  searchAdminUsers: vi.fn(),
  setAdminUserStatus: vi.fn(),
  toastShow: vi.fn(),
}));

vi.mock('@/api/adminUsers', () => ({ searchAdminUsers, setAdminUserStatus }));
vi.mock('@stores/session', () => ({ useSessionStore: () => ({ user: { id: 99 } }) }));
vi.mock('@stores/toast', () => ({ useToastStore: () => ({ show: toastShow }) }));

const activeUser: AdminUserDto = {
  id: 7,
  username: 'student7',
  displayName: '小明',
  role: 'STUDENT',
  status: 'ACTIVE',
};

const disabledUser: AdminUserDto = { ...activeUser, status: 'DISABLED' };

function page(content: AdminUserDto[]): AdminUserPageDto {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function mountPage() {
  return mount(AdminUsersPage, {
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  searchAdminUsers.mockResolvedValue(page([activeUser]));
  setAdminUserStatus.mockResolvedValue(disabledUser);
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value(this: HTMLDialogElement) { this.setAttribute('open', ''); },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value(this: HTMLDialogElement) { this.removeAttribute('open'); },
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AdminUsersPage', () => {
  it('confirms status changes in the shared admin dialog before calling the API', async () => {
    const nativeConfirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="admin-user-status-7"]').trigger('click');

    expect(nativeConfirm).not.toHaveBeenCalled();
    expect(wrapper.get('dialog.admin-dialog').attributes('open')).toBeDefined();
    expect(wrapper.text()).toContain('确认停用账号');
    expect(setAdminUserStatus).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="admin-user-status-confirm"]').trigger('click');
    await flushPromises();

    expect(setAdminUserStatus).toHaveBeenCalledWith(7, 'DISABLED');
    expect(toastShow).toHaveBeenCalledWith('账号已停用', 'success');
  });

  it('removes the redundant page heading and exposes the panel as the page heading', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('.admin-page-heading').exists()).toBe(false);
    expect(wrapper.find('.admin-panel__header h2').text()).toBe('用户管理');
  });

  it('keeps long account names readable without hover-only tooltips', async () => {
    const longName = '一'.repeat(128);
    searchAdminUsers.mockResolvedValueOnce(page([{
      ...activeUser,
      username: 'u'.repeat(64),
      displayName: longName,
    }]));
    const wrapper = mountPage();
    await flushPromises();

    const cells = wrapper.findAll('.admin-users__readable');
    expect(cells.map((cell) => cell.text())).toEqual([longName, 'u'.repeat(64)]);
    expect(cells.every((cell) => !cell.attributes('title'))).toBe(true);
  });

  it('does not leave stale account actions available when a filtered reload fails', async () => {
    const wrapper = mountPage();
    await flushPromises();
    searchAdminUsers.mockRejectedValueOnce(new Error('network error'));

    await wrapper.get('[data-testid="admin-users-role-filter"]').setValue('TEACHER');
    await flushPromises();

    expect(wrapper.find('[data-testid="admin-user-status-7"]').exists()).toBe(false);
    expect(wrapper.get('[role="alert"]').text()).toContain('network error');
  });

  it('leaves the account unchanged when the confirmation dialog is cancelled', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="admin-user-status-7"]').trigger('click');
    await wrapper.get('[data-testid="admin-user-status-cancel"]').trigger('click');

    expect(setAdminUserStatus).not.toHaveBeenCalled();
  });
});
