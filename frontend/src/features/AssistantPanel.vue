<template>
  <section class="assistant-panel assistant-conversation" :aria-label="`${roleName}助教`">
    <img v-if="portraitUrl" class="assistant-portrait" :src="portraitUrl" alt="" aria-hidden="true" />
    <header class="assistant-heading">
      <div class="assistant-identity">
        <span class="assistant-emblem"><GameIcon name="message" /></span>
        <h3>{{ roleName }}</h3>
      </div>
      <span
        class="status-dot"
        :class="{ online: available && allowed }"
        aria-hidden="true"
      ></span>
    </header>
    <div
      ref="history"
      class="chat-history"
      role="log"
      aria-label="助教会话记录"
      aria-live="polite"
    >
      <p v-if="!messages.length" class="assistant-empty">
        阅读或练习时遇到问题，可以在这里问我。
      </p>
      <article
        v-for="(item, index) in messages"
        :key="index"
        :class="item.role"
      >
        <span class="assistant-sr-only">{{ item.role === "user" ? "你的问题" : roleName }}：</span>
        <p>{{ item.text }}</p>
      </article>
      <button
        v-for="refItem in references"
        :key="`${refItem.material_id}-${refItem.page_no}`"
        class="citation"
        @click="
          $emit('cite', refItem.material_id, refItem.page_no)
        "
      >
        {{ refItem.title }} · 第{{ refItem.page_no }}页 <GameIcon name="external"/>
      </button>
    </div>
    <p v-if="!allowed" class="assistant-service-notice" role="status">
      本活动未开放助教，可以继续独立学习。
    </p>
    <p v-else-if="!available" class="assistant-service-notice" role="status">
      {{ unavailable || "助教暂不可用，可以继续阅读或练习。" }}
    </p>
    <p v-else-if="busy" class="assistant-service-notice" role="status">正在回复…</p>
    <form class="assistant-composer" @submit.prevent="send">
      <label class="assistant-sr-only" :for="inputId"
        >向助教提问</label
      ><textarea
        :id="inputId"
        v-model="question"
        placeholder="向助教提问"
        rows="2"
        :disabled="!available || !allowed || busy"
        :aria-invalid="overLimit ? 'true' : undefined"
        :aria-describedby="overLimit ? `${inputId}-error` : undefined"
      ></textarea>
      <p v-if="overLimit" :id="`${inputId}-error`" class="assistant-input-error" role="alert">问题最多 2000 字，请删减 {{ questionLength - 2000 }} 字后发送。</p>
      <div class="assistant-form-actions">
        <button v-if="busy" type="button" @click="cancel">停止回复</button
        ><button
          v-else
          type="submit"
          class="primary assistant-send"
          aria-label="发送"
          title="发送"
          :disabled="!available || !allowed || !question.trim() || overLimit"
        >
          <GameIcon name="arrowUp" />
        </button>
      </div>
    </form>
  </section>
</template>
<script setup lang="ts">
import GameIcon from "../shared/GameIcon.vue";
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { api, notify } from "../shared/api";
import "./assistantUi.css";
import { useSession } from "../shared/session";
const props = defineProps<{
  activityId: string;

  allowed: boolean;
  pageNo?: number;
  videoTime?: number;
  code?: string;
  roleName: string;
  portraitUrl?: string;
}>();
defineEmits<{ cite: [materialId: number, page: number] }>();
const session = useSession(),
  available = ref(false),
  unavailable = ref(""),
  question = ref(""),
  busy = ref(false),
  history = ref<HTMLElement>(),
  messages = ref<{ role: string; text: string }[]>([]),
  references = ref<
    { material_id: number; page_no: number; title: string }[]
  >([]);
const storageKey = computed(() => `assistant:${session.user?.id}:${session.classroomId}:${props.activityId}`);
const inputId = computed(() => `assistant-message-${props.activityId}`);
const questionLength = computed(() => Array.from(question.value).length);
const overLimit = computed(() => questionLength.value > 2000);
let sessionId = "",
  requestId = "",
  controller: AbortController | undefined,
  generation = 0;
async function restore() {
  const current = ++generation;
  cancel();
  sessionId = "";
  available.value = false;
  unavailable.value = "正在连接助教…";
  question.value = "";
  messages.value = [];
  references.value = [];
  const key = storageKey.value;
  try {
    const s = await api.get<{ available: boolean; reason: string }>(
      "/assistant/status",
    );
    if (current !== generation) return;
    available.value = s.available;
    unavailable.value = s.reason;
  } catch {
    if (current !== generation) return;
    unavailable.value = "助教服务暂时不可用";
  }
  if (current !== generation) return;
  const savedId = sessionStorage.getItem(key) ?? "";
  if (!savedId) return;
  try {
    const saved = await api.get<{ messages: { role: string; content: string }[] }>(`/assistant/sessions/${savedId}`);
    if (current !== generation) return;
    sessionId = savedId;
    messages.value = saved.messages.filter(m => m.role === "user" || m.role === "assistant").map(m => ({ role: m.role, text: m.content }));
  } catch {
    // Keep the recovery pointer when history is temporarily unavailable.
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
  if (!question.value.trim() || overLimit.value || busy.value || !props.allowed || !available.value) return;
  const current = generation, key = storageKey.value;
  busy.value = true;
  const activeController = new AbortController();
  controller = activeController;
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
      if (current !== generation || activeController.signal.aborted) return;
      sessionId = s.id;
      sessionStorage.setItem(
        key,
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
      signal: activeController.signal,
    });
    if (current !== generation) return;
    if (!res.ok) {
      const e = await res.json();
      throw new Error(e.detail ?? "助教调用失败");
    }
    const reader = res.body?.getReader();
    if (!reader) throw new Error("未收到流式响应");
    let buffer = "";
    const decoder = new TextDecoder();
    while (!activeController.signal.aborted && current === generation) {
      const part = await reader.read();
      if (current !== generation || activeController.signal.aborted) break;
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
    if (current !== generation) return;
    if (e instanceof Error && e.name === "AbortError") {
      reply.text += "\n[已停止]";
    } else {
      const detail = e instanceof Error ? e.message : "助教调用失败";
      reply.text += `\n${detail}`;
      notify(detail);
    }
  } finally {
    if (current === generation && controller === activeController) {
      requestId = "";
      busy.value = false;
    }
  }
}
watch(storageKey, restore, { immediate: true });
onBeforeUnmount(() => {
  generation++;
  cancel();
});
</script>
