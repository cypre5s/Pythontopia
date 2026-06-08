import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';
import { useSseStream } from '@/composables/useSseStream';
import { getBugMineEchoes } from '@/api/bugMine';

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
  /**
   * 教师 copilot：教师在自己班级页时的班级 id（来自 useCompanionContext，学生恒为 null）。
   * 非空时透传 /api/tutor/ask 让后端注入本班只读学情；学生即便传了后端也会忽略（红线在服务端）。
   */
  classroomId?: string | null;
}

// 对齐 /api/tutor/ask 的 result 事件载荷（与 TutorPanel 同源）。
interface TutorStreamResult {
  answerMd?: string;
  evidencePageIds?: number[];
  groundingStatus?: string;
}

/** 主动陪伴气泡：到期复习提醒（来自 FSRS 记忆矿脉 echoes 的最高优先项）。 */
export interface CompanionNudge {
  kcCode: string;
  text: string;
}

export const useCompanionStore = defineStore('companion', () => {
  const open = ref(false);
  const busy = ref(false);
  const messages = ref<CompanionMessage[]>([]);
  const nudge = ref<CompanionNudge | null>(null);
  // 关闭后本次会话不再打扰：dismiss 即置位，loadNudge 直接早退。
  let nudgeDismissed = false;
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
    const classroomId = ctx.classroomId ? Number(ctx.classroomId) : NaN;
    const body = {
      question: trimmed,
      kcCodes: ctx.kcCodes,
      context: ctx.context,
      ...(images && images.length > 0 ? { images: images.slice(0, 3) } : {}),
      ...(Number.isFinite(classroomId) && classroomId > 0 ? { classroomId } : {}),
    };
    await sse.start(body);
  }

  function toggle(): void {
    open.value = !open.value;
  }

  /** 取最高优先到期 echo 拼一条复习提醒；无到期或已被关闭则不打扰。best-effort，失败静默。 */
  async function loadNudge(): Promise<void> {
    if (nudgeDismissed) return;
    try {
      const echoes = await getBugMineEchoes();
      const top = echoes[0];
      nudge.value = top
        ? { kcCode: top.kcCode, text: `该复习「${top.problemTitle}」了，要去矿洞看看吗？` }
        : null;
    } catch {
      nudge.value = null;
    }
  }

  function dismissNudge(): void {
    nudge.value = null;
    nudgeDismissed = true;
  }

  return {
    open,
    busy,
    messages,
    nudge,
    sse,
    ask,
    toggle,
    codeVerified,
    loadNudge,
    dismissNudge,
  };
});
