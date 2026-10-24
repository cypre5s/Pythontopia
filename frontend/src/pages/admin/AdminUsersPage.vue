<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useSessionStore } from '@stores/session';
import { useToastStore } from '@stores/toast';
import {
  importAdminStudents,
  searchAdminUsers,
  setAdminUserStatus,
  type AdminStudentImportResultDto,
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
const importing = ref(false);
const importError = ref<string | null>(null);
const importResult = ref<AdminStudentImportResultDto | null>(null);
const statusChangeError = ref<string | null>(null);
const listBusy = computed(() => loading.value || updatingUserId.value !== null || importing.value);
const pageSize = ref(20);
const pendingStatusChange = ref<{
  user: AdminUserDto;
  status: AdminUserStatus;
  action: string;
} | null>(null);
const usersPage = ref<HTMLElement | null>(null);
const statusDialog = ref<HTMLDialogElement | null>(null);
const statusTrigger = ref<HTMLButtonElement | null>(null);

function roleLabel(role: AdminUserDto['role']): string {
  if (role === 'ADMIN') return '管理员';
  if (role === 'TEACHER') return '教师';
  return '学生';
}

function statusLabel(status: AdminUserStatus): string {
  return status === 'ACTIVE' ? '正常' : '已停用';
}

function statusTone(status: AdminUserStatus): string {
  return status === 'ACTIVE' ? 'admin-status--positive' : 'admin-status--danger';
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
    page.value = 0;
    totalPages.value = 0;
    totalElements.value = 0;
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

function openStatusConfirmation(user: AdminUserDto, event: MouseEvent): void {
  if (user.id === session.user?.id || updatingUserId.value !== null) return;
  statusChangeError.value = null;
  const status: AdminUserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  const action = status === 'DISABLED' ? '停用' : '启用';
  statusTrigger.value = event.currentTarget as HTMLButtonElement;
  pendingStatusChange.value = { user, status, action };
  void nextTick(() => {
    statusDialog.value?.showModal();
    statusDialog.value?.querySelector<HTMLButtonElement>('[data-testid="admin-user-status-cancel"]')?.focus();
  });
}

function closeStatusConfirmation(restoreTrigger = true): void {
  if (updatingUserId.value !== null) return;
  statusDialog.value?.close();
  pendingStatusChange.value = null;
  statusChangeError.value = null;
  void nextTick(() => {
    if (restoreTrigger && statusTrigger.value?.isConnected) statusTrigger.value.focus();
    else usersPage.value?.focus();
  });
}

async function confirmStatusChange(): Promise<void> {
  const pending = pendingStatusChange.value;
  if (!pending || updatingUserId.value !== null) return;

  statusChangeError.value = null;
  updatingUserId.value = pending.user.id;
  try {
    await setAdminUserStatus(pending.user.id, pending.status);
    toast.show(`账号已${pending.action}`, 'success');
    updatingUserId.value = null;
    closeStatusConfirmation(false);
    await loadUsers();
  } catch (cause) {
    statusChangeError.value = cause instanceof Error ? cause.message : `账号${pending.action}失败`;
    toast.show(cause instanceof Error ? cause.message : `账号${pending.action}失败`, 'error');
  } finally {
    updatingUserId.value = null;
  }
}

async function importRoster(event: Event): Promise<void> {
  const input = event.currentTarget as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || importing.value) return;

  importError.value = null;
  importResult.value = null;
  importing.value = true;
  let imported = false;
  try {
    importResult.value = await importAdminStudents(file);
    imported = true;
    toast.show(
      `已新建 ${importResult.value.createdCount} 个学生账号，跳过 ${importResult.value.skippedCount} 个已有账号`,
      'success',
    );
  } catch (cause) {
    importError.value = cause instanceof Error ? cause.message : '名单导入失败';
    toast.show(importError.value, 'error');
  } finally {
    importing.value = false;
    input.value = '';
  }
  if (imported) await loadUsers();
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
    :aria-busy="listBusy"
  >
    <AdminPanel
      title="用户管理"
      description="查看账号角色与状态并管理登录资格；导入结果会显示新建账号的初始凭据。"
    >
      <template #actions>
        <div class="admin-users__import-tools">
          <label
            class="admin-button admin-users__import-button"
            :class="{ 'admin-users__import-button--disabled': listBusy }"
          >
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              aria-label="导入学生名单"
              data-testid="admin-student-roster"
              :disabled="listBusy"
              @change="importRoster"
            >
            <span>导入学生名单</span>
          </label>
          <span class="admin-users__help">
            <button
              class="admin-users__help-button"
              type="button"
              aria-label="查看推荐的 XLSX 名单格式"
              aria-describedby="admin-student-roster-tooltip"
              data-testid="admin-student-roster-help"
            >
              !
            </button>
            <span
              id="admin-student-roster-tooltip"
              class="admin-users__tooltip"
              role="tooltip"
            >
              <strong>推荐的 XLSX 格式</strong>
              <span>首个工作表建议使用以下表头，列顺序可以调整：</span>
              <table aria-label="推荐学生名单格式">
                <thead>
                  <tr>
                    <th scope="col">学号</th>
                    <th scope="col">姓名</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>20260001</td>
                    <td>张小明</td>
                  </tr>
                  <tr>
                    <td>20260002</td>
                    <td>李小红</td>
                  </tr>
                </tbody>
              </table>
              <span>按完整表头识别：学号 / 学生学号 / Student ID / studentid / student_id；姓名 / 学生姓名 / Name。其他列会忽略；若同类候选列超过一列，会提示错误。</span>
            </span>
          </span>
        </div>
        <button
          class="admin-button"
          type="button"
          data-testid="admin-users-refresh"
          :disabled="listBusy"
          @click="loadUsers"
        >
          {{ loading ? '正在刷新…' : '刷新列表' }}
        </button>
      </template>

      <template #toolbar>
        <form
          class="admin-users__filters"
          data-testid="admin-users-search-form"
          @submit.prevent="searchUsers"
        >
          <label class="admin-users__search">
            <span>搜索账号或显示名</span>
            <input
              v-model="searchInput"
              class="admin-control"
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
              class="admin-control"
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
              class="admin-control"
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
            class="admin-button admin-button--primary"
            type="submit"
            :disabled="listBusy"
          >
            搜索
          </button>
        </form>
      </template>

      <div
        v-if="importError"
        class="admin-users__error"
        role="alert"
      >
        <span>导入失败：{{ importError }}</span>
      </div>

      <section
        v-if="importResult"
        class="admin-users__import-result"
        aria-live="polite"
        data-testid="admin-student-import-result"
      >
        <h3>导入完成：新建 {{ importResult.createdCount }} 个学生账号，跳过 {{ importResult.skippedCount }} 个已有账号</h3>
        <p v-if="importResult.credentials.length > 0">
          请保存并分发以下新账号的初始登录凭据。
        </p>
        <p v-else>
          没有新建账号，因此没有新的登录凭据。
        </p>
        <AdminTable
          v-if="importResult.credentials.length > 0"
          label="本次新建学生的登录凭据"
          :min-width="560"
        >
          <table
            class="admin-users__table"
            aria-label="本次新建学生的登录凭据"
          >
            <thead>
              <tr>
                <th scope="col">
                  显示名
                </th>
                <th scope="col">
                  学号 / 用户名
                </th>
                <th scope="col">
                  初始密码
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="credential in importResult.credentials"
                :key="credential.username"
              >
                <td class="admin-users__readable">
                  {{ credential.displayName }}
                </td>
                <td class="admin-users__readable">
                  {{ credential.username }}
                </td>
                <td class="admin-users__readable">
                  {{ credential.password }}
                </td>
              </tr>
            </tbody>
          </table>
        </AdminTable>
      </section>

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
              <th scope="col">编号</th>
              <th scope="col">显示名</th>
              <th scope="col">用户名</th>
              <th scope="col">角色</th>
              <th scope="col">状态</th>
              <th scope="col" class="admin-table__sticky">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in entries" :key="user.id">
              <td>{{ user.id }}</td>
              <td class="admin-users__readable">
                <RouterLink
                  v-if="user.role === 'STUDENT'"
                  :to="{ name: 'admin-student-detail', params: { userId: user.id } }"
                  :data-testid="`admin-user-detail-${user.id}`"
                >
                  {{ user.displayName }}
                </RouterLink>
                <span v-else>{{ user.displayName }}</span>
              </td>
              <td class="admin-users__readable">{{ user.username }}</td>
              <td>{{ roleLabel(user.role) }}</td>
              <td>
                <span
                  class="admin-status admin-users__status"
                  :class="[
                    `admin-users__status--${user.status.toLowerCase()}`,
                    statusTone(user.status),
                  ]"
                >{{ statusLabel(user.status) }}</span>
              </td>
              <td class="admin-table__sticky">
                <button
                  class="admin-button"
                  type="button"
                  :disabled="user.id === session.user?.id || listBusy"
                  :data-testid="`admin-user-status-${user.id}`"
                  @click="openStatusConfirmation(user, $event)"
                >
                  {{ updatingUserId === user.id ? '正在保存…' : user.status === 'ACTIVE' ? '停用' : '启用' }}
                </button>
                <span v-if="user.id === session.user?.id" class="admin-users__self-note">当前账号</span>
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
      v-if="pendingStatusChange"
      ref="statusDialog"
      class="admin-dialog admin-users__dialog"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="admin-user-status-title"
      aria-describedby="admin-user-status-description"
      @cancel.prevent="closeStatusConfirmation()"
      @click.self="closeStatusConfirmation()"
    >
      <h2 id="admin-user-status-title">确认{{ pendingStatusChange.action }}账号</h2>
      <p id="admin-user-status-description">
        {{ pendingStatusChange.user.displayName }}（{{ pendingStatusChange.user.username }}）
        {{ pendingStatusChange.status === 'DISABLED' ? '将无法继续使用当前登录令牌。' : '将恢复登录权限。' }}
      </p>
      <p v-if="statusChangeError" role="alert">{{ statusChangeError }}</p>
      <div class="admin-users__dialog-actions">
        <button
          class="admin-button"
          type="button"
          data-testid="admin-user-status-cancel"
          :disabled="updatingUserId !== null"
          @click="closeStatusConfirmation()"
        >
          取消
        </button>
        <button
          class="admin-button"
          :class="pendingStatusChange.status === 'DISABLED' ? 'admin-button--danger' : 'admin-button--primary'"
          type="button"
          data-testid="admin-user-status-confirm"
          :disabled="updatingUserId !== null"
          @click="confirmStatusChange"
        >
          {{ updatingUserId === null ? `确认${pendingStatusChange.action}` : '正在保存…' }}
        </button>
      </div>
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
  gap: 10px;
  flex-wrap: wrap;
  width: 100%;
}

