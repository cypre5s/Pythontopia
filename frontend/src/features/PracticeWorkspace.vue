<template>
  <div
    class="practice-workspace learning-layout"
    :class="{ 'is-compact': compact }"
  >
    <nav
      v-if="activity && compact"
      class="workspace-view-switch"
      aria-label="练习与助教视图"
    >
      <button
        :aria-pressed="!showAssistant"
        aria-controls="practice-content-region"
        @click="switchView(false)"
      >
        题目与代码</button
      ><button
        :aria-pressed="showAssistant"
        aria-controls="practice-assistant-region"
        @click="switchView(true)"
      >
        向助教求助
      </button>
    </nav>
    <section
      id="practice-content-region"
      class="practice-content"
      tabindex="-1"
      aria-label="题目与代码"
      :hidden="compact && showAssistant"
      :inert="compact && showAssistant"
      :aria-busy="loading"
    >
      <header class="content-heading">
        <p class="eyebrow">练习活动</p>
        <h2>{{ activity?.title ?? "正在读取练习" }}</h2>
        <div class="learning-meta">
          <span>正式提交后记录成绩</span
          >
          <span v-if="storySessionId && activityProgress">已完成 {{ activityProgress.accepted_question_ids?.length ?? 0 }} / {{ activity?.questions.length ?? 0 }} 题</span>
        </div>
      </header>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="loading" class="learning-empty" role="status">正在读取题目…</p>
      <nav class="question-list" aria-label="题目列表">
        <button
          v-for="(question, index) in activity?.questions ?? []"
          :key="question.id"
          :class="{ selected: question.id === current?.id }"
          :aria-current="question.id === current?.id ? 'step' : undefined"
          :disabled="busy"
          @click="questionIndex = index"
        >
          <span>{{ index + 1 }}</span
          ><span>{{ question.title }}</span>
        </button>
      </nav>
      <article
        v-if="current"
        class="question-body"
        aria-labelledby="practice-question-title"
      >
        <p class="question-position">
          当前为第 <strong>{{ questionIndex + 1 }}</strong> /
          {{ activity?.questions.length }} 题 · 可重复练习
        </p>
        <span class="question-kind">{{ kindName[current.kind] }}</span>
        <h3 id="practice-question-title">{{ current.title }}</h3>
        <p class="question-prompt">{{ current.prompt }}</p>
        <template
          v-if="current.kind === 'single_choice' || current.kind === 'boolean'"
          ><label
            v-for="(option, index) in current.options?.length
              ? current.options
              : ['正确', '错误']"
            :key="index"
            class="answer-option"
            ><input
              v-model="answer"
              type="radio"
              name="practice-answer"
              :value="
                current.kind === 'boolean'
                  ? index === 0
                    ? 'true'
                    : 'false'
                  : option
              "
            />{{ option }}</label
          ></template
        >
        <label v-else-if="current.kind === 'fill'"
          >填写答案<input
            v-model="answer"
            placeholder="输入你的答案"
            autocomplete="off"
        /></label>
        <template v-else
          ><div
            v-for="(sample, index) in current.samples ?? []"
            :key="index"
            class="sample"
          >
            <strong>公开样例 {{ index + 1 }}</strong>
            <pre>
