import { ref, type Ref } from 'vue';
import {
  finishProfileInterview,
  getLearnerProfile,
  openProfileInterview,
  sendProfileTurn,
  type CheckResultDto,
  type ProfileMessageDto,
} from '@/api/learner';
import type { LearnerProfileDto } from '@/api/dto/LearnerProfileDto';

export interface ProfileInterviewState {
  sessionId: string | null;
  history: ProfileMessageDto[];
  currentQuestion: string;
  dimensionsFilled: string[];
  done: boolean;
  safety: CheckResultDto | null;
  grounding: CheckResultDto | null;
  loading: boolean;
}

/** 学生画像 + 画像访谈（8 维 dimensions 的对话式采集）切片。 */
export function createProfileSlice() {
  const profile: Ref<LearnerProfileDto | null> = ref(null);
  const profileInterview = ref<ProfileInterviewState>({
    sessionId: null,
    history: [],
    currentQuestion: '请介绍你的专业背景、编程基础和学习目标。',
    dimensionsFilled: [],
    done: false,
    safety: null,
    grounding: null,
    loading: false,
  });

  async function loadProfile(): Promise<void> {
    profile.value = await getLearnerProfile();
  }

  /**
   * 打开 / 续接画像访谈。
   *
   * 后端 `ProfileService.openOrResumeInterview` 的两条分支（新建、续接 OPEN）都只回
   * `{sessionId, status}`，`draft` 恒为 null、`history` 恒为空——因此**不能**拿返回值去
   * 覆盖已有画像与历史，否则学生一走进画像室就把 `learning.profile` 清成 null，
   * 整个会话内 useProfileSignals 恒空（主界面「下一步」丢画像依据、出题退化成无画像随机）。
   * 只有非空返回才覆盖。
   */
  async function openInterview(): Promise<void> {
    const response = await openProfileInterview();
    profileInterview.value.sessionId = response.interviewSessionId;
    if (response.history && response.history.length > 0) {
      profileInterview.value.history = response.history;
    }
    if (response.draft) {
      profile.value = response.draft;
    }
  }

  async function sendTurn(userMessage: string): Promise<void> {
    if (profileInterview.value.loading) return;
    profileInterview.value.loading = true;
    let historyBeforeTurn: ProfileMessageDto[] | null = null;
    let historyLengthBeforeTurn = 0;
    try {
      if (!profileInterview.value.sessionId) {
        await openInterview();
      }
      const sessionId = profileInterview.value.sessionId;
      if (!sessionId) {
        throw new Error('Profile interview session was not opened');
      }
      // 先快照本轮之前的多轮历史（用于给 ProfileAgent 多轮记忆），再把当前消息加入本地展示。
      historyBeforeTurn = profileInterview.value.history;
      historyLengthBeforeTurn = historyBeforeTurn.length;
      const priorHistory = [...historyBeforeTurn];
      historyBeforeTurn.push({ role: 'user', content: userMessage });
      const response = await sendProfileTurn(sessionId, { userMessage, history: priorHistory });
      // 这些字段由外部 graph 填充，缺键时后端回 null；补默认值，避免访谈永远不进入完成态
      // 或把 null 写进 state 让组件 `.length` 崩掉。
      profileInterview.value.dimensionsFilled = response.dimensionsFilled ?? [];
      profileInterview.value.done = response.done ?? false;
      profileInterview.value.safety = response.safetyResult ?? null;
      profileInterview.value.grounding = response.groundingResult ?? null;
      if (response.nextQuestion) {
        profileInterview.value.currentQuestion = response.nextQuestion;
        profileInterview.value.history.push({ role: 'assistant', content: response.nextQuestion });
      }
      if (response.profileDraft) {
        profile.value = response.profileDraft;
      }
    } catch (error) {
      historyBeforeTurn?.splice(historyLengthBeforeTurn);
      throw error;
    } finally {
      profileInterview.value.loading = false;
    }
  }

  async function finishInterview(): Promise<void> {
    if (profileInterview.value.sessionId) {
      await finishProfileInterview(profileInterview.value.sessionId);
    }
    await loadProfile();
    profileInterview.value.done = true;
  }

  return {
    profile,
    profileInterview,
    loadProfile,
    openInterview,
    sendTurn,
    finishInterview,
  };
}
