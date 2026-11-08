<template>
  <section class="admin-list questions-list" aria-label="题目列表">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="list-toolbar table-toolbar">
      <label class="search-field"
        ><span class="sr-only">搜索题目标题或 ID</span
        ><input v-model="search" type="search" placeholder="搜索题目标题或 ID"
      /></label>
      <label
        >章节<select v-model="chapter">
          <option value="">全部章节</option>
          <option value="unassigned">未分类</option>
          <option
            v-for="item in courses.chapters"
            :key="item.id"
            :value="String(item.id)"
          >
            {{ item.title }}
          </option>
        </select></label
      >
      <label
        >题型<select v-model="kind">
          <option value="">全部题型</option>
          <option
            v-for="(label, value) in questionKinds"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <label
        >来源<select v-model="sourceType">
          <option value="">全部来源</option>
          <option
            v-for="(label, value) in sourceTypes"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <label
        >状态<select v-model="status">
          <option value="">全部状态</option>
          <option
            v-for="(label, value) in questionStatuses"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select></label
      >
      <div class="list-toolbar-actions inline-actions">
        <Button variant="outline" @click="load">刷新题库</Button
        ><Button variant="outline" @click="openAssignment"
          >练习 / 测验选题</Button
        ><Button @click="openEditor()">新增题目</Button>
      </div>
    </div>
    <div class="admin-panel" :aria-busy="loading">
      <p
        v-if="loading && !questions.length"
        class="loading-notice"
        role="status"
      >
        正在读取题库…
      </p>
      <div class="admin-table-scroll">
        <Table
          ><TableHeader
            ><TableRow
              ><TableHead>题目</TableHead
              ><TableHead>关联章节 / 知识点</TableHead
              ><TableHead>来源 / 状态</TableHead><TableHead>使用位置</TableHead
              ><TableHead class="table-actions-heading"
                >操作</TableHead
              ></TableRow
            ></TableHeader
          ><TableBody
            ><TableRow v-for="item in paged" :key="item.id"
              ><TableCell
                ><span class="record-title">{{ item.title }}</span
                ><small
                  >{{ item.id }} ·
                  {{ questionKinds[item.kind] ?? item.kind }}</small
                ></TableCell
              ><TableCell
                >{{ chapterNames(item.chapter_ids ?? [], courses.chapters)
                }}<small>{{
                  pointNames(item.knowledge_point_ids ?? [])
                }}</small></TableCell
              ><TableCell
                >{{ sourceTypes[item.source_type] ?? "导入"
                }}<small
                  ><span class="status-badge" :data-status="item.status">{{
                    questionStatuses[item.status] ?? item.status
                  }}</span></small
                ></TableCell
              ><TableCell>{{
                item.activities?.map((activity) => activity.title).join("、") ||
                "暂未使用"
              }}</TableCell
              ><TableCell
                ><div class="row-actions">
                  <template v-if="canEdit(item.owner_id)"
                    ><Button
                      variant="outline"
                      size="sm"
                      @click="openEditor(item)"
                      >编辑</Button
                    ><Button
                      variant="ghost"
                      size="sm"
                      class="danger"
                      @click="
                        pendingDelete = item;
                        deleteError = '';
                      "
                      >删除</Button
                    ></template
                  ><span v-else class="status-badge">共享只读</span>
                </div></TableCell
              ></TableRow
            ></TableBody
          ></Table
        >
      </div>
      <div v-if="!filtered.length && !loading" class="admin-empty">
        <h3>{{ questions.length ? "没有匹配的题目" : "题库还是空的" }}</h3>
        <p>
          {{
            questions.length
              ? "请调整搜索或筛选条件。"
              : "先录入题目，再把可用题目加入练习或测验。"
          }}
        </p>
      </div>
      <AdminPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filtered.length"
        label="道题目"
      />
    </div>
    <AdminDialog
      v-model:open="editorOpen"
      :title="editingId ? '编辑题目' : '新增题目'"
      description="题目独立保存到题库。标为可用后，可加入现有练习或测验。"
      wide
    >
      <form class="teacher-form" @submit.prevent="saveQuestion">
        <p v-if="editorError" class="error" role="alert">{{ editorError }}</p>
        <div class="form-section">
          <label
            >题目 ID<input
              v-model="draft.id"
              required
              :disabled="!!editingId"
              pattern="[A-Za-z0-9_-]+"
              maxlength="80"
              placeholder="例如 variable-01"
            /><span class="field-hint"
              >字母、数字、短横线或下划线；保存后不可修改。</span
            ></label
          ><label
            >题目标题<input
              v-model="draft.title"
              required
              maxlength="200" /></label
          ><label
            >题型<select v-model="draft.kind">
              <option
                v-for="(label, value) in questionKinds"
                :key="value"
                :value="value"
              >
                {{ label }}
              </option>
            </select></label
          ><label
            >状态<select v-model="draft.status">
              <option
                v-for="(label, value) in questionStatuses"
                :key="value"
                :value="value"
              >
                {{ label }}
              </option></select
            ><span class="field-hint"
              >可用题目至少关联一个章节；知识点可选。</span
            ></label
          ><label class="full-width"
            >题干<textarea v-model="draft.prompt" required rows="4" />
          </label>
        </div>
        <ContentChapterSelect
          v-model="draft.chapter_ids"
          :chapters="courses.chapters"
        />
        <fieldset class="content-tag-select">
          <legend>关联知识点</legend>
          <p class="field-hint">可多选，知识点所属章节会一并关联。</p>
          <div class="content-tag-options">
            <label
              v-for="point in courses.knowledge_points"
              :key="point.id"
              class="checkbox-label"
              ><input
                type="checkbox"
                :checked="draft.knowledge_point_ids.includes(point.id)"
                @change="
                  togglePoint(
                    point.id,
                    ($event.target as HTMLInputElement).checked,
                  )
                "
              />{{ point.title
              }}<span class="field-hint">{{
                chapterNames([point.chapter_id], courses.chapters)
              }}</span></label
            >
          </div>
          <p v-if="!courses.knowledge_points.length" class="field-hint">
            暂无知识点，可稍后补充。
          </p>
        </fieldset>
        <fieldset
          v-if="draft.kind === 'single_choice'"
          class="content-tag-select"
        >
          <legend>选项与答案</legend>
          <p class="field-hint">选中一个正确答案，至少保留两个不同选项。</p>
          <div
            v-for="(_, index) in draft.options"
            :key="index"
            class="question-option-row"
          >
            <input
              v-model="draft.answerIndex"
              type="radio"
              name="correct-option"
              :value="index"
              :aria-label="`选项 ${index + 1} 为正确答案`"
            /><input
              v-model="draft.options[index]"
              required
              :aria-label="`选项 ${index + 1}`"
            /><Button
              variant="ghost"
              size="sm"
              :disabled="draft.options.length <= 2"
              :aria-label="`移除选项 ${index + 1}`"
              @click="removeOption(index)"
              >移除</Button
            >
          </div>
          <Button variant="outline" size="sm" @click="draft.options.push('')"
            >添加选项</Button
          >
        </fieldset>
        <label v-else-if="draft.kind === 'boolean'"
          >正确答案<select v-model="draft.booleanAnswer">
            <option value="true">正确</option>
            <option value="false">错误</option>
          </select></label
        >
        <label v-else-if="draft.kind === 'fill'"
          >可接受答案<textarea
            v-model="draft.textAnswer"
            required
            rows="3"
          /><span class="field-hint"
            >每行一个可接受的答案，忽略大小写和首尾空格。</span
          ></label
        >
        <template v-else-if="draft.kind === 'python'">
          <fieldset class="content-tag-select">
            <legend>公开样例</legend>
            <div
              v-for="(sample, index) in draft.samples"
              :key="index"
              class="question-case-row"
            >
              <label>输入<textarea v-model="sample.input" rows="2" /></label
              ><label
                >期望输出<textarea v-model="sample.output" rows="2" /></label
              ><Button
                variant="ghost"
                size="sm"
                :aria-label="`移除公开样例 ${index + 1}`"
                @click="draft.samples.splice(index, 1)"
                >移除</Button
              >
            </div>
            <Button
              variant="outline"
              size="sm"
              @click="draft.samples.push({ input: '', output: '' })"
              >添加公开样例</Button
            >
          </fieldset>
          <fieldset class="content-tag-select">
            <legend>隐藏用例</legend>
            <p class="field-hint">仅教师可见，用于正式判题。至少添加一个。</p>
            <div
              v-for="(sample, index) in draft.hidden_cases"
              :key="index"
              class="question-case-row"
            >
              <label>输入<textarea v-model="sample.input" rows="2" /></label
              ><label
                >期望输出<textarea
                  v-model="sample.expected_output"
                  rows="2"
                /></label
              ><Button
                variant="ghost"
                size="sm"
                :aria-label="`移除隐藏用例 ${index + 1}`"
                @click="draft.hidden_cases.splice(index, 1)"
                >移除</Button
              >
            </div>
            <Button
              variant="outline"
              size="sm"
              @click="
                draft.hidden_cases.push({ input: '', expected_output: '' })
              "
              >添加隐藏用例</Button
            >
          </fieldset>
        </template>
        <div class="form-section">
          <h3>来源</h3>
          <label
            >来源类型<select v-model="draft.source_type">
              <option
                v-for="(label, value) in sourceTypes"
                :key="value"
                :value="value"
              >
                {{ label }}
              </option>
            </select></label
          ><label
            >来源资料（可选）<select v-model="draft.material_id">
              <option value="">不关联资料</option>
              <option
                v-for="material in materials"
                :key="material.id"
                :value="String(material.id)"
              >
                {{ material.title }}
              </option>
            </select></label
          ><label
            >来源页码（可选）<input
              v-model="draft.page_no"
              type="number"
              min="1"
              step="1"
              :disabled="!draft.material_id" /></label
          ><label class="full-width"
            >来源依据（可选）<textarea
              v-model="draft.evidence"
              rows="2"
              placeholder="例如：依据讲义中的变量赋值示例。"
            />
          </label>
        </div>
        <div class="form-actions">
          <Button variant="outline" :disabled="busy" @click="editorOpen = false"
            >取消</Button
          ><Button type="submit" :disabled="busy">{{
            busy ? "正在保存…" : "保存题目"
          }}</Button>
        </div>
      </form>
    </AdminDialog>
    <AdminDialog
      :open="!!pendingDelete"
      title="删除题目"
      :description="`确认删除「${pendingDelete?.title ?? ''}」？仍在练习或测验中使用的题目需要先解除引用，解除引用后才可删除或停用。`"
      @update:open="
        (open) => {
          if (!open) pendingDelete = null;
        }
      "
      ><p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
      <div class="form-actions">
        <Button variant="outline" :disabled="busy" @click="pendingDelete = null"
          >取消</Button
        ><Button
          variant="destructive"
          :disabled="busy"
          @click="deleteQuestion"
          >{{ busy ? "正在删除…" : "确认删除" }}</Button
        >
      </div></AdminDialog
    >
    <AdminDialog
      v-model:open="assignmentOpen"
      title="练习 / 测验选题"
      description="从题库选入可用题目，按下方顺序展示给学生。保存后更新所选活动的当前配置。"
      wide
    >
      <form class="teacher-form" @submit.prevent="saveAssignment">
        <p v-if="assignmentError" class="error" role="alert">
          {{ assignmentError }}
        </p>
        <label
          >练习或测验<select v-model="assignmentActivity" required>
            <option value="" disabled>请选择现有练习或测验</option>
            <option
              v-for="activity in assignableActivities"
              :key="activity.id"
              :value="activity.id"
            >
              {{ activity.title }} ·
              {{ chapterNames([activity.chapter_id], courses.chapters) }}
            </option>
          </select></label
        >
        <p v-if="!assignableActivities.length" class="notice">
          暂无可选的练习或测验，请先准备课程活动。
        </p>
        <template v-if="assignmentActivity"
          ><section class="content-assignment">
            <h3>已选题目 · {{ assignmentIds.length }}</h3>
            <ol v-if="assignmentIds.length" class="content-ordered-questions">
              <li v-for="(id, index) in assignmentIds" :key="id">
                <span
                  ><strong>{{
                    questions.find((item) => item.id === id)?.title ?? id
                  }}</strong
                  ><small>{{ id }}</small></span
                >
                <div class="row-actions">
                  <Button
                    variant="ghost"
                    size="sm"
                    :disabled="index === 0"
                    :aria-label="`上移题目 ${id}`"
                    @click="moveQuestion(index, -1)"
                    >上移</Button
                  ><Button
                    variant="ghost"
                    size="sm"
                    :disabled="index === assignmentIds.length - 1"
                    :aria-label="`下移题目 ${id}`"
                    @click="moveQuestion(index, 1)"
                    >下移</Button
                  ><Button
                    variant="ghost"
                    size="sm"
                    :aria-label="`移出题目 ${id}`"
                    @click="toggleAssignment(id, false)"
                    >移出</Button
                  >
                </div>
              </li>
            </ol>
            <p v-else class="field-hint">还没有选入题目，从下面的题库勾选。</p>
          </section>
          <label
            >搜索可用题目<input
              v-model="assignmentSearch"
              type="search"
              placeholder="搜索标题或 ID"
          /></label>
          <div class="content-assignment-options">
            <label
              v-for="question in assignmentCandidates"
              :key="question.id"
              class="checkbox-label"
              ><input
                type="checkbox"
                :checked="assignmentIds.includes(question.id)"
                @change="
                  toggleAssignment(
                    question.id,
                    ($event.target as HTMLInputElement).checked,
                  )
                "
              /><span
                >{{ question.title
                }}<small
                  >{{ questionKinds[question.kind] }} · {{ question.id }}</small
                ></span
              ></label
            >
            <p v-if="!assignmentCandidates.length" class="field-hint">
              没有匹配的可用题目。请先在题库中审核题目。
            </p>
          </div></template
        >
        <div class="form-actions">
          <Button
            variant="outline"
            :disabled="busy"
            @click="assignmentOpen = false"
            >取消</Button
          ><Button type="submit" :disabled="busy || !assignmentActivity">{{
            busy ? "正在保存…" : "保存选题与顺序"
          }}</Button>
        </div>
      </form>
    </AdminDialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onDeactivated, onMounted, reactive, ref, watch } from "vue";
