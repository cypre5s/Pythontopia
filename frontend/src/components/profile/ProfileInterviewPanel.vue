<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@/stores/learning';

const learning = useLearningStore();
const router = useRouter();
const answer = ref('');
const errorMessage = ref<string | null>(null);
const finishing = ref(false);

const dimensionLabels: Record<string, string> = {
  major: '专业背景',
  base_level: '知识基础',
  learning_goal: '学习目标',
  learning_preference: '学习偏好',
};

async function send(): Promise<void> {
  if (!answer.value.trim() || learning.profileInterview.loading || finishing.value) {
    return;
  }
  const message = answer.value.trim();
  errorMessage.value = null;
  try {
    await learning.sendTurn(message);
    answer.value = '';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '发送失败，请稍后重试。';
  }
}

async function finish(): Promise<void> {
  if (finishing.value) return;
  finishing.value = true;
  errorMessage.value = null;
  try {
    await learning.finishInterview();
    await router.push('/profile');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存画像失败，请稍后重试。';
  } finally {
    finishing.value = false;
  }
}
</script>

<template>
  <aside class="profile-panel">
    <header>
      <h2>画像屋访谈</h2>
      <span>{{ learning.profileInterview.dimensionsFilled.length }} / 4</span>
    </header>

    <div class="dimension-grid">
      <span
        v-for="(label, key) in dimensionLabels"
        :key="key"
        :class="{ filled: learning.profileInterview.dimensionsFilled.includes(key) }"
      >
        {{ label }}
      </span>
    </div>

    <div class="profile-question">
      {{ learning.profileInterview.currentQuestion }}
    </div>
    <p class="profile-note">
      访谈只记录你的目标、已有经验和偏好，不会据此评估能力或情绪。
    </p>

    <div class="status-row">
      <span>Grounding: {{ learning.profileInterview.grounding?.passed === false ? '需补充' : '通过' }}</span>
      <span>Safety: {{ learning.profileInterview.safety?.passed === false ? '失败' : '通过' }}</span>
    </div>
    <p
      v-if="errorMessage"
      class="profile-error"
      data-testid="profile-interview-error"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <form
      v-if="!learning.profileInterview.done"
      class="profile-form"
      @submit.prevent="send"
    >
      <textarea
        v-model="answer"
        :disabled="learning.profileInterview.loading || finishing"
        placeholder="写下你的编程经验、学习目标或偏好媒介"
      />
      <button
        :disabled="learning.profileInterview.loading || finishing"
        type="submit"
      >
        {{ learning.profileInterview.loading ? '发送中…' : '发送' }}
      </button>
    </form>

    <button
      v-else
      class="finish"
      type="button"
      :disabled="finishing"
      @click="finish"
    >
      {{ finishing ? '保存中…' : '完成访谈' }}
    </button>
  </aside>
</template>
