<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { listClassroomMembers, listMyClassrooms, removeClassroomMember, updateClassroom } from '@/api/classroom';
import type { ClassroomDto } from '@/api/dto/ClassroomDto';
import type { ClassroomMemberDto } from '@/api/dto/ClassroomMemberDto';
import { fetchKcHeatmap, fetchQuestSummary, fetchResourceUsage } from '@/api/teacher';
import type { KcHeatmapEntryDto, QuestCompletionDto, ResourceUsageDto } from '@/api/dto/TeacherDashboard';
import { humanizeKcText } from '@/data/kcNames';
import AdminPanel from './AdminPanel.vue';
import AdminTable from './AdminTable.vue';

const classrooms = ref<ClassroomDto[]>([]);
const selectedId = ref<number | null>(null);
const members = ref<ClassroomMemberDto[]>([]);
const heatmap = ref<KcHeatmapEntryDto[]>([]);
const quests = ref<QuestCompletionDto[]>([]);
const usage = ref<ResourceUsageDto | null>(null);
const loading = ref(false);
const savingMemberId = ref<number | null>(null);
const editingBasics = ref(false);
const savingBasics = ref(false);
const classNameDraft = ref('');
const classDescriptionDraft = ref('');
const error = ref('');
const selected = computed(() => classrooms.value.find((item) => item.classroomId === selectedId.value) ?? null);