import { Button } from "../components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../components/ui/table";
import AdminDialog from "../components/admin/AdminDialog.vue";
import AdminPagination from "../components/admin/AdminPagination.vue";
import ContentChapterSelect from "./ContentChapterSelect.vue";
import { api, notify } from "../shared/api";
import { useSession } from "../shared/session";
import {
  chapterNames,
  matchesChapter,
  questionKinds,
  questionStatuses,
  sourceTypes,
} from "../shared/teacherContent";
import type {
  QuestionSourceType,
  QuestionStatus,
  TeacherCourses,
  TeacherMaterial,
  TeacherQuestion,
} from "../shared/teacherContent";
const props = defineProps<{ courses: TeacherCourses }>(),
  emit = defineEmits<{ updated: [] }>(),
  session = useSession();
const questions = ref<TeacherQuestion[]>([]),
  materials = ref<TeacherMaterial[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref("");
const search = ref(""),
  chapter = ref(""),
  kind = ref(""),
  sourceType = ref(""),
  status = ref(""),
  page = ref(1),
  pageSize = ref(10);
const filtered = computed(() =>
  questions.value.filter(
    (item) =>
      matchesChapter(item.chapter_ids ?? [], chapter.value) &&
      (!kind.value || item.kind === kind.value) &&
      (!sourceType.value || item.source_type === sourceType.value) &&
      (!status.value || item.status === status.value) &&
      `${item.title} ${item.id}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
  ),
);
const paged = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
watch([search, chapter, kind, sourceType, status, pageSize], () => {
  page.value = 1;
});
watch(
  () => filtered.value.length,
  (total) => {
    page.value = Math.min(
      page.value,
      Math.max(1, Math.ceil(total / pageSize.value)),
    );
  },
);
function pointNames(ids: number[]) {
  return (
    ids
      .map(
        (id) =>
          props.courses.knowledge_points.find((point) => point.id === id)
            ?.title ?? `知识点 #${id}`,
      )
      .join("、") || "未关联知识点"
  );
}
function emptyDraft() {
  return {
    id: "",
    title: "",
    kind: "single_choice",
    prompt: "",
    chapter_ids: [] as number[],
    knowledge_point_ids: [] as number[],
    source_type: "imported" as QuestionSourceType,
    status: "draft" as QuestionStatus,
    options: ["", ""],
    answerIndex: 0,
    booleanAnswer: "true",
    textAnswer: "",
    samples: [] as { input: string; output: string }[],
    hidden_cases: [] as { input: string; expected_output: string }[],
    material_id: "",
    page_no: "",
    evidence: "",
    sourceExtra: {} as Record<string, unknown>,
    originalAnswer: null as unknown,
    originalKind: "",
    originalAnswerText: "",
  };
}
const draft = reactive(emptyDraft()),
  editingId = ref<string | null>(null),
  editorOpen = ref(false),
  editorError = ref("");
