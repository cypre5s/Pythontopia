<template>
  <section class="assistant-panel" aria-label="课程助教">
    <header>
      <div>
        <p class="eyebrow">当前学习助教</p>
        <h3>{{ roleName }}</h3>
      </div>
      <span
        class="status-dot"
        :class="{ online: available && allowed }"
        aria-hidden="true"
      ></span>
    </header>
    <p class="assistant-state" role="status">
      {{
        !allowed
          ? "本活动未开放助教"
          : available
            ? "服务就绪 · 由你主动提问"
            : "助教暂不可用"
      }}
    </p>
    <p class="assistant-intro">
      围绕当前内容提问，获取概念与错误提示。按自己的节奏阅读，需要时再发送问题。
    </p>
    <p v-if="!allowed" class="notice">
      本次活动不允许使用助教，可以继续独立阅读或练习。
    </p>
    <p v-else-if="!available" class="notice">
      {{ unavailable || "助教尚未配置，请继续阅读或练习。" }}
    </p>
    <div
      ref="history"
      class="chat-history"
      role="log"
      aria-label="助教会话记录"
      aria-live="polite"
    >
      <p v-if="!messages.length" class="assistant-empty">
        {{
          allowed && available
            ? "从一个具体问题开始，例如：这一页的概念应该怎样理解？"
            : "课程与练习仍可正常使用，学习进度不会因此受到影响。"
        }}
      </p>
      <article
        v-for="(item, index) in messages"
        :key="index"
        :class="item.role"
      >
        <small>{{ item.role === "user" ? "你的问题" : roleName }}</small>
        <p>{{ item.text }}</p>
      </article>
      <button
        v-for="refItem in references"
        :key="`${refItem.material_id}-${refItem.page_no}`"
        class="citation"
        @click="
          $emit('cite', refItem.material_id, refItem.version, refItem.page_no)
        "
      >
        {{ refItem.title }} · 第{{ refItem.page_no }}页 <GameIcon name="external"/>
      </button>
    </div>
    <form @submit.prevent="send">
      <label class="assistant-composer-label" for="assistant-message"
        >向助教提问</label
      ><textarea
        id="assistant-message"
        v-model="question"
        placeholder="描述你卡住的概念或错误…"
        rows="3"
        :disabled="!available || !allowed || busy"
        maxlength="2000"
      ></textarea>
      <div class="assistant-form-actions">
        <span class="assistant-character-count"
          >{{ question.length }} / 2000 字</span
        ><button v-if="busy" type="button" @click="cancel">停止回复</button
        ><button
          v-else
          class="primary"
          :disabled="!available || !allowed || !question.trim()"
        >
          发送
        </button>
      </div>
    </form>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { nextTick, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { api, notify } from "../shared/api";
import "./learningUi.css";
import { useSession } from "../shared/session";
const props = defineProps<{
  activityId: string;
  allowed: boolean;
  pageNo?: number;
  videoTime?: number;
  code?: string;
  roleName: string;
}>();
defineEmits<{ cite: [materialId: number, version: number, page: number] }>();
const session = useSession(),
  available = ref(false),
  unavailable = ref(""),
  question = ref(""),
  busy = ref(false),
  history = ref<HTMLElement>(),
  messages = ref<{ role: string; text: string }[]>([]),
  references = ref<
    { material_id: number; version: number; page_no: number; title: string }[]
  >([]);
let sessionId = "",
  requestId = "",
  controller: AbortController | undefined;
async function status() {
  try {
    const s = await api.get<{ available: boolean; reason: string }>(
      "/assistant/status",
    );
    available.value = s.available;
    unavailable.value = s.reason;
  } catch {
    unavailable.value = "助教服务暂时不可用";
  }
}
function cancel() {
  controller?.abort();
  if (requestId)
    void api
      .post(`/assistant/requests/${requestId}/cancel`, {})
      .catch(() => undefined);
  requestId = "";
  busy.value = false;
}
async function send() {
  if (!question.value.trim() || busy.value || !props.allowed) return;
  busy.value = true;
  controller = new AbortController();
  const text = question.value;
  question.value = "";
  messages.value.push({ role: "user", text }, { role: "assistant", text: "" });
  const reply = messages.value[messages.value.length - 1]!;
  try {
    if (!sessionId) {
      const s = await api.post<{ id: string }>("/assistant/sessions", {
        classroom_id: session.classroomId,
        activity_id: props.activityId,
      });
      sessionId = s.id;
      sessionStorage.setItem(
        `assistant:${session.user?.id}:${session.classroomId}:${props.activityId}`,
        sessionId,
      );
    }
    const res = await fetch(`/api/assistant/sessions/${sessionId}/messages`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${api.token()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: text,
        page_no: props.pageNo,
        video_time: props.videoTime,
        code: props.code,
      }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const e = await res.json();
      throw new Error(e.detail ?? "助教调用失败");
    }
    const reader = res.body?.getReader();
    if (!reader) throw new Error("未收到流式响应");
    let buffer = "";
    const decoder = new TextDecoder();
    while (!controller.signal.aborted) {
      const part = await reader.read();
      if (part.done) break;
      buffer += decoder.decode(part.value, { stream: true });
      buffer = buffer.replace(/\r\n/g, "\n");
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const chunk = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const event = chunk
          .split("\n")
          .find((l) => l.startsWith("event:"))
          ?.slice(6)
          .trim();
        const raw = chunk
          .split("\n")
          .filter((l) => l.startsWith("data:"))
          .map((l) => l.slice(5).trim())
          .join("\n");
        if (raw) {
          const data = JSON.parse(raw) as {
            text?: string;
            detail?: string;
            request_id?: string;
            items?: typeof references.value;
          };
          if (event === "meta") requestId = data.request_id ?? "";
          if (event === "token") reply.text += data.text ?? "";
          if (event === "references") references.value = data.items ?? [];
          if (event === "error") throw new Error(data.detail ?? "助教服务错误");
        }
        boundary = buffer.indexOf("\n\n");
      }
      await nextTick();
      history.value?.scrollTo({ top: history.value.scrollHeight });
    }
  } catch (e) {
    if (e instanceof Error && e.name === "AbortError") {
      reply.text += "\n[已停止]";
    } else {
      const detail = e instanceof Error ? e.message : "助教调用失败";
      reply.text += `\n${detail}`;
      notify(detail);
    }
  } finally {
    busy.value = false;
  }
}
watch(
  () => props.activityId,
  () => {
    cancel();
    sessionId = "";
    messages.value = [];
    references.value = [];
  },
);
onMounted(async () => {
  await status();
  sessionId =
    sessionStorage.getItem(
      `assistant:${session.user?.id}:${session.classroomId}:${props.activityId}`,
    ) ?? "";
  if (sessionId) {
    try {
      const saved = await api.get<{
        messages: { role: string; content: string }[];
      }>(`/assistant/sessions/${sessionId}`);
      messages.value = saved.messages.map((m) => ({
        role: m.role,
        text: m.content,
      }));
    } catch {
      sessionId = "";
    }
  }
});
onBeforeUnmount(cancel);
</script>
