<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
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

async function toggleStatus(user: AdminUserDto): Promise<void> {
  if (user.id === session.user?.id || updatingUserId.value !== null) return;
  const status: AdminUserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  const action = status === 'DISABLED' ? '停用' : '启用';
  if (!window.confirm(`确认${action}账号“${user.displayName}（${user.username}）”？`)) return;

  updatingUserId.value = user.id;
  try {
    await setAdminUserStatus(user.id, status);
    toast.show(`账号已${action}`, 'success');
    await loadUsers();
  } catch (cause) {
    toast.show(cause instanceof Error ? cause.message : `账号${action}失败`, 'error');
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
    class="admin-users"
    data-testid="admin-users"
    :aria-busy="loading"
  >
    <header class="admin-page-heading">
      <h1>用户管理</h1>
      <p>查看账号角色与状态；停用或启用账号会撤销该账号的现有登录令牌。</p>
    </header>

    <AdminPanel
      title="平台账号"
      description="仅提供账号状态管理，密码与凭据不会出现在列表中。"
    >
      <template #actions>
        <button
          class="admin-users__button"
          type="button"
          :disabled="listBusy"
          @click="loadUsers"
        >
          {{ loading ? '正在刷新…' : '刷新列表' }}
        </button>
      </template>

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
      </form>

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
                  @click="toggleStatus(user)"
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
  </section>
</template>

<style scoped>
.admin-users {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.admin-page-heading h1 {
  margin: 0;
  color: var(--admin-text);
  font-size: 26px;
  line-height: 1.3;
}

.admin-page-heading p {
  margin: 7px 0 0;
  color: var(--admin-muted);
  font-size: 14px;
}

.admin-users__filters {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
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
  min-height: 36px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
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
  min-height: 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
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

@media (max-width: 640px) {
  .admin-page-heading h1 { font-size: 22px; }
}
</style>