const pendingDelete = ref<TeacherQuestion | null>(null),
  deleteError = ref("");
async function load() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    questions.value = await api.get<TeacherQuestion[]>("/teacher/questions");
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "题库加载失败";
  } finally {
    loading.value = false;
  }
}
async function loadMaterials() {
  try {
    materials.value = await api.get<TeacherMaterial[]>("/teacher/materials");
  } catch (reason) {
    editorError.value =
      reason instanceof Error ? reason.message : "来源资料加载失败";
  }
}
function openEditor(item?: TeacherQuestion) {
  if (item && editingId.value !== item.id) {
    const source = item.source ?? {},
      answer = unwrapAnswer(item.answer);
    Object.assign(draft, emptyDraft(), {
      id: item.id,
      title: item.title,
      kind: item.kind,
      prompt: item.prompt,
      chapter_ids: [...(item.chapter_ids ?? [])],
      knowledge_point_ids: [...(item.knowledge_point_ids ?? [])],
      source_type: item.source_type ?? "imported",
      status: item.status ?? "draft",
      options: item.options?.length ? [...item.options] : ["", ""],
      answerIndex: Math.max(
        0,
        item.options?.findIndex((option) => option === answer) ?? 0,
      ),
      booleanAnswer: String(answer === true),
      textAnswer: Array.isArray(answer)
        ? answer.map(String).join("\n")
        : typeof answer === "string"
          ? answer
          : "",
      samples: (item.samples ?? []).map((sample) => ({
        input: sample.input,
        output:
          sample.output ??
          (sample as { expected_output?: string }).expected_output ??
          "",
      })),
      hidden_cases: (item.hidden_cases ?? []).map((sample) => ({
        input: sample.input,
        expected_output: sample.expected_output ?? sample.output ?? "",
      })),
      material_id: source.material_id == null ? "" : String(source.material_id),
      page_no: source.page_no == null ? "" : String(source.page_no),
      evidence: typeof source.evidence === "string" ? source.evidence : "",
      sourceExtra: { ...source },
      originalAnswer: JSON.parse(JSON.stringify(item.answer ?? null)),
      originalKind: item.kind,
      originalAnswerText: answerText(item.kind, answer),
    });
    editingId.value = item.id;
  } else if (!item && editingId.value !== null) {
    editingId.value = null;
    Object.assign(draft, emptyDraft());
  }
  editorError.value = "";
  editorOpen.value = true;
  void loadMaterials();
}
function canEdit(ownerId: number | null) {
  return session.user?.role === "admin" || ownerId === session.user?.id;
}
function unwrapAnswer(answer: unknown): unknown {
  return answer !== null &&
    typeof answer === "object" &&
    !Array.isArray(answer) &&
    "value" in answer
    ? (answer as Record<string, unknown>).value
    : answer;
}
function answerText(kind: string, answer: unknown) {
  return kind === "fill" && Array.isArray(answer)
    ? answer.map(String).join("\n")
    : String(answer ?? "");
}
function questionAnswer(): unknown {
  const answers = draft.textAnswer
    .split("\n")
    .map((value) => value.trim())
    .filter(Boolean);
  const value =
    draft.kind === "boolean"
      ? draft.booleanAnswer === "true"
      : draft.kind === "single_choice"
        ? draft.options[draft.answerIndex]
        : draft.kind === "fill"
          ? answers.length > 1
            ? answers
            : (answers[0] ?? "")
          : null;
  if (draft.kind !== draft.originalKind) return value;
  const currentText =
    draft.kind === "fill" ? draft.textAnswer : answerText(draft.kind, value);
  if (currentText === draft.originalAnswerText) return draft.originalAnswer;
  const original = draft.originalAnswer;
  const shaped =
    draft.kind === "fill" &&
    Array.isArray(unwrapAnswer(original)) &&
    !Array.isArray(value)
      ? [value]
      : value;
  return original !== null &&
    typeof original === "object" &&
    !Array.isArray(original) &&
    "value" in original
    ? { ...original, value: shaped }
    : shaped;
}
watch(
  () => [...draft.chapter_ids],
  (ids) => {
    draft.knowledge_point_ids = draft.knowledge_point_ids.filter((id) => {
      const point = props.courses.knowledge_points.find(
        (item) => item.id === id,
      );
      return point && ids.includes(point.chapter_id);
    });
  },
);
function togglePoint(id: number, checked: boolean) {
  if (checked) {
    draft.knowledge_point_ids = [
      ...new Set([...draft.knowledge_point_ids, id]),
    ];
    const point = props.courses.knowledge_points.find((item) => item.id === id);
    if (point && !draft.chapter_ids.includes(point.chapter_id))
      draft.chapter_ids.push(point.chapter_id);
  } else
    draft.knowledge_point_ids = draft.knowledge_point_ids.filter(
      (value) => value !== id,
    );
}
function removeOption(index: number) {
  draft.options.splice(index, 1);
  if (draft.answerIndex >= index)
    draft.answerIndex = Math.max(0, draft.answerIndex - 1);
}
async function saveQuestion() {
  if (busy.value) return;
  editorError.value = "";
  if (!draft.id.trim() || !draft.title.trim() || !draft.prompt.trim()) {
    editorError.value = "请填写题目 ID、标题和题干。";
    return;
  }
  if (
    draft.kind === "single_choice" &&
    (draft.options.length < 2 ||
      draft.options.some((option) => !option.trim()) ||
      new Set(draft.options).size !== draft.options.length)
  ) {
    editorError.value = "请填写至少两个不同的选项。";
    return;
  }
  if (draft.status === "ready" && !draft.chapter_ids.length) {
    editorError.value = "可用题目至少需要关联一个章节。";
    return;
  }
  if (
    draft.kind === "python" &&
    draft.status === "ready" &&
    !draft.hidden_cases.length
  ) {
    editorError.value = "编程题至少需要一个隐藏用例。";
    return;
  }
  if (draft.kind === "fill" && !draft.textAnswer.trim()) {
    editorError.value = "请填写至少一个可接受答案。";
    return;
  }
  const source = { ...draft.sourceExtra };
  delete source.kind;
  delete source.material_id;
  delete source.page_no;
  delete source.evidence;
  if (draft.material_id) source.material_id = Number(draft.material_id);
  if (draft.material_id && draft.page_no)
    source.page_no = Number(draft.page_no);
  if (draft.evidence.trim()) source.evidence = draft.evidence.trim();
  const payload = {
    id: draft.id.trim(),
    title: draft.title.trim(),
    kind: draft.kind,
    prompt: draft.prompt,
    chapter_ids: draft.chapter_ids,
    knowledge_point_ids: draft.knowledge_point_ids,
    source_type: draft.source_type,
    status: draft.status,
    source,
    options: draft.kind === "single_choice" ? draft.options : [],
    answer: questionAnswer(),
    samples: draft.kind === "python" ? draft.samples : [],
    hidden_cases: draft.kind === "python" ? draft.hidden_cases : [],
  };
  busy.value = true;
  try {
    if (editingId.value) {
      const fields: Omit<typeof payload, "id"> & { id?: string } = {
        ...payload,
      };
      delete fields.id;
      await api.put(`/teacher/questions/${editingId.value}`, fields);
    } else await api.post("/teacher/questions", payload);
    editorOpen.value = false;
    editingId.value = null;
    Object.assign(draft, emptyDraft());
    await load();
    emit("updated");
    notify("题目已保存到题库。");
  } catch (reason) {
    editorError.value =
      reason instanceof Error ? reason.message : "题目保存失败";
  } finally {
    busy.value = false;
  }
}
async function deleteQuestion() {
  if (busy.value || !pendingDelete.value) return;
  busy.value = true;
  deleteError.value = "";
  try {
    await api.request(`/teacher/questions/${pendingDelete.value.id}`, {
      method: "DELETE",
    });
    pendingDelete.value = null;
    await load();
    emit("updated");
    notify("题目已删除。");
  } catch (reason) {
    deleteError.value = reason instanceof Error ? reason.message : "删除失败";
  } finally {
    busy.value = false;
  }
}
const assignmentOpen = ref(false),
  assignmentActivity = ref(""),
  assignmentSearch = ref(""),
  assignmentError = ref(""),
  assignments = reactive<Record<string, string[]>>({});
