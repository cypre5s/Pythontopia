<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';
import {
  searchAdminUsers,
  setAdminUserStatus,
  type AdminUserDto,
  type AdminUserRoleFilter,
  type AdminUserStatus,
  type AdminUserStatusFilter,
} from '@/api/adminUsers';
import AdminPanel from './AdminPanel.vue';
import AdminTable from './AdminTable.vue';
import AdminPagination from './AdminPagination.vue';

const session = useSessionStore();
const toast = useToastStore();
const entries = ref<AdminUserDto[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchInput = ref('');
const searchTerm = ref('');
const statusFilter = ref<AdminUserStatusFilter>('ALL');
const roleFilter = ref<AdminUserRoleFilter>('ALL');
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const updatingUserId = ref<number | null>(null);
const statusChangeUser = ref<AdminUserDto | null>(null);
const statusChangeError = ref<string | null>(null);
const statusDialog = ref<HTMLDialogElement | null>(null);
const statusChangeTrigger = ref<HTMLButtonElement | null>(null);
const usersPage = ref<HTMLElement | null>(null);
const listBusy = computed(() => loading.value || updatingUserId.value !== null);
const pageSize = ref(20);

function roleLabel(role: AdminUserDto['role']): string {
  if (role === 'ADMIN') return '管理员';
  if (role === 'TEACHER') return '教师';
  return '学生';
}

function statusLabel(status: AdminUserStatus): string {
  return status === 'ACTIVE' ? '正常' : '已停用';
}

async function loadUsers(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = null;
  entries.value = [];
  totalElements.value = 0;
  totalPages.value = 0;
  try {
    const readPage = () => searchAdminUsers({
      page: page.value,
      size: pageSize.value,
      ...(searchTerm.value ? { q: searchTerm.value } : {}),
      ...(statusFilter.value === 'ALL' ? {} : { status: statusFilter.value }),
      ...(roleFilter.value === 'ALL' ? {} : { role: roleFilter.value }),
    });
    let result = await readPage();
    const lastPage = Math.max(0, result.totalPages - 1);
    if (page.value > lastPage) {
      page.value = lastPage;
      result = await readPage();
    }
    entries.value = result.content;
    totalElements.value = result.totalElements;
    totalPages.value = result.totalPages;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '用户列表暂不可用';
    entries.value = [];
    totalElements.value = 0;
    totalPages.value = 0;
  } finally {
    loading.value = false;
  }
}

function applyFilters(): void {
  page.value = 0;
  void loadUsers();
}

function searchUsers(): void {
  searchTerm.value = searchInput.value.trim();
  applyFilters();
}

function goToPage(nextPage: number): void {
  if (listBusy.value || nextPage < 0 || nextPage >= totalPages.value) return;
  page.value = nextPage;
  void loadUsers();
}

function changePageSize(nextSize: number): void {
  if (listBusy.value || nextSize === pageSize.value) return;
  pageSize.value = nextSize;
  page.value = 0;
  void loadUsers();
}

function toggleStatus(user: AdminUserDto, event: MouseEvent): void {
  if (user.id === session.user?.id || updatingUserId.value !== null) return;
  statusChangeTrigger.value = event.currentTarget as HTMLButtonElement;
  statusChangeUser.value = user;
  statusChangeError.value = null;
  void nextTick(() => {
    statusDialog.value?.showModal();
    statusDialog.value?.querySelector<HTMLButtonElement>('[data-testid="admin-user-status-cancel"]')?.focus();
  });
}

function dismissStatusChange(restoreFocus = true): void {
  statusDialog.value?.close();
  statusChangeUser.value = null;
  statusChangeError.value = null;
  void nextTick(() => {
    if (restoreFocus && statusChangeTrigger.value?.isConnected) statusChangeTrigger.value.focus();
    else usersPage.value?.focus();
  });
}

async function confirmStatusChange(): Promise<void> {
  const user = statusChangeUser.value;
  if (!user || updatingUserId.value !== null) return;
  const status: AdminUserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  const action = status === 'DISABLED' ? '停用' : '启用';
  updatingUserId.value = user.id;
  try {
    await setAdminUserStatus(user.id, status);
    dismissStatusChange(false);
    toast.show(`账号已${action}`, 'success');
    await loadUsers();
  } catch (cause) {
    statusChangeError.value = cause instanceof Error ? cause.message : `账号${action}失败`;
    toast.show(statusChangeError.value, 'error');
  } finally {
    updatingUserId.value = null;
  }
}

onMounted(() => {
  void loadUsers();
});
</script>

<template>
  <section
    ref="usersPage"
    class="admin-users"
    tabindex="-1"
    data-testid="admin-users"
    :aria-busy="loading"
  >
    <header class="admin-page-heading">
      <h1>用户管理</h1>
      <p>查看账号角色与状态；停用或启用账号会撤销该账号的现有登录令牌。</p>
    </header>

    <AdminPanel
      title="账号列表"
      description="仅提供账号状态管理，密码与凭据不会出现在列表中。"
    >
      <template #actions>
        <form
          class="admin-users__filters"
          data-testid="admin-users-search-form"
          @submit.prevent="searchUsers"
        >
          <label class="admin-users__search">
            <span>搜索账号或显示名</span>
            <input
              v-model="searchInput"
              type="search"
              maxlength="100"
              :disabled="listBusy"
              aria-label="搜索账号或显示名"
              data-testid="admin-users-search"
            >
          </label>
          <label class="admin-users__status-filter">
            <span>账号状态</span>
            <select
              v-model="statusFilter"
              aria-label="按账号状态筛选"
              :disabled="listBusy"
              @change="applyFilters"
            >
              <option value="ALL">全部状态</option>
              <option value="ACTIVE">正常</option>
              <option value="DISABLED">已停用</option>
            </select>
          </label>
          <label class="admin-users__role-filter">
            <span>用户角色</span>
            <select
              v-model="roleFilter"
              aria-label="按用户角色筛选"
              data-testid="admin-users-role-filter"
              :disabled="listBusy"
              @change="applyFilters"
            >
              <option value="ALL">全部角色</option>
              <option value="STUDENT">学生</option>
              <option value="TEACHER">教师</option>
              <option value="ADMIN">管理员</option>
            </select>
          </label>
          <button
            class="admin-users__button admin-users__button--primary"
            type="submit"
            :disabled="listBusy"
          >
            搜索
          </button>
          <button
            class="admin-users__button"
            type="button"
            data-testid="admin-users-refresh"
            :disabled="listBusy"
            @click="loadUsers"
          >
            {{ loading ? '正在刷新…' : '刷新列表' }}
          </button>
        </form>
      </template>

      <p
        v-if="loading && entries.length === 0"
        class="admin-users__state"
        role="status"
        aria-live="polite"
      >
        正在读取用户列表…
      </p>
      <div
        v-if="error"
        class="admin-users__error"
        role="alert"
      >
        <span>读取失败：{{ error }}</span>
        <button
          class="admin-users__button"
          type="button"
          :disabled="listBusy"
          @click="loadUsers"
        >
          重试
        </button>
      </div>
      <p
        v-else-if="!loading && entries.length === 0"
        class="admin-users__state"
      >
        没有匹配的账号。
      </p>

      <AdminTable
        v-if="entries.length > 0"
        label="用户列表"
        :min-width="820"
      >
        <table class="admin-users__table">
          <thead>
            <tr>
              <th scope="col">
                编号
              </th>
              <th scope="col">
                显示名
              </th>
              <th scope="col">
                用户名
              </th>
              <th scope="col">
                角色
              </th>
              <th scope="col">
                状态
              </th>
              <th scope="col">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="user in entries"
              :key="user.id"
            >
              <td>{{ user.id }}</td>
              <td :title="user.displayName">
                {{ user.displayName }}
              </td>
              <td :title="user.username">
                {{ user.username }}
              </td>
              <td>{{ roleLabel(user.role) }}</td>
              <td>
                <span
                  class="admin-users__status"
                  :class="`admin-users__status--${user.status.toLowerCase()}`"
                >{{ statusLabel(user.status) }}</span>
              </td>
              <td>
                <button
                  class="admin-users__button"
                  type="button"
                  :disabled="user.id === session.user?.id || listBusy"
                  :data-testid="`admin-user-status-${user.id}`"
                  @click="toggleStatus(user, $event)"
                >
                  {{ updatingUserId === user.id ? '正在保存…' : user.status === 'ACTIVE' ? '停用' : '启用' }}
                </button>
                <span
                  v-if="user.id === session.user?.id"
                  class="admin-users__self-note"
                >当前账号</span>
              </td>
            </tr>
          </tbody>
        </table>
      </AdminTable>

      <template #footer>
        <AdminPagination
          :page="page"
          :total-pages="totalPages"
          :page-size="pageSize"
          :total-elements="totalElements"
          item-label="个账号"
          :disabled="listBusy"
          @page-change="goToPage"
          @page-size-change="changePageSize"
        />
      </template>
    </AdminPanel>

    <dialog
      v-if="statusChangeUser"
      ref="statusDialog"
      class="admin-dialog admin-users__dialog"
      aria-labelledby="admin-user-status-title"
      @cancel.prevent="dismissStatusChange()"
      @click.self="dismissStatusChange()"
    >
      <h2 id="admin-user-status-title">
        确认{{ statusChangeUser.status === 'ACTIVE' ? '停用' : '启用' }}账号
      </h2>
      <p>
        {{ statusChangeUser.displayName }}（{{ statusChangeUser.username }}）
        {{ statusChangeUser.status === 'ACTIVE' ? '停用后将撤销现有登录令牌。' : '启用后可重新登录。' }}
      </p>
      <div class="admin-dialog__actions">
        <button
          class="admin-users__button"
          type="button"
          data-testid="admin-user-status-cancel"
          :disabled="updatingUserId !== null"
          @click="dismissStatusChange()"
        >
          取消
        </button>
        <button
          class="admin-users__button admin-users__button--danger"
          type="button"
          data-testid="admin-user-status-confirm"
          :disabled="updatingUserId !== null"
          @click="confirmStatusChange"
        >
          {{ updatingUserId !== null ? '正在保存…' : '确认变更' }}
        </button>
      </div>
      <p
        v-if="statusChangeError"
        class="admin-dialog__error"
        role="alert"
      >
        {{ statusChangeError }}
      </p>
    </dialog>
  </section>
</template>

<style scoped>
.admin-users {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.admin-users__filters {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-users__search,
.admin-users__status-filter,
.admin-users__role-filter {
  display: grid;
  gap: 5px;
  color: var(--admin-muted);
  font-size: 12px;
}

.admin-users__search input,
.admin-users__status-filter select,
.admin-users__role-filter select {
  min-height: var(--admin-control-height, 36px);
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-control-radius, 8px);
  padding: 0 10px;
  background: #fff;
  color: var(--admin-text);
  font: inherit;
  font-size: 13px;
}

.admin-users__search input {
  width: min(320px, 72vw);
}

.admin-users__button {
  display: inline-flex;
  min-height: var(--admin-control-height, 36px);
  align-items: center;
  justify-content: center;
  border: 1px solid #cbd5e1;
  border-radius: var(--admin-control-radius, 8px);
  padding: 0 14px;
  background: #fff;
  color: #334155;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.admin-users__button:hover:not(:disabled) {
  border-color: var(--admin-focus);
  color: var(--admin-primary-hover);
}

.admin-users__button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-users__button--primary {
  border-color: var(--admin-primary);
  background: var(--admin-primary);
  color: #fff;
}

.admin-users__button--danger {
  border-color: #fecaca;
  background: #fff7f7;
  color: #991b1b;
}

.admin-users__button:focus-visible,
.admin-users__search input:focus-visible,
.admin-users__status-filter select:focus-visible,
.admin-users__role-filter select:focus-visible {
  outline: 3px solid var(--admin-focus);
  outline-offset: 2px;
}

.admin-users__state {
  align-self: center;
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-users__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #b91c1c;
  font-size: 13px;
}

.admin-users__table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-users__table th:nth-child(1),
.admin-users__table td:nth-child(1) { width: 72px; }
.admin-users__table th:nth-child(2),
.admin-users__table td:nth-child(2) { width: 170px; }
.admin-users__table th:nth-child(3),
.admin-users__table td:nth-child(3) { width: 190px; }
.admin-users__table th:nth-child(4),
.admin-users__table td:nth-child(4) { width: 100px; }
.admin-users__table th:nth-child(5),
.admin-users__table td:nth-child(5) { width: 110px; }
.admin-users__table th:nth-child(6),
.admin-users__table td:nth-child(6) { width: 178px; }

.admin-users__table td:nth-child(1),
.admin-users__table td:nth-child(4),
.admin-users__table td:nth-child(5),
.admin-users__table td:nth-child(6) {
  overflow: visible;
  text-overflow: clip;
}

.admin-users__status {
  display: inline-flex;
  border-radius: 999px;
  padding: 4px 9px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.admin-users__status--active {
  background: #dcfce7;
  color: #166534;
}

.admin-users__status--disabled {
  background: #fee2e2;
  color: #991b1b;
}

.admin-users__self-note {
  margin-left: 8px;
  color: var(--admin-muted);
  font-size: 11px;
  white-space: nowrap;
}

</style>