.admin-users__search,
.admin-users__status-filter,
.admin-users__role-filter {
  display: grid;
  gap: 4px;
  color: var(--admin-text-muted);
  font-size: 11px;
}

.admin-users__import-tools {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.admin-users :deep(.admin-panel__actions) {
  position: relative;
}

.admin-users__import-button {
  position: relative;
  overflow: hidden;
  min-height: var(--control-height-md);
  cursor: pointer;
}

.admin-users__import-button input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
  opacity: 0;
}

.admin-users__import-button input:focus-visible + span {
  outline: 2px solid var(--admin-focus);
  outline-offset: -3px;
}

.admin-users__import-button--disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.admin-users__help {
  display: inline-flex;
}

.admin-users__help-button {
  display: inline-grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border: 1px solid #94a3b8;
  border-radius: 50%;
  background: #f8fafc;
  color: #475569;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  cursor: help;
}

.admin-users__help-button:focus-visible {
  outline: 2px solid var(--admin-focus);
  outline-offset: 2px;
}

.admin-users__tooltip {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  right: 0;
  display: none;
  width: min(360px, calc(100vw - 48px));
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--admin-panel-border);
  border-radius: 10px;
  background: var(--admin-panel-bg);
  box-shadow: 0 10px 28px rgb(15 23 42 / 18%);
  color: var(--admin-text-strong);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  white-space: normal;
}

