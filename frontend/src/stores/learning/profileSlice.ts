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

  async function openInterview(): Promise<void> {
    const response = await openProfileInterview();
    profileInterview.value.sessionId = response.interviewSessionId;
    profileInterview.value.history = response.history;
    profile.value = response.draft;
  }

  async function sendTurn(userMessage: string): Promise<void> {
    if (!profileInterview.value.sessionId) {
      await openInterview();
    }
    const sessionId = profileInterview.value.sessionId;
    if (!sessionId) {
      throw new Error('Profile interview session was not opened');
    }
    profileInterview.value.loading = true;
    try {
      // 先快照本轮之前的多轮历史（用于给 ProfileAgent 多轮记忆），再把当前消息加入本地展示。
      const priorHistory = [...profileInterview.value.history];
      profileInterview.value.history.push({ role: 'user', content: userMessage });
      const response = await sendProfileTurn(sessionId, { userMessage, history: priorHistory });
      profileInterview.value.dimensionsFilled = response.dimensionsFilled;
      profileInterview.value.done = response.done;
      profileInterview.value.safety = response.safetyResult;
      profileInterview.value.grounding = response.groundingResult;
      if (response.nextQuestion) {
        profileInterview.value.currentQuestion = response.nextQuestion;
        profileInterview.value.history.push({ role: 'assistant', content: response.nextQuestion });
      }
      if (response.profileDraft) {
        profile.value = response.profileDraft;
      }
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
