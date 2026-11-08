<template>
  <p v-if="!classroomId" class="notice">请先创建或选择一个班级。</p>
  <section v-else class="student-accounts">
    <div class="table-toolbar">
      <label>搜索学号或姓名<input v-model="search" type="search" /></label>
      <Button :disabled="busy" @click="createOpen = true">按学号创建</Button>
      <Button :disabled="busy" variant="outline" @click="importOpen = true">批量导入</Button>
      <Button :disabled="busy || !selected.length" variant="outline" @click="action = 'reset-password'">重置密码（{{ selected.length }}）</Button>
      <Button :disabled="busy || !selected.length" variant="destructive" @click="action = 'remove'">移出班级（{{ selected.length }}）</Button>
    </div>
    <p>初始和重置密码均为学生本人的学号；首次登录必须修改。移出班级保留账号和学习记录。</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="loading" role="status">正在读取学生…</p>
    <Table v-else>
      <TableHeader><TableRow><TableHead><input type="checkbox" aria-label="选择当前筛选的学生" :checked="filtered.length > 0 && filtered.every(s => selected.includes(s.student_id))" :disabled="busy || filtered.length > 500" @change="toggleAll" /></TableHead><TableHead>学号</TableHead><TableHead>姓名</TableHead><TableHead>密码状态</TableHead></TableRow></TableHeader>
      <TableBody><TableRow v-for="student in filtered" :key="student.id"><TableCell><input v-model="selected" type="checkbox" :value="student.student_id" :aria-label="`选择${student.student_id}`" :disabled="busy || !student.student_id" /></TableCell><TableCell>{{ student.student_id || '未分配学号' }}</TableCell><TableCell>{{ student.name }}</TableCell><TableCell>{{ student.must_change_password ? '待修改初始密码' : '已设置' }}</TableCell></TableRow></TableBody>
    </Table>
    <p v-if="!loading && !filtered.length" class="admin-empty">没有匹配的学生</p>
    <AdminDialog v-model:open="createOpen" title="创建学生账号" description="创建后自动加入当前班级，初始密码为学号。">
      <form class="teacher-form" @submit.prevent="create([{ student_id: studentId, name }])">
        <label>学号<input v-model="studentId" inputmode="numeric" pattern="[0-9]{1,32}" maxlength="32" required :disabled="busy" /></label>
        <label>姓名<input v-model="name" maxlength="80" required :disabled="busy" /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <Button type="submit" :disabled="busy">{{ busy ? '正在创建…' : '创建账号' }}</Button>
      </form>
    </AdminDialog>
    <AdminDialog v-model:open="importOpen" title="批量创建学生" description="上传UTF-8 CSV或Excel .xlsx；只保留一个工作表，首行为“学号、姓名”。每次最多500人、2MB。" wide>
      <div class="teacher-form">
        <p>Excel学号列必须设为文本，保留前导零。同班已有学生将跳过，不覆盖姓名和密码；其他已有账号须先使用加入码加入本班。</p>
        <a :href="templateUrl" download="学生导入模板.csv">下载CSV模板</a>
        <label>选择学生表格<input type="file" accept=".xlsx,.csv" :disabled="busy" @change="preview" /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div v-if="previewRows.length" class="import-preview">
          <p>共{{ previewRows.length }}人：新建{{ previewRows.filter(r => r.action === 'create').length }}人，跳过{{ previewRows.filter(r => r.action === 'existing').length }}人。</p>
          <Table><TableHeader><TableRow><TableHead>学号</TableHead><TableHead>姓名</TableHead><TableHead>处理</TableHead></TableRow></TableHeader><TableBody><TableRow v-for="row in previewRows" :key="row.student_id"><TableCell>{{ row.student_id }}</TableCell><TableCell>{{ row.name }}</TableCell><TableCell>{{ row.action === 'create' ? '创建' : '跳过已有学生' }}</TableCell></TableRow></TableBody></Table>
        </div>
        <Button :disabled="busy || !previewRows.length" @click="create(previewRows.map(r => ({ student_id: r.student_id, name: r.name })))">{{ busy ? '正在处理…' : '确认批量创建' }}</Button>
      </div>
    </AdminDialog>
    <AdminDialog :open="!!action" :title="action === 'remove' ? '移出班级' : '重置学生密码'" :description="action === 'remove' ? `将选中的${selected.length}名学生移出当前班级，保留账号、其他班级和学习记录。` : `将选中的${selected.length}名学生密码重置为各自学号，所有旧登录立即失效；下次登录必须修改密码。`" @update:open="value => { if (!value && !busy) action = null; }">
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="form-actions"><Button variant="outline" :disabled="busy" @click="action = null">取消</Button><Button variant="destructive" :disabled="busy" @click="batch">{{ busy ? '正在处理…' : '确认操作' }}</Button></div>
    </AdminDialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Button } from "../components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import AdminDialog from "../components/admin/AdminDialog.vue";
