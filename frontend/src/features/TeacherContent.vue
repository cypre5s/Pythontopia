<template>
  <div class="admin-content admin-stack">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <KeepAlive
      ><TeacherCurriculum
        v-if="section === 'curriculum'"
        @updated="curriculumUpdated"
    /></KeepAlive>
    <template v-if="section === 'materials'">
      <section class="admin-list materials-list" aria-label="课程材料列表">
        <div class="list-toolbar table-toolbar">
          <label class="search-field"
            ><span class="sr-only">搜索材料名称或 ID</span
            ><input
              v-model="materialSearch"
              type="search"
              placeholder="搜索材料名称或 ID"
          /></label>
          <label
            >处理状态<select v-model="materialStatus">
              <option value="">全部状态</option>
              <option value="ready">可使用</option>
              <option value="queued">排队中</option>
              <option value="processing">处理中</option>
              <option value="failed">处理失败</option>
              <option value="missing">媒体缺失</option>
            </select></label
          >
          <div class="list-toolbar-actions inline-actions">
            <Button variant="outline" @click="loadMaterials">刷新状态</Button
            ><Button @click="openUploadEditor">上传课程材料</Button>
          </div>
        </div>
        <div class="admin-panel">
          <div class="admin-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>资料 / ID</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th>处理反馈</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in pagedMaterials" :key="m.id">
                  <td class="record-title">
                    {{ m.title }}<small>#{{ m.id }}</small>
                  </td>
                  <td>{{ materialKinds[m.kind] ?? m.kind }}</td>
                  <td>
                    <span class="status-badge" :data-status="m.status">{{
                      materialStatuses[m.status] ?? m.status
                    }}</span>
                  </td>
                  <td>{{ m.error ?? m.notice ?? "—" }}</td>
                  <td>
                    <Button
                      variant="outline"
                      size="sm"
                      :disabled="m.status !== 'ready' || !classroomId"
                      @click="previewMaterial(m)"
                      >预览</Button
                    >
                  </td>
                </tr>
                <tr v-if="!filteredMaterials.length">
                  <td colspan="5" class="admin-empty">
                    {{
                      materials.length
                        ? "没有匹配的材料，请调整搜索或筛选条件。"
                        : "还没有课程材料，上传文件后可在此查看处理状态。"
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <AdminPagination
            v-model:page="materialPage"
            v-model:page-size="materialPageSize"
            :total="filteredMaterials.length"
            label="份材料"
          />
        </div>
        <p v-if="!classroomId" class="field-hint">
          选择班级后可预览该班级可访问的材料。
        </p>
      </section>
      <AdminDialog
        v-model:open="uploadEditorOpen"
        title="上传课程材料"
        description="上传新材料，或更新已有材料。引用该材料的活动将使用更新后的内容。"
        wide
      >
        <p v-if="uploadError" class="error" role="alert">{{ uploadError }}</p>
        <form class="teacher-form" @submit.prevent="upload">
          <label
            >资料名称<input
              v-model="uploadForm.title"
              required
              placeholder="例如：第二章课堂讲义"
          /></label>
          <label
            >所属章节<select v-model.number="uploadForm.chapter_id">
              <option v-for="c in courses.chapters" :key="c.id" :value="c.id">
                {{ c.title }}
              </option>
            </select></label
          >
          <label class="full-width"
            >文件<input
              type="file"
              accept=".pdf,.ppt,.pptx,.mp4,.webm,.txt,.md"
              :required="!uploadFile"
              @change="selectFile"
            /><span class="field-hint"
              >支持 PDF、PPT / PPTX、MP4 / WebM、TXT / Markdown。</span
            ><span v-if="uploadFile" class="field-hint"
              >已选择：{{ uploadFile.name }}</span
            ></label
          >
          <label
            >更新已有材料（选填）<input
              v-model="uploadForm.material_id"
              type="number"
              min="1"
              placeholder="填写材料 ID，留空创建新材料"
            /><span class="field-hint">材料 ID 可从上方列表查看。</span></label
          >
          <div class="form-actions">
            <Button type="submit" :disabled="uploadBusy">{{
              uploadBusy ? "上传中…" : "上传并处理"
            }}</Button
            ><Button variant="outline" @click="uploadEditorOpen = false"
              >取消</Button
            >
          </div>
        </form>
        <p class="field-hint">
          文件处理状态会自动更新。PPT 转换失败时会显示原因，处理完成后才能预览。
        </p>
      </AdminDialog>
      <section v-if="preview" class="admin-panel material-preview">
        <header class="panel-heading">
          <h2>材料预览</h2>
          <Button variant="outline" @click="closePreview">收起预览</Button>
        </header>
        <iframe
          v-if="['pdf', 'presentation'].includes(preview.kind)"
          :src="preview.url"
          title="教师 PDF 预览"
        ></iframe>
        <video
          v-else-if="preview.kind === 'video'"
          :src="preview.url"
          controls
        ></video>
        <pre v-else>{{ preview.text }}</pre>
      </section>
    </template>
    <template v-else-if="section === 'activities'">
      <section class="admin-panel">
        <header class="panel-heading">
          <div>
            <h2>
              学习活动
              <span class="count-badge">{{ courses.activities.length }}</span>
            </h2>
            <p class="panel-description">
              查看章节活动，维护材料、知识点与 NPC 的关联。
            </p>
          </div>
          <Button @click="startActivity">新建活动</Button>
        </header>
        <div class="table-toolbar">
          <label class="search-field"
            ><span class="sr-only">搜索活动标题或 ID</span
            ><input
              v-model="activitySearch"
              type="search"
              placeholder="搜索活动标题或 ID"
          /></label>
          <label
            >章节<select v-model="activityChapter">
              <option value="">全部章节</option>
              <option
                v-for="c in courses.chapters"
                :key="c.id"
                :value="String(c.id)"
              >
                {{ c.title }}
              </option>
            </select></label
          >
          <span class="muted">{{ filteredActivities.length }} 个活动</span>
        </div>
        <div class="admin-table-scroll">
          <table>
            <thead>
              <tr>
                <th>活动</th>
                <th>类型 / 章节</th>
                <th>NPC</th>
                <th>知识点</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in filteredActivities" :key="a.id">
                <td class="record-title">
                  {{ a.title }}<small>{{ a.id }}</small>
                </td>
                <td>
                  {{ activityKinds[a.kind] ?? a.kind
                  }}<small>{{ chapterTitle(a.chapter_id) }}</small>
                </td>
                <td>
                  {{
                    courses.npcs.find((n) => n.key === a.npc_key)?.name ??
                    a.npc_key
                  }}
                </td>
                <td>
                  {{
                    (a.knowledge_point_ids ?? [])
                      .map(
                        (id) =>
                          courses.knowledge_points?.find((k) => k.id === id)
                            ?.title ?? id,
                      )
                      .join("、") || "未绑定"
                  }}
                </td>
                <td>
                  <Button
                    v-if="
                      session.user?.role === 'admin' ||
                      a.owner_id === session.user?.id
                    "
                    variant="outline"
                    size="sm"
                    @click="editActivity(a)"
                    >编辑绑定</Button
                  ><span v-else class="status-badge">共享只读</span>
                </td>
              </tr>
              <tr v-if="!filteredActivities.length">
                <td colspan="5" class="admin-empty">
                  {{
                    courses.activities.length
                      ? "没有匹配的活动，请调整搜索或章节筛选。"
                      : "还没有学习活动，可以先创建活动并绑定课程材料。"
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <details
        :open="activityEditorOpen"
        class="admin-panel admin-editor"
        @toggle="
          activityEditorOpen = ($event.target as HTMLDetailsElement).open
        "
      >
        <summary>{{ activity.edit ? "编辑自建活动" : "新建活动" }}</summary>
        <p class="panel-description">
          共享活动只读。保存自建活动后，可在任务配置中引用活动 ID。
        </p>
        <form class="teacher-form" @submit.prevent="saveActivity">
          <h3 class="form-section">基本信息</h3>
          <label
            >活动 ID<input
              v-model="activity.id"
              required
              :disabled="activity.edit"
              placeholder="例如：my-python-lesson"
          /></label>
          <label
            >标题<input
              v-model="activity.title"
              required
              placeholder="学生看到的活动名称"
          /></label>
          <label
            >章节<select
              v-model.number="activity.chapter_id"
              :disabled="activity.edit"
            >
              <option v-for="c in courses.chapters" :key="c.id" :value="c.id">
                {{ c.title }}
              </option>
            </select></label
          >
          <label
            >类型<select v-model="activity.kind" :disabled="activity.edit">
              <option value="lesson">教学</option>
              <option value="reading">阅读</option>
              <option value="practice">练习</option>
              <option value="assessment">测验</option>
            </select></label
          >
          <h3 class="form-section">内容与角色</h3>
          <label
            >NPC<select v-model="activity.npc_key">
              <option v-for="n in courses.npcs" :key="n.key" :value="n.key">
                {{ n.name }}
              </option>
            </select></label
          >
          <label
            >材料<select v-model="activity.material_id">
              <option value="">无材料</option>
              <option
                v-for="m in materials"
                :key="m.id"
                :value="m.id"
              >
                {{ m.title }}
              </option>
            </select></label
          >
          <KnowledgePointSelect
            v-model="activity.knowledge_point_ids"
            :chapter-id="activity.chapter_id"
            :points="courses.knowledge_points ?? []"
          />
          <label class="checkbox-label full-width"
            ><input
              v-model="activity.assistant_allowed"
              type="checkbox"
            />允许学生在活动内主动使用助教</label
          >
          <div class="form-actions">
            <Button type="submit" :disabled="saving">{{
              saving ? "正在保存…" : activity.edit ? "保存活动" : "创建活动"
            }}</Button
            ><Button
              v-if="activity.edit"
              variant="outline"
              :disabled="saving"
              @click="startActivity"
              >另建活动</Button
            ><Button variant="ghost" @click="activityEditorOpen = false"
              >收起</Button
            >
          </div>
        </form>
      </details>
    </template>
    <template v-else-if="section === 'quests'">
      <section class="admin-panel">
        <header class="panel-heading">
          <div>
            <h2>任务定义</h2>
            <p class="panel-description">
              选择已有定义作为起点，修改并校验后保存当前配置。
            </p>
          </div>
          <span class="count-badge">{{ quests.length }} 个定义</span>
        </header>
        <ol class="admin-steps">
          <li>选择任务定义</li>
          <li>编辑并校验</li>
          <li>保存后到「章节管理」设置开放规则</li>
        </ol>
        <label
          >已有任务<select @change="selectQuest">
            <option value="">选择定义作为起点</option>
            <option v-for="(q, index) in quests" :key="index" :value="index">
              {{ String(q.title ?? q.id) }}
            </option>
          </select></label
        >
        <p v-if="!quests.length" class="field-hint">
          暂无任务定义，可在下方编辑完整任务 JSON。
        </p>
        <details open class="admin-editor">
          <summary>任务 JSON 编辑器</summary>
          <label
            >任务 JSON<textarea
              v-model="questJson"
              class="json-editor"
              rows="20"
              spellcheck="false"
              placeholder="选择已有任务，或填写新的任务定义"
            ></textarea>
          </label>
        </details>
        <p class="field-hint">
          重复
          ID、缺失引用、循环和非法效果会被拒绝；学生进度和成绩继续保留。
        </p>
        <div class="form-actions">
          <Button
            variant="outline"
            :disabled="saving || !questJson.trim()"
            @click="validateQuest"
            >校验任务</Button
          ><Button
            :disabled="saving || !questJson.trim()"
            @click="publishQuest"
            >{{ saving ? "正在处理…" : "保存任务配置" }}</Button
          >
        </div>
        <pre v-if="validation" class="notice" role="status">{{
          validation
        }}</pre>
      </section>
      <details
        v-if="session.user?.role === 'admin'"
        class="admin-panel admin-editor"
      >
        <summary>新增 NPC</summary>
        <p class="panel-description">
          配置小镇角色的地图位置和脚本对白。已有角色可直接在活动表单中绑定。
        </p>
        <label
          >NPC JSON<textarea
            v-model="npcJson"
            class="json-editor"
            rows="10"
            spellcheck="false"
          ></textarea>
        </label>
        <div class="form-actions">
          <Button :disabled="saving || !npcJson.trim()" @click="createNpc">{{
            saving ? "正在创建…" : "创建 NPC"
          }}</Button>
        </div>
      </details>
    </template>
    <template v-else-if="section === 'questions'">
      <section class="admin-list questions-list" aria-label="题目列表">
        <div class="list-toolbar table-toolbar">
          <label class="search-field"
            ><span class="sr-only">搜索题目标题、ID 或活动</span
            ><input
              v-model="questionSearch"
              type="search"
              placeholder="搜索题目标题、ID 或活动"
          /></label>
          <label
            >题型<select v-model="questionKind">
              <option value="">全部题型</option>
              <option value="single_choice">单选题</option>
              <option value="boolean">判断题</option>
              <option value="fill">填空题</option>
              <option value="python">编程题</option>
            </select></label
          >
          <div class="list-toolbar-actions inline-actions">
            <Button @click="openQuestionEditor">新增题目</Button>
          </div>
        </div>
        <div class="admin-panel">
          <div class="admin-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>题目</th>
                  <th>类型</th>
                  <th>关联活动</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="q in pagedQuestions" :key="q.id">
                  <td class="record-title">
                    {{ q.title }}<small>{{ q.id }}</small>
                  </td>
                  <td>
                    <span class="status-badge">{{
                      questionKinds[q.kind] ?? q.kind
                    }}</span>
                  </td>
                  <td>
                    {{
                      courses.activities.find((a) => a.id === q.activity_id)
                        ?.title ?? q.activity_id
                    }}<small>{{ q.activity_id }}</small>
                  </td>
                </tr>
                <tr v-if="!filteredQuestions.length">
                  <td colspan="3" class="admin-empty">
                    {{
                      questions.length
                        ? "没有匹配的题目，请调整搜索或题型筛选。"
                        : "题库为空，可以创建经过核验并标明来源的题目。"
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <AdminPagination
            v-model:page="questionPage"
            v-model:page-size="questionPageSize"
            :total="filteredQuestions.length"
            label="道题目"
          />
        </div>
      </section>
      <AdminDialog
        v-model:open="questionEditorOpen"
        title="新增题目"
        description="题目须关联自己的活动。填写经过核验的题干和真实来源，检查答案与测试用例后创建。"
        wide
      >
        <p v-if="questionError" class="error" role="alert">
          {{ questionError }}
        </p>
        <p v-if="questionJsonError" class="error" role="alert">
          {{ questionJsonError }}。请在下方「完整 JSON」中修正，当前内容会保留。
        </p>
        <form class="teacher-form" @submit.prevent="createQuestion">
          <h3 class="form-section">题目信息</h3>
          <label
            >题目 ID<input
              v-model="questionId"
              :disabled="!questionDraft"
              required
              placeholder="例如：my-concept-1"
          /></label>
          <label
            >关联活动<select
              v-model="questionActivity"
              :disabled="!questionDraft"
              required
            >
              <option value="">选择自己的活动</option>
              <option
                v-for="a in ownQuestionActivities"
                :key="a.id"
                :value="a.id"
              >
                {{ a.title }} · {{ a.id }}
              </option>
              <option
                v-if="
                  questionActivity &&
                  !ownQuestionActivities.some((a) => a.id === questionActivity)
                "
                :value="questionActivity"
              >
                {{ questionActivity }}
              </option>
            </select></label
          >
          <label
            >题目标题<input
              v-model="questionTitle"
              :disabled="!questionDraft"
              required
              placeholder="便于在题库中查找"
          /></label>
          <label
            >题型<select v-model="questionType" :disabled="!questionDraft">
              <option value="single_choice">单选题</option>
              <option value="boolean">判断题</option>
              <option value="fill">填空题</option>
              <option value="python">编程题</option>
            </select></label
          >
          <label class="full-width"
            >题干<textarea
              v-model="questionPrompt"
              :disabled="!questionDraft"
              required
              rows="4"
              placeholder="填写经过核验的题干"
            ></textarea>
          </label>
          <label v-if="questionType === 'single_choice'" class="full-width"
            >选项 JSON<textarea
              :value="questionFragmentValue('options', [])"
              :disabled="!questionDraft"
              class="json-editor"
              rows="4"
              spellcheck="false"
              @input="
                setQuestionFragment(
                  'options',
                  ($event.target as HTMLTextAreaElement).value,
                )
              "
            ></textarea
            ><span class="field-hint"
              >填写字符串数组，例如 ["选项一", "选项二"]。</span
            ></label
          >
          <label v-if="questionType === 'boolean'"
            >正确答案<select
              v-model="questionAnswer"
              :disabled="!questionDraft"
              required
            >
              <option value="">选择正确答案</option>
              <option value="true">正确（true）</option>
              <option value="false">错误（false）</option>
            </select></label
          >
          <label v-else-if="questionType !== 'python'"
            >正确答案<input
              v-model="questionAnswer"
              :disabled="!questionDraft"
              required
              placeholder="与正确选项或预期填空内容一致"
          /></label>
          <details class="admin-editor full-width">
            <summary>来源与测试用例</summary>
            <p class="field-hint">
              填写真实课程材料及页码。编程题至少提供一个私有测试用例；输入与预期输出的字段由服务端校验。
            </p>
            <label
              >来源 JSON<textarea
                :value="questionFragmentValue('source', {})"
                :disabled="!questionDraft"
                class="json-editor"
                rows="5"
                spellcheck="false"
                @input="
                  setQuestionFragment(
                    'source',
                    ($event.target as HTMLTextAreaElement).value,
                  )
                "
              ></textarea>
            </label>
            <label
              >公开样例 JSON<textarea
                :value="questionFragmentValue('samples', [])"
                :disabled="!questionDraft"
                class="json-editor"
                rows="5"
                spellcheck="false"
                @input="
                  setQuestionFragment(
                    'samples',
                    ($event.target as HTMLTextAreaElement).value,
                  )
                "
              ></textarea>
            </label>
            <label
              >隐藏用例 JSON<textarea
                :value="questionFragmentValue('hidden_cases', [])"
                :disabled="!questionDraft"
                class="json-editor"
                rows="5"
                spellcheck="false"
                @input="
                  setQuestionFragment(
                    'hidden_cases',
                    ($event.target as HTMLTextAreaElement).value,
                  )
                "
              ></textarea>
            </label>
          </details>
          <p
            v-if="questionFragmentErrors.length"
            class="error full-width"
            role="alert"
          >
            {{
              questionFragmentErrors.join("；")
            }}。未完成的输入已保留，请修正后创建。
          </p>
          <details class="admin-editor full-width">
            <summary>完整 JSON（导入或高级编辑）</summary>
            <p class="field-hint">
              完整 JSON 与上方表单同步。粘贴完整定义会替换表单和分项 JSON 内容。
            </p>
            <label
              >题目 JSON<textarea
                :value="questionJson"
                class="json-editor"
                rows="18"
                spellcheck="false"
                @input="
                  replaceQuestionJson(
                    ($event.target as HTMLTextAreaElement).value,
                  )
                "
              ></textarea>
            </label>
          </details>
          <div class="form-actions">
            <Button
              type="submit"
              :disabled="
                saving || !questionDraft || !!questionFragmentErrors.length
              "
              >{{ saving ? "正在创建…" : "创建题目" }}</Button
            >
            <Button variant="outline" @click="questionEditorOpen = false"
              >取消</Button
            >
          </div>
        </form>
        <p class="field-hint">
          公开样例与私有答案、隐藏用例分开保存；学生接口不返回私有内容。
        </p>
      </AdminDialog>
    </template>
  </div>
</template>
<script setup lang="ts">
import {
  computed,
  onMounted,
  onBeforeUnmount,
  ref,
  reactive,
  watch,
} from "vue";
import { Button } from "../components/ui/button";
import AdminDialog from "../components/admin/AdminDialog.vue";
import AdminPagination from "../components/admin/AdminPagination.vue";
import { api, notify } from "../shared/api";
import TeacherCurriculum from "./TeacherCurriculum.vue";
import KnowledgePointSelect from "./KnowledgePointSelect.vue";
import type { KnowledgePoint } from "../shared/curriculum";
import { useSession } from "../shared/session";
import type {
  Activity,
  Chapter,
  Material,
  Npc,
  Question,
} from "../shared/types";
interface TeacherActivity extends Omit<Activity, "material" | "questions"> {
  material_id: number | null;
  knowledge_point_ids: number[];
  owner_id: number | null;
}
const session = useSession(),
  emit = defineEmits<{ updated: [] }>();
const props = defineProps<{ section: string; classroomId: number | null }>(),
  courses = ref<{
    chapters: Chapter[];
    activities: TeacherActivity[];
    npcs: Npc[];
    knowledge_points: KnowledgePoint[];
  }>({ chapters: [], activities: [], npcs: [], knowledge_points: [] }),
  materials = ref<(Material & { error?: string })[]>([]),
  quests = ref<Record<string, unknown>[]>([]),
  questions = ref<(Question & { activity_id: string })[]>([]),
  error = ref(""),
  uploadForm = reactive({ title: "", chapter_id: 179, material_id: "" }),
  uploadBusy = ref(false),
  activity = reactive({
    edit: false,
    id: "",
    title: "",
    chapter_id: 179,
    kind: "lesson",
    npc_key: "mayor_py",
    material_id: "" as string | number,
    assistant_allowed: true,
    knowledge_point_ids: [] as number[],
  }),
  questJson = ref(""),
  validation = ref(""),
  npcJson = ref(
    JSON.stringify(
      {
        key: "new_resident",
        name: "新居民",
        map_key: "town_hub",
        tile_x: 22,
        tile_y: 33,
        portrait: "",
        dialogue: [{ id: "hello", text: "你好，欢迎探索小镇。" }],
      },
      null,
      2,
    ),
  ),
  questionJson = ref(
    JSON.stringify(
      {
        id: "my-concept-1",
        activity_id: "my-practice",
        title: "概念练习",
        kind: "single_choice",
        prompt: "填写经过核验的题干。",
        options: ["选项一", "选项二"],
        answer: "选项一",
        samples: [],
        hidden_cases: [],
        source: { kind: "teacher_authored" },
      },
      null,
      2,
    ),
  ),
  preview = ref<{ kind: string; url: string; text: string } | null>(null);
const uploadEditorOpen = ref(false),
  activityEditorOpen = ref(false),
  questionEditorOpen = ref(false),
  materialSearch = ref(""),
  materialStatus = ref(""),
  activitySearch = ref(""),
  activityChapter = ref(""),
  questionSearch = ref(""),
  questionKind = ref(""),
  saving = ref(false),
  materialPage = ref(1),
  materialPageSize = ref(10),
  questionPage = ref(1),
  questionPageSize = ref(10),
  uploadFile = ref<File>(),
  uploadError = ref(""),
  questionError = ref("");
function openUploadEditor() {
  uploadError.value = "";
  uploadEditorOpen.value = true;
}
function openQuestionEditor() {
  questionError.value = "";
  questionEditorOpen.value = true;
}
watch(
  () => props.section,
  () => {
    uploadEditorOpen.value = false;
    questionEditorOpen.value = false;
  },
);
const questionFragments = reactive<Record<string, string>>({});
const questionDraft = computed<Record<string, unknown> | null>(() => {
  try {
    const value: unknown = JSON.parse(questionJson.value);
    return value !== null && typeof value === "object" && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
});
const questionJsonError = computed(() => {
  try {
    JSON.parse(questionJson.value);
    return questionDraft.value ? "" : "题目 JSON 必须是一个对象";
  } catch {
    return "题目 JSON 格式不正确";
  }
});
const ownQuestionActivities = computed(() =>
  courses.value.activities.filter(
    (a) => session.user?.role === "admin" || a.owner_id === session.user?.id,
  ),
);
function setQuestionField(key: string, value: unknown) {
  if (!questionDraft.value) return;
  questionJson.value = JSON.stringify(
    { ...questionDraft.value, [key]: value },
    null,
    2,
  );
}
function questionTextField(key: string) {
  return computed({
    get: () =>
      typeof questionDraft.value?.[key] === "string"
        ? String(questionDraft.value[key])
        : "",
    set: (value: string) => setQuestionField(key, value),
  });
}
const questionId = questionTextField("id"),
  questionActivity = questionTextField("activity_id"),
  questionTitle = questionTextField("title"),
  questionType = questionTextField("kind"),
  questionPrompt = questionTextField("prompt");
const questionAnswer = computed({
  get: () => {
    const answer = questionDraft.value?.answer;
    return typeof answer === "string"
      ? answer
      : answer == null
        ? ""
        : JSON.stringify(answer);
  },
  set: (value: string) => {
    if (questionType.value === "boolean")
      setQuestionField(
        "answer",
        value === "true" ? true : value === "false" ? false : null,
      );
    else setQuestionField("answer", value);
  },
});
const questionFragmentNames: Record<string, string> = {
  options: "选项",
  source: "来源",
  samples: "公开样例",
  hidden_cases: "隐藏用例",
};
const questionFragmentErrors = computed(() =>
  Object.entries(questionFragments).flatMap(([key, value]) => {
    try {
      JSON.parse(value);
      return [];
    } catch {
      return [`${questionFragmentNames[key] ?? key} JSON 格式不正确`];
    }
  }),
);
function questionFragmentValue(key: string, fallback: unknown) {
  return (
    questionFragments[key] ??
    JSON.stringify(questionDraft.value?.[key] ?? fallback, null, 2)
  );
}
function setQuestionFragment(key: string, value: string) {
  questionFragments[key] = value;
  try {
    setQuestionField(key, JSON.parse(value));
  } catch {
    /* Preserve incomplete JSON until the teacher finishes editing. */
  }
}
function replaceQuestionJson(value: string) {
  questionJson.value = value;
  for (const key of Object.keys(questionFragments))
    delete questionFragments[key];
}
watch(questJson, () => {
  validation.value = "";
});
const materialKinds: Record<string, string> = {
  text: "文本",
  pdf: "PDF",
  presentation: "演示文稿",
  video: "视频",
};
const materialStatuses: Record<string, string> = {
  ready: "可使用",
  queued: "排队中",
  processing: "处理中",
  failed: "处理失败",
  missing: "媒体缺失",
};
const activityKinds: Record<string, string> = {
  lesson: "教学",
  reading: "阅读",
  practice: "练习",
  assessment: "测验",
};
const questionKinds: Record<string, string> = {
  single_choice: "单选题",
  boolean: "判断题",
  fill: "填空题",
  python: "编程题",
};
const filteredMaterials = computed(() =>
  materials.value.filter(
    (m) =>
      (!materialStatus.value || m.status === materialStatus.value) &&
      `${m.title} ${m.id}`
        .toLowerCase()
        .includes(materialSearch.value.trim().toLowerCase()),
  ),
);
const filteredActivities = computed(() =>
  courses.value.activities.filter(
    (a) =>
      (!activityChapter.value ||
        String(a.chapter_id) === activityChapter.value) &&
      `${a.title} ${a.id}`
        .toLowerCase()
        .includes(activitySearch.value.trim().toLowerCase()),
  ),
);
const filteredQuestions = computed(() =>
  questions.value.filter(
    (q) =>
      (!questionKind.value || q.kind === questionKind.value) &&
      `${q.title} ${q.id} ${q.activity_id} ${courses.value.activities.find((a) => a.id === q.activity_id)?.title ?? ""}`
        .toLowerCase()
        .includes(questionSearch.value.trim().toLowerCase()),
  ),
);
const pagedMaterials = computed(() =>
  filteredMaterials.value.slice(
    (materialPage.value - 1) * materialPageSize.value,
    materialPage.value * materialPageSize.value,
  ),
);
const pagedQuestions = computed(() =>
  filteredQuestions.value.slice(
    (questionPage.value - 1) * questionPageSize.value,
    questionPage.value * questionPageSize.value,
  ),
);
watch([materialSearch, materialStatus, materialPageSize], () => {
  materialPage.value = 1;
});
watch([questionSearch, questionKind, questionPageSize], () => {
  questionPage.value = 1;
});
watch(
  () => filteredMaterials.value.length,
  (total) => {
    materialPage.value = Math.min(
      materialPage.value,
      Math.max(1, Math.ceil(total / materialPageSize.value)),
    );
  },
);
watch(
  () => filteredQuestions.value.length,
  (total) => {
    questionPage.value = Math.min(
      questionPage.value,
      Math.max(1, Math.ceil(total / questionPageSize.value)),
    );
  },
);
function chapterTitle(id: number) {
  return (
    courses.value.chapters.find((c) => c.id === id)?.title ?? `章节 #${id}`
  );
}
function startActivity() {
  Object.assign(activity, {
    edit: false,
    id: "",
    title: "",
    chapter_id: courses.value.chapters[0]?.id ?? 179,
    kind: "lesson",
    npc_key: courses.value.npcs[0]?.key ?? "mayor_py",
    material_id: "",
    assistant_allowed: true,
    knowledge_point_ids: [],
  });
  activityEditorOpen.value = true;
}
let timer: ReturnType<typeof setTimeout> | undefined;
async function safe(job: () => Promise<void>, mutation = false) {
  if (mutation && saving.value) return;
  if (mutation) saving.value = true;
  try {
    error.value = "";
    await job();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
    notify(error.value);
  } finally {
    if (mutation) saving.value = false;
  }
}
async function loadCourses() {
  courses.value = await api.get("/teacher/courses");
}
async function curriculumUpdated() {
  await loadCourses();
  emit("updated");
}
async function loadQuests() {
  quests.value = await api.get("/teacher/quests");
}
async function loadQuestions() {
  questions.value = await api.get("/teacher/questions");
}
async function loadMaterials() {
  await safe(async () => {
    materials.value = await api.get("/teacher/materials");
    clearTimeout(timer);
    if (
      materials.value.some((m) => ["queued", "processing"].includes(m.status))
    )
      timer = setTimeout(() => {
        void loadMaterials();
      }, 2000);
  });
}
function selectFile(e: Event) {
  uploadFile.value =
    (e.target as HTMLInputElement).files?.[0] ?? uploadFile.value;
}
async function upload() {
  if (!uploadFile.value || uploadBusy.value) return;
  uploadBusy.value = true;
  uploadError.value = "";
  await safe(async () => {
    const form = new FormData();
    form.set("file", uploadFile.value!);
    form.set("title", uploadForm.title);
    form.set("chapter_id", String(uploadForm.chapter_id));
    if (uploadForm.material_id) form.set("material_id", uploadForm.material_id);
    await api.request("/teacher/materials/upload", {
      method: "POST",
      body: form,
    });
    await loadMaterials();
    notify("已上传，处理状态将自动更新。");
    uploadEditorOpen.value = false;
  });
  if (uploadEditorOpen.value && error.value) uploadError.value = error.value;
  uploadBusy.value = false;
}
function editActivity(a: TeacherActivity) {
  Object.assign(activity, {
    ...a,
    material_id: a.material_id ?? "",
    edit: true,
  });
  activityEditorOpen.value = true;
}
async function saveActivity() {
  await safe(async () => {
    const fields = {
      title: activity.title,
      npc_key: activity.npc_key,
      assistant_allowed: activity.assistant_allowed,
      material_id: activity.material_id || null,
      knowledge_point_ids: activity.knowledge_point_ids,
    };
    if (activity.edit)
      await api.put(`/teacher/activities/${activity.id}`, fields);
    else
      await api.post("/teacher/activities", {
        ...fields,
        id: activity.id,
        kind: activity.kind,
        chapter_id: activity.chapter_id,
      });
    await loadCourses();
    notify("活动已保存。");
  }, true);
}
function selectQuest(e: Event) {
  const index = (e.target as HTMLSelectElement).value;
  if (index === "") return;
  const q = quests.value[Number(index)];
  questJson.value = JSON.stringify(q?.definition ?? q, null, 2);
}
async function validateQuest() {
  await safe(async () => {
    const definition = questJson.value;
    const result = await api.post("/teacher/quests/validate", {
      definition: JSON.parse(definition),
    });
    if (definition === questJson.value)
      validation.value = JSON.stringify(result, null, 2);
  }, true);
}
async function publishQuest() {
  await safe(async () => {
    await api.post("/teacher/quests", {
      definition: JSON.parse(questJson.value),
    });
    await loadQuests();
    notify("任务配置已保存。");
  }, true);
}
async function createNpc() {
  await safe(async () => {
    await api.post("/teacher/npcs", JSON.parse(npcJson.value));
    await loadCourses();
    notify("NPC已创建。");
  }, true);
}
async function createQuestion() {
  if (!questionDraft.value || questionFragmentErrors.value.length) return;
  questionError.value = "";
  await safe(async () => {
    await api.post<{ id: string }>("/teacher/questions", JSON.parse(questionJson.value));
    await Promise.all([loadQuestions(), loadCourses()]);
    notify("题目已创建并加入当前活动。");
    questionEditorOpen.value = false;
  }, true);
  if (questionEditorOpen.value && error.value)
    questionError.value = error.value;
}
function closePreview() {
  if (preview.value?.url) URL.revokeObjectURL(preview.value.url);
  preview.value = null;
}
async function previewMaterial(m: Material) {
  await safe(async () => {
    closePreview();
    if (m.kind === "text") {
      const content = await api.get<Material>(
        `/materials/${m.id}?classroom_id=${props.classroomId}`,
      );
      preview.value = {
        kind: "text",
        url: "",
        text: content.pages
          .map((p) => `第${p.page_no}页\n${p.text}`)
          .join("\n\n"),
      };
    } else {
      const blob = await api.blob(
        `/materials/${m.id}/file?classroom_id=${props.classroomId}`,
      );
      preview.value = {
        kind: m.kind,
        url: URL.createObjectURL(blob),
        text: "",
      };
    }
  });
}
onMounted(() =>
  safe(async () => {
    await Promise.all([
      loadCourses(),
      loadMaterials(),
      loadQuests(),
      loadQuestions(),
    ]);
  }),
);
onBeforeUnmount(() => {
  clearTimeout(timer);
  closePreview();
});
</script>