const assignableActivities = computed(() =>
  props.courses.activities.filter(
    (activity) =>
      ["practice", "assessment"].includes(activity.kind) &&
      (session.user?.role === "admin" ||
        activity.owner_id === session.user?.id),
  ),
);
const assignmentIds = computed(
  () => assignments[assignmentActivity.value] ?? [],
);
const selectedAssignmentActivity = computed(() =>
  props.courses.activities.find(
    (activity) => activity.id === assignmentActivity.value,
  ),
);
const assignmentCandidates = computed(() =>
  questions.value.filter(
    (question) =>
      question.status === "ready" &&
      (question.owner_id === null ||
        question.owner_id === selectedAssignmentActivity.value?.owner_id) &&
      question.chapter_ids.includes(
        selectedAssignmentActivity.value?.chapter_id ?? -1,
      ) &&
      `${question.title} ${question.id}`
        .toLowerCase()
        .includes(assignmentSearch.value.trim().toLowerCase()),
  ),
);
watch(assignmentActivity, (id) => {
  if (id && !assignments[id])
    assignments[id] = [
      ...(props.courses.activities.find((activity) => activity.id === id)
        ?.question_ids ?? []),
    ];
  assignmentError.value = "";
});
function openAssignment() {
  assignmentError.value = "";
  assignmentOpen.value = true;
  if (!assignmentActivity.value)
    assignmentActivity.value = assignableActivities.value[0]?.id ?? "";
}
function toggleAssignment(id: string, checked: boolean) {
  if (
    checked &&
    props.courses.activities.find(
      (activity) => activity.id === assignmentActivity.value,
    )?.kind === "assessment" &&
    assignmentIds.value.length >= 100
  ) {
    assignmentError.value = "测验最多选择 100 道题目。";
    return;
  }
  assignmentError.value = "";
  assignments[assignmentActivity.value] = checked
    ? [...new Set([...assignmentIds.value, id])]
    : assignmentIds.value.filter((value) => value !== id);
}
function moveQuestion(index: number, direction: number) {
  const next = [...assignmentIds.value],
    target = index + direction;
  if (target < 0 || target >= next.length) return;
  [next[index], next[target]] = [next[target]!, next[index]!];
  assignments[assignmentActivity.value] = next;
}
async function saveAssignment() {
  if (busy.value || !assignmentActivity.value) return;
  assignmentError.value = "";
  if (
    props.courses.activities.find(
      (activity) => activity.id === assignmentActivity.value,
    )?.kind === "assessment" &&
    assignmentIds.value.length > 100
  ) {
    assignmentError.value = "测验最多选择 100 道题目。";
    return;
  }
  busy.value = true;
  try {
    await api.put(`/teacher/activities/${assignmentActivity.value}/questions`, {
      question_ids: assignmentIds.value,
    });
    assignmentOpen.value = false;
    await load();
    emit("updated");
    notify("选题与顺序已保存。");
  } catch (reason) {
    assignmentError.value =
      reason instanceof Error ? reason.message : "选题保存失败";
  } finally {
    busy.value = false;
  }
}
onMounted(() => void load());
onDeactivated(() => {
  editorOpen.value = false;
  assignmentOpen.value = false;
  pendingDelete.value = null;
});
</script>