import { api } from "../shared/api";
const props = defineProps<{ classroomId: number | null }>();
const emit = defineEmits<{ changed: [] }>();
interface Student { id: number; student_id: string; name: string; must_change_password: boolean }
interface Preview { student_id: string; name: string; action: "create" | "existing" }
const students = ref<Student[]>([]), selected = ref<string[]>([]), search = ref(""), error = ref(""), message = ref(""), loading = ref(false), busy = ref(false);
const createOpen = ref(false), importOpen = ref(false), studentId = ref(""), name = ref(""), previewRows = ref<Preview[]>([]), action = ref<"reset-password" | "remove" | null>(null);
const filtered = computed(() => students.value.filter(s => `${s.student_id} ${s.name}`.includes(search.value.trim())));
const base = () => `/teacher/classrooms/${props.classroomId}/students`;
const templateUrl = `data:text/csv;charset=utf-8,${encodeURIComponent('\uFEFF学号,姓名\r\n')}`;
function toggleAll(event: Event) { selected.value = (event.target as HTMLInputElement).checked ? filtered.value.map(s => s.student_id).filter(Boolean).slice(0, 500) : []; }
async function load() {
  if (!props.classroomId) return;
  loading.value = true;
  try { students.value = await api.get<Student[]>(base()); }
  catch (e) { error.value = e instanceof Error ? e.message : "加载失败"; }
  finally { loading.value = false; }
}
async function create(rows: { student_id: string; name: string }[]) {
  if (busy.value) return;
  busy.value = true; error.value = "";
  try {
    const result = await api.post<{ created: number; existing: number }>(base(), { students: rows });
    message.value = `已创建${result.created}名学生，跳过${result.existing}名已有学生。`;
    createOpen.value = false; importOpen.value = false; previewRows.value = []; studentId.value = ""; name.value = "";
    await load(); emit("changed");
  } catch (e) { error.value = e instanceof Error ? e.message : "创建失败"; }
  finally { busy.value = false; }
}
async function preview(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]; previewRows.value = []; error.value = "";
  if (!file || busy.value) return;
  if (file.size > 2 * 1024 * 1024) { error.value = "文件不能超过2MB"; return; }
  busy.value = true;
  try {
    const form = new FormData(); form.append("file", file);
    const result = await api.request<{ students: Preview[] }>(`${base()}/import-preview`, { method: "POST", body: form });
    previewRows.value = result.students;
  } catch (e) { error.value = e instanceof Error ? e.message : "导入失败"; }
  finally { busy.value = false; }
}
async function batch() {
  if (busy.value || !action.value) return;
  busy.value = true; error.value = "";
  try {
    const result = await api.post<{ affected: number }>(`${base()}/batch`, { student_ids: selected.value, action: action.value });
    message.value = `已处理${result.affected}名学生。`; selected.value = []; action.value = null;
    await load(); emit("changed");
  } catch (e) { error.value = e instanceof Error ? e.message : "操作失败"; }
  finally { busy.value = false; }
}
onMounted(load);
</script>
<style scoped>
.student-accounts { display:grid; gap:16px; }
.import-preview { max-height:320px; overflow:auto; }
</style>