.admin-users__tooltip strong,
.admin-users__tooltip span {
  display: block;
}

.admin-users__help:hover .admin-users__tooltip,
.admin-users__help:focus-within .admin-users__tooltip {
  display: grid;
}

.admin-users__tooltip table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.admin-users__tooltip th,
.admin-users__tooltip td {
  padding: 5px 8px;
  border: 1px solid var(--admin-panel-border);
}

.admin-users__tooltip th {
  background: #f1f5f9;
  font-weight: 700;
}

.admin-users__import-result {
  display: grid;
  gap: 10px;
}

.admin-users__import-result h3,
.admin-users__import-result p {
  margin: 0;
}

.admin-users__import-result p {
  color: var(--admin-text-muted);
  font-size: 13px;
}

.admin-users__search input {
  width: min(320px, 72vw);
}

.admin-users__filters > button {
  align-self: end;
}

.admin-users__state {
  align-self: center;
  color: var(--admin-text-muted);
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
  text-align: center;
}

.admin-users__table td.admin-users__readable {
  overflow-wrap: anywhere;
  white-space: normal;
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

.admin-users__self-note {
  margin-left: 8px;
  color: var(--admin-text-muted);
  font-size: 11px;
  white-space: nowrap;
}

.admin-users__dialog > p {
  margin: 12px 0 20px;
  color: var(--admin-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.admin-users__dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

</style>
