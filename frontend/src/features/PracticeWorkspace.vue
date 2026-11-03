<template>
  <div class="practice-workspace learning-layout">
    <section class="practice-content">
      <header class="content-heading">
        <p class="eyebrow">练习活动 · 正式成绩由服务端判定</p>
        <h2>{{ activity?.title }}</h2>
      </header>
      <p v-if="error" class="error">{{ error }}</p>
      <nav class="question-list" aria-label="题目列表">
        <button
          v-for="(question, index) in activity?.questions ?? []"
          :key="question.id"
          :class="{ selected: question.id === current?.id }"
          @click="questionIndex = index"
        >
          {{ index + 1 }} · {{ question.title }}
        </button>
      </nav>
      <article v-if="current" class="question-body">
        <span class="question-kind">{{ kindName[current.kind] }}</span>
        <h3>{{ current.title }}</h3>
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
              :value="
                current.kind === 'boolean'
                  ? index === 0
                    ? 'true'
                    : 'false'
                  : option
              "
            />{{ option }}</label
          ></template
        ><label v-else-if="current.kind === 'fill'"
          >填写答案<input
            v-model="answer"
            placeholder="输入你的答案"
            autocomplete="off" /></label
        ><template v-else
          ><div
            v-for="(sample, i) in current.samples ?? []"
            :key="i"
            class="sample"
          >
            <strong>公开样例 {{ i + 1 }}</strong>
            <pre>
输入：{{ sample.input }}
输出：{{ sample.output }}</pre
            >
          </div>
          <CodeEditor v-model="code"
        /></template>
        <div class="submission-actions">
          <button
            v-if="current.kind === 'python'"
            :disabled="busy"
            @click="submit('run')"
          >
            运行公开样例</button
          ><button
            class="primary"
            :disabled="busy || (!code.trim() && current.kind === 'python')"
            @click="submit('submit')"
          >
            {{ busy ? "等待判定…" : "正式提交" }}</button
          ><span>草稿按账号与题目保存</span>
        </div>
        <div v-if="result" class="submission-result" role="status">
          <strong>{{ statusName[result.status] ?? result.status }}</strong>
          <p v-if="result.feedback.detail">{{ result.feedback.detail }}</p>
          <section
            v-for="(sample, index) in result.feedback.samples ?? []"
            :key="index"
            class="public-sample-feedback"
            aria-label="公开样例反馈"
          >
            <h4>公开样例 {{ index + 1 }} · {{ statusName[sample.status] }}</h4>
            <p><strong>标准输出</strong></p>
            <pre>{{ sample.stdout || "（无输出）" }}</pre>
            <template v-if="sample.stderr"
              ><p><strong>错误输出</strong></p>
              <pre>{{ sample.stderr }}</pre></template
            ><template v-if="sample.compile_output"
              ><p><strong>编译反馈</strong></p>
              <pre>{{ sample.compile_output }}</pre>
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
      <p v-if="activity && !activity.questions?.length" class="notice">
        此活动尚未绑定公开题目，请联系教师。
      </p>
    </section>
    <button
      class="mobile-assistant-toggle"
      @click="showAssistant = !showAssistant"
    >
      {{ showAssistant ? "返回练习" : "向助教求助" }}</button
    ><AssistantPanel
      v-if="activity"
      :class="{ 'mobile-visible': showAssistant }"
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
  watch,
  defineAsyncComponent,
} from "vue";
import { api, notify } from "../shared/api";
import { useSession } from "../shared/session";
import type { Activity, Submission, SubmissionStatus } from "../shared/types";
import AssistantPanel from "./AssistantPanel.vue";
const CodeEditor = defineAsyncComponent(() => import("./CodeEditor.vue")),
  props = defineProps<{ activityId: string }>(),
  session = useSession(),
  activity = ref<Activity>(),
  questionIndex = ref(0),
  answer = ref(""),
  code = ref(""),
  error = ref(""),
  busy = ref(false),
  result = ref<Submission>(),
  showAssistant = ref(false);
const contextUserId = session.user?.id,
  contextClassroomId = session.classroomId;
let timer: ReturnType<typeof setTimeout> | undefined,
  closed = false,
  pendingKey = "";
const current = computed(() => activity.value?.questions[questionIndex.value]),
  roleName = computed(
    () =>
      session.world?.npcs.find((n) => n.key === activity.value?.npc_key)
        ?.name ?? "练习助教",
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
async function load() {
  try {
    activity.value = await session.activity(props.activityId);
    questionIndex.value = 0;
    restore();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "题目加载失败";
  }
}
function restore() {
  if (!current.value) return;
  const raw = localStorage.getItem(key.value);
  const draft = raw
    ? (JSON.parse(raw) as {
        code: string;
        answer: string;
        submission_id?: string;
      })
    : null;
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
  if (!current.value || !activity.value) return;
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
    result.value = await api.get<Submission>(
      `/submissions/${id}?classroom_id=${session.classroomId}`,
    );
    save();
    await handleResult();
  } catch (e) {
    notify(e instanceof Error ? e.message : "结果读取失败");
  }
}
function cite(_id: number, _version: number, page: number) {
  const lesson = session.world?.quests
    .flatMap((q) => q.nodes)
    .find((n) => n.type === "STUDY_LESSON" && n.activity_id);
  if (lesson?.activity_id)
    session.open({
      kind: "lesson",
      activity_id: lesson.activity_id,
      page_no: page,
    });
}
watch(current, restore);
watch([answer, code], save);
watch(() => props.activityId, load);
onMounted(load);
onBeforeUnmount(() => {
  closed = true;
  clearTimeout(timer);
  save();
});
</script>
