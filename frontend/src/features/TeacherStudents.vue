<template>
  <p v-if="!classroomId" class="notice">请先创建或选择一个班级。</p>
  <section v-else class="student-accounts">
    <div class="students-heading">
      <p class="students-summary">管理本班 <strong>{{ students.length }}</strong> 名学生的账号与访问权限</p>
      <div class="students-actions"><Button variant="outline" :disabled="busy" @click="importOpen = true"><Upload :size="16" />批量导入</Button><Button :disabled="busy" @click="createOpen = true"><Plus :size="16" />按学号创建</Button></div>
    </div>
    <section class="admin-panel students-panel" aria-label="学生名册">
      <div class="students-toolbar">
        <label class="search-field"><Search :size="16" /><span class="sr-only">搜索学号或姓名</span><input v-model="search" type="search" placeholder="搜索学号或姓名…" /></label>
        <label><span class="sr-only">密码状态筛选</span><select v-model="status"><option value="all">全部密码状态</option><option value="pending">待修改初始密码</option><option value="ready">已设置密码</option></select></label>
        <label><span class="sr-only">学生排序</span><select v-model="sort"><option value="id">按学号排序</option><option value="name">按姓名排序</option></select></label>
        <Button variant="ghost" size="icon" aria-label="刷新学生列表" :disabled="busy || loading" @click="load"><RefreshCw :size="16" /></Button>
      </div>
      <div v-if="selected.length" class="students-selection" role="region" aria-label="批量操作">
        <span>已选择 <strong>{{ selected.length }}</strong> 名学生</span>
        <Button v-if="filtered.length > selected.length && filtered.length <= 500" variant="ghost" size="sm" :disabled="busy" @click="selected = filtered.map(s => s.student_id).filter(Boolean)">选择全部筛选结果（{{ filtered.length }}）</Button>
        <Button variant="ghost" size="sm" :disabled="busy" @click="selected = []">取消选择</Button>
        <div class="students-actions"><Button :disabled="busy" variant="outline" size="sm" @click="action = 'reset-password'"><KeyRound :size="14" />重置密码（{{ selected.length }}）</Button><Button :disabled="busy" variant="destructive" size="sm" @click="action = 'remove'"><UserMinus :size="14" />移出班级（{{ selected.length }}）</Button></div>
      </div>
      <p v-if="error" class="error students-feedback" role="alert">{{ error }}</p>
      <p v-if="message" class="students-feedback" role="status">{{ message }}</p>
      <div class="students-table" :aria-busy="loading">
        <Table>
          <TableHeader><TableRow><TableHead class="student-selection-cell"><input type="checkbox" aria-label="选择本页学生" :checked="allPageSelected" :indeterminate="somePageSelected && !allPageSelected" :disabled="busy || loading || !paged.length" @change="toggleAll" /></TableHead><TableHead>学号</TableHead><TableHead>姓名</TableHead><TableHead>密码状态</TableHead></TableRow></TableHeader>
          <TableBody>
            <TableRow v-if="loading"><TableCell colspan="4" class="student-empty" role="status">正在读取学生…</TableCell></TableRow>
            <TableRow v-for="student in loading ? [] : paged" :key="student.id" :data-state="selected.includes(student.student_id) ? 'selected' : undefined"><TableCell class="student-selection-cell"><input v-model="selected" type="checkbox" :value="student.student_id" :aria-label="`选择${student.student_id}`" :disabled="busy || !student.student_id || (selected.length >= 500 && !selected.includes(student.student_id))" /></TableCell><TableCell class="student-number">{{ student.student_id || '未分配学号' }}</TableCell><TableCell>{{ student.name }}</TableCell><TableCell><span class="student-status" :class="{ pending: student.must_change_password }"><span aria-hidden="true"></span>{{ student.must_change_password ? '待修改初始密码' : '已设置' }}</span></TableCell></TableRow>
            <TableRow v-if="!loading && !filtered.length"><TableCell colspan="4" class="student-empty">{{ students.length ? '没有符合条件的学生，试试其他搜索条件。' : '班级还没有学生，可按学号创建或导入表格。' }}</TableCell></TableRow>
          </TableBody>
        </Table>
      </div>
      <footer class="students-pagination">
        <span>{{ filtered.length ? (page - 1) * pageSize + 1 : 0 }}–{{ Math.min(page * pageSize, filtered.length) }} / {{ filtered.length }} 名学生</span>
        <div class="students-actions"><label class="pagination-size">每页<select v-model.number="pageSize"><option :value="25">25</option><option :value="50">50</option><option :value="100">100</option></select>人</label><span>第 {{ page }} / {{ pageCount }} 页</span><Button variant="outline" size="icon" aria-label="上一页学生" :disabled="page <= 1 || loading" @click="page--"><ChevronLeft :size="16" /></Button><Button variant="outline" size="icon" aria-label="下一页学生" :disabled="page >= pageCount || loading" @click="page++"><ChevronRight :size="16" /></Button></div>
      </footer>
    </section>
    <p class="students-hint"><Info :size="15" />初始与重置密码为本人学号，首次登录必须修改。移出班级保留账号和学习记录。</p>
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
import { computed, onMounted, ref, watch } from "vue";
import { Plus, Upload, Search, RefreshCw, KeyRound, UserMinus, ChevronLeft, ChevronRight, Info } from "@lucide/vue";
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
const page = ref(1), pageSize = ref(25), status = ref("all"), sort = ref("id");
const filtered = computed(() => students.value.filter(s => `${s.student_id} ${s.name}`.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())
  && (status.value === "all" || s.must_change_password === (status.value === "pending")))
  .sort((a, b) => sort.value === "name" ? a.name.localeCompare(b.name, "zh-CN") || (a.student_id ?? "").localeCompare(b.student_id ?? "") : (a.student_id ?? "").localeCompare(b.student_id ?? "")));
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)));
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
const allPageSelected = computed(() => paged.value.length > 0 && paged.value.every(s => selected.value.includes(s.student_id)));
const somePageSelected = computed(() => paged.value.some(s => selected.value.includes(s.student_id)));
watch([search, status], () => { page.value = 1; selected.value = []; });
watch([pageSize, sort], () => { page.value = 1; });
watch(pageCount, count => { page.value = Math.min(page.value, count); });
const base = () => `/teacher/classrooms/${props.classroomId}/students`;
const templateUrl = `data:text/csv;charset=utf-8,${encodeURIComponent('\uFEFF学号,姓名\r\n')}`;
function toggleAll(event: Event) {
  const ids = paged.value.map(s => s.student_id).filter(Boolean);
  selected.value = (event.target as HTMLInputElement).checked ? [...new Set([...selected.value, ...ids])].slice(0, 500) : selected.value.filter(id => !ids.includes(id));
}
async function load() {
  if (!props.classroomId) return;
  loading.value = true;
  try { students.value = await api.get<Student[]>(base()); selected.value = selected.value.filter(id => students.value.some(s => s.student_id === id)); }
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
