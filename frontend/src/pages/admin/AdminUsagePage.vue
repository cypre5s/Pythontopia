<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchAdminUsage, type AdminUsageDto, type AdminUsageEventDto } from '@/api/adminUsage';
import AdminPanel from './AdminPanel.vue';
import AdminTable from './AdminTable.vue';

const days = ref(30);
const usernameInput = ref('');
const username = ref('');
const usage = ref<AdminUsageDto | null>(null);
const loading = ref(false);
const error = ref('');

const eventLabels: Record<AdminUsageEventDto['eventType'], string> = {
  LOOP_RUN: 'AI 学习闭环',
  RESOURCE_RUN: 'AI 资源生成',
  MEDIA_JOB: '媒体任务',
  FORMAL_JUDGE: '正式判题',
};

async function load(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    usage.value = await fetchAdminUsage({ days: days.value, ...(username.value ? { username: username.value } : {}) });
  } catch (cause) {
    usage.value = null;
    error.value = cause instanceof Error ? cause.message : '资源使用记录暂不可用';
  } finally {
    loading.value = false;
  }
}

function search(): void {
  username.value = usernameInput.value.trim();
  void load();
}

function date(value: string): string {
  return new Date(value).toLocaleString('zh-CN');
}

onMounted(() => void load());
</script>

<template>
  <section class="admin-usage" data-testid="admin-usage" :aria-busy="loading">
    <AdminPanel title="资源消耗管理" description="汇总已有的 AI 闭环、资源生成、媒体任务和正式 Judge 记录。历史未采集 token 的记录保持未知，不统计历史费用。">
      <template #toolbar>
        <label><span>时间范围</span>
          <select v-model.number="days" class="admin-control" data-testid="admin-usage-days">
            <option :value="7">近 7 天</option><option :value="30">近 30 天</option><option :value="90">近 90 天</option><option :value="365">近 365 天</option>
          </select>
        </label>
        <label><span>用户名</span><input v-model="usernameInput" class="admin-control" maxlength="64" data-testid="admin-usage-username"></label>
        <button class="admin-button admin-button--primary" type="button" data-testid="admin-usage-search" :disabled="loading" @click="search">查询</button>
      </template>

      <p v-if="loading && !usage" role="status">正在读取资源使用记录…</p>
      <p v-if="error" role="alert">读取失败：{{ error }}</p>
      <template v-if="usage">
        <div class="admin-usage__stats" aria-label="资源消耗汇总">
          <article><span>匹配记录</span><strong>{{ usage.totalEvents }}</strong></article>
          <article><span>已记录输入 token</span><strong>{{ usage.inputTokens.toLocaleString() }}</strong></article>
          <article><span>已记录输出 token</span><strong>{{ usage.outputTokens.toLocaleString() }}</strong></article>
          <article><span>token 未采集</span><strong>{{ usage.unknownTokenEvents }}</strong></article>
        </div>
        <AdminTable label="资源使用历史" :min-width="880">
          <table>
            <thead><tr><th scope="col">时间</th><th scope="col">账号</th><th scope="col">类型</th><th scope="col">记录</th><th scope="col">状态</th><th scope="col">输入 token</th><th scope="col">输出 token</th></tr></thead>
            <tbody>
              <tr v-for="event in usage.content" :key="`${event.eventType}-${event.id}`">
                <td>{{ date(event.occurredAt) }}</td><td>{{ event.username }}</td><td>{{ eventLabels[event.eventType] }}</td><td>{{ event.label }}</td><td>{{ event.status }}</td>
                <td>{{ event.inputTokens === null ? '未采集' : event.inputTokens.toLocaleString() }}</td><td>{{ event.outputTokens === null ? '未采集' : event.outputTokens.toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
        </AdminTable>
        <p v-if="usage.content.length === 0" class="admin-usage__empty">所选范围内没有匹配记录。</p>
        <p v-if="usage.totalEvents > usage.content.length" class="admin-usage__note">当前仅展示最近 500 条；上方汇总按筛选后的全部记录计算。</p>
      </template>
    </AdminPanel>
    <p class="admin-usage__footnote">token 统计只取后端闭环记录中的实际计数；其他历史事件没有可追溯 token 数据，因此显示“未采集”。</p>
  </section>
</template>

<style scoped>
.admin-usage__stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
.admin-usage__stats article { display: grid; gap: 5px; padding: 14px; border: 1px solid var(--admin-panel-border); border-radius: 10px; background: #fff; }
.admin-usage__stats span, .admin-usage__footnote, .admin-usage__note { color: var(--admin-text-muted); font-size: 13px; }
.admin-usage__stats strong { color: var(--admin-text-strong); font-size: 22px; }
.admin-usage__empty { color: var(--admin-text-muted); text-align: center; }
@media (max-width: 700px) { .admin-usage__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