async function loadClasses(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    classrooms.value = await listMyClassrooms();
    if (!classrooms.value.some((item) => item.classroomId === selectedId.value)) {
      selectedId.value = classrooms.value[0]?.classroomId ?? null;
    }
    await loadClassDetail();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '班级数据暂不可用';
    classrooms.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadClassDetail(): Promise<void> {
  const id = selectedId.value;
  if (id === null) {
    members.value = [];
    heatmap.value = [];
    quests.value = [];
    usage.value = null;
    return;
  }
  const results = await Promise.allSettled([
    listClassroomMembers(id), fetchKcHeatmap(id), fetchQuestSummary(id), fetchResourceUsage(id),
  ]);
  members.value = results[0].status === 'fulfilled' ? results[0].value : [];
  heatmap.value = results[1].status === 'fulfilled' ? results[1].value : [];
  quests.value = results[2].status === 'fulfilled' ? results[2].value : [];
  usage.value = results[3].status === 'fulfilled' ? results[3].value : null;
  const failed = results.find((result) => result.status === 'rejected');
  if (failed?.status === 'rejected') error.value = failed.reason instanceof Error ? failed.reason.message : '部分班级看板暂不可用';
}

async function selectClass(id: number): Promise<void> {
  selectedId.value = id;
  editingBasics.value = false;
  error.value = '';
  await loadClassDetail();
}

function editBasics(): void {
  if (!selected.value) return;
  classNameDraft.value = selected.value.name;
  classDescriptionDraft.value = selected.value.description;
  editingBasics.value = true;
}

async function saveBasics(): Promise<void> {
  const classroom = selected.value;
  const name = classNameDraft.value.trim();
  if (!classroom || !name || savingBasics.value) return;
  savingBasics.value = true;
  error.value = '';
  try {
    const updated = await updateClassroom(classroom.classroomId, {
      name,
      description: classDescriptionDraft.value.trim(),
    });
    classrooms.value = classrooms.value.map((item) => item.classroomId === updated.classroomId ? updated : item);
    editingBasics.value = false;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '班级信息保存失败';
  } finally {
    savingBasics.value = false;
  }
}

async function removeMember(member: ClassroomMemberDto): Promise<void> {
  const id = selectedId.value;
  if (id === null || savingMemberId.value !== null || !window.confirm(`确认将 ${member.displayName} 移出班级？`)) return;
  savingMemberId.value = member.studentId;
  error.value = '';
  try {
    await removeClassroomMember(id, member.studentId);
    members.value = await listClassroomMembers(id);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '成员更新失败';
  } finally {
    savingMemberId.value = null;
  }
}

onMounted(() => void loadClasses());
</script>

<template>
  <section class="admin-classrooms" data-testid="admin-classrooms" :aria-busy="loading">
    <AdminPanel title="班级管理" description="查看全校班级、成员、任务、知识点掌握与资源使用；移出成员会保留其历史学习记录。">
      <template #actions>
        <button class="admin-button" type="button" :disabled="loading" @click="loadClasses">刷新</button>
      </template>
      <p v-if="loading && classrooms.length === 0" role="status">正在读取班级…</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <p v-if="!loading && classrooms.length === 0 && !error">当前没有班级。</p>

      <div v-if="classrooms.length" class="admin-classrooms__layout">
        <nav class="admin-classrooms__list" aria-label="班级列表">
          <button
            v-for="item in classrooms"
            :key="item.classroomId"
            class="admin-classrooms__class"
            :class="{ 'admin-classrooms__class--selected': selectedId === item.classroomId }"
            type="button"
            :aria-pressed="selectedId === item.classroomId"
            @click="selectClass(item.classroomId)"
          >
            <strong>{{ item.name }}</strong><span>{{ item.status === 'ACTIVE' ? '进行中' : '已归档' }} · 课程包 {{ item.packId }}</span>
          </button>
        </nav>

        <div v-if="selected" class="admin-classrooms__detail">
          <section class="admin-classrooms__summary" aria-label="班级信息">
            <button class="admin-button" type="button" data-testid="admin-class-edit" @click="editBasics">编辑班级信息</button>
            <div><span>班级</span><strong>{{ selected.name }}</strong></div>
            <div><span>教师账号</span><strong>{{ selected.teacherId }}</strong></div>
            <div><span>加入码</span><strong>{{ selected.joinCode || '—' }}</strong></div>
            <div><span>学生人数</span><strong>{{ members.length }}</strong></div>
            <p>{{ selected.description || '暂无班级说明' }}</p>
            <form v-if="editingBasics" class="admin-classrooms__edit" @submit.prevent="saveBasics">
              <label>班级名称<input v-model="classNameDraft" class="admin-control" maxlength="128" required data-testid="admin-class-name"></label>
              <label>班级说明<textarea v-model="classDescriptionDraft" class="admin-control" maxlength="1000" rows="3" data-testid="admin-class-description" /></label>
              <div><button class="admin-button admin-button--primary" type="submit" :disabled="savingBasics || !classNameDraft.trim()" data-testid="admin-class-save">{{ savingBasics ? '保存中…' : '保存' }}</button><button class="admin-button" type="button" :disabled="savingBasics" @click="editingBasics = false">取消</button></div>
            </form>
          </section>

          <AdminPanel title="班级成员" :description="`${selected.name} 的成员与账号状态`">
            <p v-if="members.length === 0">暂无成员。</p>
            <AdminTable v-else label="班级成员" :min-width="560">
              <table>
                <thead><tr><th scope="col">学生</th><th scope="col">用户名</th><th scope="col">状态</th><th scope="col">管理</th></tr></thead>
                <tbody>
                  <tr v-for="member in members" :key="member.studentId">
                    <td>{{ member.displayName }}</td><td>{{ member.username }}</td><td>{{ member.status === 'ACTIVE' ? '在班' : '已移出' }}</td>
                    <td><button v-if="member.status === 'ACTIVE'" class="admin-button" type="button" :disabled="savingMemberId !== null" :data-testid="`admin-remove-class-member-${member.studentId}`" @click="removeMember(member)">{{ savingMemberId === member.studentId ? '处理中…' : '移出班级' }}</button></td>
                  </tr>
                </tbody>
              </table>
            </AdminTable>
          </AdminPanel>

          <div class="admin-classrooms__dashboards">
            <AdminPanel title="知识点掌握" description="按班级汇总的真实掌握度">
              <p v-if="heatmap.length === 0">暂无知识点掌握数据。</p>
              <ul v-else><li v-for="item in heatmap" :key="item.kcCode">{{ item.kcName || humanizeKcText(item.kcCode) }}：{{ Math.round(item.avgMastery * 100) }}%（{{ item.studentCount }} 人）</li></ul>
            </AdminPanel>
            <AdminPanel title="班级任务" description="任务完成情况">
              <p v-if="quests.length === 0">暂无班级任务统计。</p>
              <ul v-else><li v-for="item in quests" :key="item.classroomQuestId">{{ item.title }}：{{ item.completedCount }}/{{ item.totalStudents }} 完成</li></ul>
            </AdminPanel>
            <AdminPanel title="资源使用" description="按当前看板时间范围统计">
              <p v-if="!usage">暂时无法读取资源使用统计。</p>
              <ul v-else><li v-for="item in usage.totals" :key="item.resourceType">{{ item.resourceType }}：{{ item.count }} 次</li><li v-if="usage.totals.length === 0">当前时间范围内暂无资源使用。</li></ul>
            </AdminPanel>
          </div>
        </div>
      </div>
    </AdminPanel>
  </section>
</template>

<style scoped>
.admin-classrooms__layout { display: grid; grid-template-columns: minmax(190px, 260px) minmax(0, 1fr); gap: 18px; }
.admin-classrooms__list { display: grid; align-content: start; gap: 8px; }
.admin-classrooms__class { display: grid; gap: 5px; padding: 12px; border: 1px solid var(--admin-panel-border); border-radius: 10px; background: white; color: var(--admin-text-strong); text-align: left; cursor: pointer; }
.admin-classrooms__class span, .admin-classrooms__summary span { color: var(--admin-text-muted); font-size: 12px; }
.admin-classrooms__class--selected { border-color: var(--admin-accent, #168f82); box-shadow: 0 0 0 2px rgb(22 143 130 / 12%); }
.admin-classrooms__detail { min-width: 0; display: grid; gap: 16px; }
.admin-classrooms__summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; padding: 16px; border: 1px solid var(--admin-panel-border); border-radius: 12px; background: #fff; }
.admin-classrooms__summary div { display: grid; gap: 4px; }
.admin-classrooms__summary p { grid-column: 1 / -1; margin: 0; color: var(--admin-text-muted); }
.admin-classrooms__edit { grid-column: 1 / -1; display: grid; gap: 10px; }
.admin-classrooms__edit label { display: grid; gap: 4px; color: var(--admin-text-muted); font-size: 12px; }
.admin-classrooms__edit div { display: flex; gap: 8px; }
.admin-classrooms__dashboards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.admin-classrooms__dashboards :deep(ul) { margin: 0; padding-left: 20px; }
@media (max-width: 860px) { .admin-classrooms__layout { grid-template-columns: 1fr; } .admin-classrooms__list { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); } }
@media (max-width: 600px) { .admin-classrooms__summary, .admin-classrooms__dashboards { grid-template-columns: 1fr; } }
</style>