输入：{{ sample.input }}
输出：{{ sample.output }}</pre
            >
          </div>
          <CodeEditor v-model="code"
        /></template>
        <p v-if="!hasAnswer && current.kind !== 'python'" class="answer-hint">
          {{
            current.kind === "fill"
              ? "填写答案后再正式提交。"
              : "先选择一个答案，再正式提交。"
          }}
        </p>
        <div class="submission-actions">
          <button
            v-if="current.kind === 'python'"
            :disabled="busy || !hasAnswer"
            @click="submit('run')"
          >
            运行公开样例</button
          ><button
            class="primary"
            :disabled="busy || !hasAnswer"
            @click="submit('submit')"
          >
            {{ busy ? "等待判定…" : "正式提交" }}
          </button>
        </div>
        <p class="draft-note">
          草稿自动保存，可稍后继续。运行公开样例不计成绩，正式提交才记录结果。
        </p>
        <div
          v-if="result"
          class="submission-result"
          :data-status="result.status"
        >
          <header>
            <span class="result-symbol" aria-hidden="true">{{
              result.status === "accepted"
                ? "✓"
                : ["prepared", "dispatching", "queued", "running"].includes(
                      result.status,
                    )
                  ? "◷"
                  : "!"
            }}</span>
            <div>
              <p class="eyebrow">
                {{ result.mode === "run" ? "公开样例运行" : "正式提交结果" }}
              </p>
              <strong role="status">{{ statusName[result.status] }}</strong>
            </div>
          </header>
          <p v-if="result.feedback.detail">{{ result.feedback.detail }}</p>
          <section
            v-for="(sample, index) in result.feedback.samples ?? []"
            :key="index"
            class="public-sample-feedback"
            aria-label="公开样例反馈"
          >
            <h4>公开样例 {{ index + 1 }} · {{ statusName[sample.status] }}</h4>
            <h5>标准输出</h5>
            <pre tabindex="0">{{ sample.stdout || "（无输出）" }}</pre>
            <template v-if="sample.stderr"
              ><h5>错误输出</h5>
              <pre tabindex="0">{{ sample.stderr }}</pre></template
            ><template v-if="sample.compile_output"
              ><h5>编译反馈</h5>
              <pre tabindex="0">{{ sample.compile_output }}</pre>
            </template>
          </section>
          <small
            >提交 {{ result.id }} ·
            {{
              result.mode === "run" ? "公开样例运行，不计成绩" : "正式提交"
            }}</small
          >
        </div>
      </article>
      <p
        v-if="activity && !loading && !activity.questions.length"
        class="learning-empty"
      >
        此活动尚未绑定公开题目，请联系教师。
      </p>
      <button v-if="storySessionId && activityProgress?.completed" class="primary" @click="$emit('complete')">完成练习</button>
    </section>
    <AssistantPanel
      v-if="activity"
      :key="activity.id"
      id="practice-assistant-region"
      tabindex="-1"
      :hidden="compact && !showAssistant"
      :inert="compact && !showAssistant"
      :aria-hidden="compact && !showAssistant"
      :activity-id="activity.id"

      :allowed="activity.assistant_allowed"
      :code="code"
      :role-name="roleName"
      @cite="cite"
    />
  </div>
</template>
<script setup lang="ts">
import {
  computed,
  ref,
  onMounted,
  onBeforeUnmount,
  nextTick,
  watch,
  defineAsyncComponent,
} from "vue";
import { api, notify } from "../shared/api";
import { useSession } from "../shared/session";
import type { Activity, ActivityProgress, Submission, SubmissionStatus } from "../shared/types";
import AssistantPanel from "./AssistantPanel.vue";
import "./learningUi.css";
const CodeEditor = defineAsyncComponent(() => import("./CodeEditor.vue")),
  props = defineProps<{ activityId: string; storySessionId?: string }>(),
  session = useSession(),
  activity = ref<Activity>(),
  questionIndex = ref(0),
  answer = ref(""),
  code = ref(""),
  error = ref(""),
  loading = ref(true),
  busy = ref(false),
  result = ref<Submission>(),
  showAssistant = ref(false),
  compact = ref(false);
const emit = defineEmits<{ complete: [] }>();
const activityProgress = ref<ActivityProgress>();
const contextUserId = session.user?.id,
  contextClassroomId = session.classroomId;
let timer: ReturnType<typeof setTimeout> | undefined,
  closed = false,
  pendingKey = "",
  compactMedia: MediaQueryList | undefined;
const current = computed(() => activity.value?.questions[questionIndex.value]),
  roleName = computed(
    () =>
      session.world?.npcs.find((n) => n.key === activity.value?.npc_key)
        ?.name ?? "练习助教",
  ),
  hasAnswer = computed(() =>
    Boolean(
      current.value &&
        (current.value.kind === "python"
          ? code.value.trim()
          : answer.value.trim()),
    ),
  );
