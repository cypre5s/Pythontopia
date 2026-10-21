<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { fetchAdminStudentDetail, type AdminStudentDetailDto } from '@/api/adminUsers';
import AdminPanel from './AdminPanel.vue';
import AdminTable from './AdminTable.vue';

const DIMENSIONS = [
  ['professional_background', '专业背景'],
  ['knowledge_base', '知识基础'],
  ['learning_goal', '学习目标'],
  ['cognitive_style', '认知风格'],
  ['learning_preference', '学习偏好'],
  ['error_pattern', '易错模式'],
  ['practical_ability', '实践能力'],
  ['emotion_persistence', '情绪与坚持度'],
] as const;
const route = useRoute();
const detail = ref<AdminStudentDetailDto | null>(null);
const loading = ref(false);
const error = ref('');
const dimensionRows = computed(() => DIMENSIONS.map(([key, label]) => ({
  key,
  label,
  value: detail.value?.profileDimensions[key],
})));

const ACTIVITY_LABELS: Record<string, string> = {
  submission_passed: '练习通过',
  submission_failed: '练习未通过',
  resource_consumed: '学习资源已查看',
  kc_mastery_reached: '知识点掌握度更新',
  building_visited: '访问学习地点',
  dialog_complete: '完成对话',
};

function percent(value: number | null | undefined): string {
  return typeof value === 'number' ? `${Math.round(value * 100)}%` : '暂无可用数据';
}

function date(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '—';
}

function activityLabel(eventType: string): string {
  return ACTIVITY_LABELS[eventType] ?? '学习活动';
}

function judgeLabel(verdict: string | null): string {
  if (!verdict) return '判题未完成';
  if (verdict === 'AC') return '通过';
  if (verdict === 'WA') return '答案错误';
  if (verdict === 'RE') return '运行错误';
  if (verdict === 'TLE') return '运行超时';
  return '未通过';
}

