import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';
import { useSseStream } from '@/composables/useSseStream';

export interface CompanionMessage {
  role: 'user' | 'assistant';
  content: string;
  /** 课件证据页 id（与全站 /api/tutor/ask result 一致，用于回链 /library?pageId=）。 */
  evidencePageIds: number[];
}

export interface CompanionAskContext {
  kcCodes: string[];
  surface: string;
  context: string;
}

// 对齐 /api/tutor/ask 的 result 事件载荷（与 TutorPanel 同源）。
interface TutorStreamResult {
  answerMd?: string;
  evidencePageIds?: number[];
  groundingStatus?: string;
}

export const useCompanionStore = defineStore('companion', () => {
  const open = ref(false);
  const busy = ref(false);
  const messages = ref<CompanionMessage[]>([]);
  const sse = useSseStream<TutorStreamResult>('/api/tutor/ask');

  watch(sse.tokens, (text) => {
    const last = messages.value[messages.value.length - 1];
    if (last && last.role === 'assistant') {
      last.content = text;
    }
  });

  watch(sse.result, (res) => {
    if (!res) return;
    const last = messages.value[messages.value.length - 1];
    if (last && last.role === 'assistant') {
      if (res.answerMd) last.content = res.answerMd;
      last.evidencePageIds = res.evidencePageIds ?? [];
    }
  });

  watch(sse.status, (s) => {
    busy.value = s === 'connecting' || s === 'streaming';
  });

  // 当前答案是否经过「真跑代码验证」：流水线出现 code_runner 完工 trace 即为已验证。
  const codeVerified = computed(() =>
    sse.traces.value.some((t) => t.node === 'code_runner' && t.phase === 'finish'),
  );

  async function ask(
    input: string,
    ctx: CompanionAskContext,
    images?: string[],
  ): Promise<void> {
    const trimmed = input.trim();
    if (!trimmed || busy.value) return;
    messages.value.push({ role: 'user', content: trimmed, evidencePageIds: [] });
    messages.value.push({ role: 'assistant', content: '', evidencePageIds: [] });
    const body = {
      question: trimmed,
      kcCodes: ctx.kcCodes,
      context: ctx.context,
      ...(images && images.length > 0 ? { images: images.slice(0, 3) } : {}),
    };
    await sse.start(body);
  }

  function toggle(): void {
    open.value = !open.value;
  }

  return { open, busy, messages, sse, ask, toggle, codeVerified };
});