const kindName = {
  single_choice: "单选题",
  boolean: "判断题",
  fill: "填空题",
  python: "Python 编程题",
};
const statusName: Record<SubmissionStatus, string> = {
  prepared: "已保存，等待送交",
  dispatching: "正在送交判题",
  compile_error: "编译错误",
  queued: "已排队",
  running: "正在运行",
  accepted: "通过",
  wrong_answer: "答案未通过",
  runtime_error: "运行错误",
  time_limit: "超出时间限制",
  memory_limit: "超出内存限制",
  infrastructure_error: "判题服务故障，未记为答错",
  submission_unknown: "判题送交状态不确定，需要教师对账，请勿重复提交",
};
const key = computed(
  () => `draft:${contextUserId}:${contextClassroomId}:${current.value?.id}`,
);
function updateCompact() {
  if (!compactMedia) return;
  if (compactMedia.matches && !compact.value)
    showAssistant.value = Boolean(
      document
        .getElementById("practice-assistant-region")
        ?.contains(document.activeElement),
    );
  compact.value = compactMedia.matches;
}
async function switchView(assistant: boolean) {
  showAssistant.value = assistant;
  await nextTick();
  document
    .getElementById(
      assistant ? "practice-assistant-region" : "practice-content-region",
    )
    ?.focus({ preventScroll: true });
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    activity.value = await session.activity(props.activityId);
    activityProgress.value = activity.value.progress;
    questionIndex.value = 0;
    restore();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "题目加载失败";
  } finally {
    loading.value = false;
  }
}
function restore() {
  clearTimeout(timer);
  if (!current.value) return;
  const raw = localStorage.getItem(key.value);
  let draft: { code: string; answer: string; submission_id?: string } | null =
    null;
  try {
    draft = raw ? JSON.parse(raw) : null;
  } catch {
    localStorage.removeItem(key.value);
  }
  code.value = draft?.code ?? "";
  answer.value = draft?.answer ?? "";
  result.value = undefined;
  if (draft?.submission_id) void poll(draft.submission_id);
}
function save() {
  if (current.value)
    localStorage.setItem(
      key.value,
      JSON.stringify({
        code: code.value,
        answer: answer.value,
        submission_id: result.value?.id,
      }),
    );
}
async function submit(mode: "run" | "submit") {
  if (!current.value || !activity.value || !hasAnswer.value || busy.value)
    return;
  busy.value = true;
  try {
    pendingKey ||= crypto.randomUUID();
    result.value = await api.post<Submission>("/submissions", {
      classroom_id: session.classroomId,
      activity_id: activity.value.id,
      question_id: current.value.id,
      idempotency_key: pendingKey,
      answer:
        current.value.kind === "boolean"
          ? answer.value === "true"
          : answer.value,
      code: code.value,
      mode,
    });
    pendingKey = "";
    save();
    await handleResult();
  } catch (e) {
    notify(e instanceof Error ? e.message : "提交失败");
  } finally {
    busy.value = false;
  }
}
async function handleResult() {
  if (
    !result.value ||
    closed ||
    session.user?.id !== contextUserId ||
    session.classroomId !== contextClassroomId
  )
    return;
  if (
    ["prepared", "dispatching", "queued", "running"].includes(
      result.value.status,
    )
  ) {
    timer = setTimeout(() => {
      if (result.value && !closed) void poll(result.value.id);
    }, 1500);
    return;
  }
  if (result.value.status === "accepted" && result.value.mode === "submit") {
    if (props.storySessionId) {
      const progress = await api.get<ActivityProgress>(`/activities/${props.activityId}/progress?classroom_id=${contextClassroomId}`);
      if (closed || session.user?.id !== contextUserId || session.classroomId !== contextClassroomId) return;
      activityProgress.value = progress;
      if (progress.completed) emit("complete");
      return;
    }
    for (const quest of session.world?.quests ?? [])
      for (const node of quest.nodes)
        if (
          node.status === "ready" &&
          ["COMPLETE_PRACTICE", "PASS_ASSESSMENT"].includes(node.type) &&
          node.activity_id === props.activityId
        ) {
          try {
            await session.event(quest, node, {
              submission_id: result.value.id,
            });
          } catch (e) {
            notify(e instanceof Error ? e.message : "任务仍有其他待完成练习");
          }
        }
    await session.refresh();
  }
}
async function poll(id: string) {
  try {
    const received = await api.get<Submission>(
      `/submissions/${id}?classroom_id=${contextClassroomId}`,
    );
    if (closed || received.question_id !== current.value?.id) return;
    result.value = received;
    save();
    await handleResult();
  } catch (e) {
    if (!closed) notify(e instanceof Error ? e.message : "结果读取失败");
  }
}
function cite(id: number, page: number) {
  session.navigate({
    kind: "material",
    material_id: id,
    page_no: page,
  });
}
watch(current, restore);
watch([answer, code], save);
watch(() => props.activityId, load);
onMounted(() => {
  compactMedia = window.matchMedia?.("(max-width: 900px), (max-height: 540px)");
  updateCompact();
  compactMedia?.addEventListener("change", updateCompact);
  void load();
});
onBeforeUnmount(() => {
  closed = true;
  clearTimeout(timer);
  compactMedia?.removeEventListener("change", updateCompact);
  save();
});
</script>