async function load(): Promise<void> {
  const userId = Number(route.params.userId);
  if (!Number.isSafeInteger(userId) || userId < 1) {
    error.value = '学生账号编号无效';
    detail.value = null;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    detail.value = await fetchAdminStudentDetail(userId);
  } catch (cause) {
    detail.value = null;
    error.value = cause instanceof Error ? cause.message : '学生详情暂不可用';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
watch(() => route.params.userId, () => void load());
</script>

<template>
  <section class="admin-student-detail" data-testid="admin-student-detail" :aria-busy="loading">
    <AdminPanel title="学生详情" description="仅展示真实档案、学习进度、服务端判题结果和班级归属。">
      <template #actions>
        <RouterLink class="admin-button" :to="{ name: 'admin-users' }">返回账号列表</RouterLink>
      </template>

      <p v-if="loading" class="admin-student-detail__state" role="status">正在读取学生详情…</p>
      <p v-else-if="error" class="admin-student-detail__error" role="alert">读取失败：{{ error }}</p>
      <template v-else-if="detail">
        <section class="admin-student-detail__section" aria-labelledby="student-account-title">
          <h3 id="student-account-title">账号信息</h3>
          <dl class="admin-student-detail__account">
            <div><dt>显示名</dt><dd>{{ detail.account.displayName }}</dd></div>
            <div><dt>用户名</dt><dd>{{ detail.account.username }}</dd></div>
            <div><dt>状态</dt><dd>{{ detail.account.status === 'ACTIVE' ? '正常' : '已停用' }}</dd></div>
            <div><dt>注册时间</dt><dd>{{ date(detail.account.createdAt) }}</dd></div>
          </dl>
        </section>

        <section class="admin-student-detail__section" aria-labelledby="student-profile-title">
          <h3 id="student-profile-title">八维学习画像</h3>
          <div class="admin-student-detail__dimensions">
            <article v-for="item in dimensionRows" :key="item.key" class="admin-student-detail__dimension">
              <h4>{{ item.label }}</h4>
              <strong>{{ percent(item.value?.score) }}</strong>
              <span>{{ item.value?.evidence_count ?? 0 }} 条画像证据</span>
            </article>
          </div>
        </section>

        <section class="admin-student-detail__section" aria-labelledby="student-mastery-title">
          <h3 id="student-mastery-title">知识点掌握进度</h3>
          <p v-if="detail.mastery.length === 0" class="admin-student-detail__state">暂无掌握度记录</p>
          <AdminTable v-else label="知识点掌握进度" :min-width="580">
            <table>
              <thead><tr><th scope="col">知识点</th><th scope="col">章节</th><th scope="col">掌握度</th><th scope="col">置信度</th><th scope="col">最近更新</th></tr></thead>
              <tbody>
                <tr v-for="(item, index) in detail.mastery" :key="`${item.kcName ?? 'kc'}-${index}`">
                  <td>{{ item.kcName || '相关知识点' }}</td>
                  <td>{{ item.chapterOrder ? `第 ${item.chapterOrder} 章` : '—' }}</td>
                  <td>{{ percent(item.masteryScore) }}</td>
                  <td>{{ percent(item.confidence) }}</td>
                  <td>{{ date(item.updatedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </AdminTable>
        </section>

        <section class="admin-student-detail__section" aria-labelledby="student-submissions-title">
          <h3 id="student-submissions-title">正式练习与 Judge 结果</h3>
          <p v-if="detail.submissions.length === 0" class="admin-student-detail__state">暂无正式提交记录</p>
          <AdminTable v-else label="正式练习与 Judge 结果" :min-width="560">
            <table>
              <thead><tr><th scope="col">题目</th><th scope="col">提交状态</th><th scope="col">Judge</th><th scope="col">测试点</th><th scope="col">提交时间</th></tr></thead>
              <tbody>
                <tr v-for="item in detail.submissions" :key="item.id">
                  <td>{{ item.problemTitle || '课程练习' }}</td>
                  <td>{{ item.status }}</td>
                  <td>{{ judgeLabel(item.judgeVerdict) }}</td>
                  <td>{{ item.passedCases }} / {{ item.totalCases }}</td>
                  <td>{{ date(item.submittedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </AdminTable>
        </section>

        <section class="admin-student-detail__section" aria-labelledby="student-activity-title">
          <h3 id="student-activity-title">最近学习活动</h3>
          <ul v-if="detail.activity.length" class="admin-student-detail__activity">
            <li v-for="item in detail.activity" :key="item.id">
              <span>{{ activityLabel(item.eventType) }}</span><time>{{ date(item.occurredAt) }}</time>
            </li>
          </ul>
          <p v-else class="admin-student-detail__state">暂无活动记录</p>
        </section>

        <section class="admin-student-detail__section" aria-labelledby="student-classroom-title">
          <h3 id="student-classroom-title">所在班级</h3>
          <ul v-if="detail.classrooms.length" class="admin-student-detail__classrooms">
            <li v-for="item in detail.classrooms" :key="item.classroomId">
              <strong>{{ item.classroomName }}</strong><span>教师：{{ item.teacherName }}</span>
            </li>
          </ul>
          <p v-else class="admin-student-detail__state">未加入班级</p>
        </section>
      </template>
    </AdminPanel>
  </section>
</template>

<style scoped>
.admin-student-detail { display: grid; gap: 20px; min-width: 0; }
.admin-student-detail__section { display: grid; gap: 12px; padding-top: 18px; border-top: 1px solid var(--admin-border); }
.admin-student-detail__section h3 { margin: 0; color: var(--admin-text); font-size: 15px; }
.admin-student-detail__account { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin: 0; }
.admin-student-detail__account div, .admin-student-detail__dimension { display: grid; gap: 5px; padding: 12px; border: 1px solid var(--admin-border); border-radius: 10px; background: var(--admin-surface-muted); }
.admin-student-detail__account dt, .admin-student-detail__dimension span { color: var(--admin-text-muted); font-size: 12px; }
.admin-student-detail__account dd { margin: 0; color: var(--admin-text); overflow-wrap: anywhere; }
.admin-student-detail__dimensions { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
.admin-student-detail__dimension h4 { margin: 0; color: var(--admin-text-muted); font-size: 12px; font-weight: 500; }
.admin-student-detail__dimension strong { color: var(--admin-accent); font-size: 20px; }
.admin-student-detail__activity, .admin-student-detail__classrooms { display: grid; gap: 8px; padding: 0; margin: 0; list-style: none; }
.admin-student-detail__activity li, .admin-student-detail__classrooms li { display: flex; justify-content: space-between; gap: 12px; padding: 10px 12px; border-bottom: 1px solid var(--admin-border); }
.admin-student-detail__activity time, .admin-student-detail__classrooms span { color: var(--admin-text-muted); font-size: 12px; }
.admin-student-detail__state { color: var(--admin-text-muted); }
.admin-student-detail__error { color: var(--admin-danger); }
@media (max-width: 640px) { .admin-student-detail__activity li, .admin-student-detail__classrooms li { flex-direction: column; } }
</style>
