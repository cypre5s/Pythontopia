<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@/stores/learning';

const learning = useLearningStore();
const router = useRouter();
const answer = ref('');

const dimensionLabels: Record<string, string> = {
  major: '专业背景',
  base_level: '知识基础',
  learning_goal: '学习目标',
  cognitive_style: '认知风格',
  learning_preference: '学习偏好',
  error_patterns: '易错模式',
  practice_capability: '实践能力',
  emotion_persistence: '情绪与坚持度',
};

async function send(): Promise<void> {
  if (!answer.value.trim()) {
    return;
  }
  const message = answer.value.trim();
  answer.value = '';
  await learning.sendTurn(message);
}

async function finish(): Promise<void> {
  await learning.finishInterview();
  await router.push('/profile');
}
</script>

<template>
  <aside class="profile-panel">
    <header>
      <h2>画像屋访谈</h2>
      <span>{{ learning.profileInterview.dimensionsFilled.length }} / 8</span>
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

    <div class="status-row">
      <span>Grounding: {{ learning.profileInterview.grounding?.passed === false ? '需补充' : '通过' }}</span>
      <span>Safety: {{ learning.profileInterview.safety?.passed === false ? '失败' : '通过' }}</span>
    </div>

    <form
      v-if="!learning.profileInterview.done"
      class="profile-form"
      @submit.prevent="send"
    >
      <textarea
        v-model="answer"
        placeholder="写下你的学习背景、目标或偏好"
      />
      <button
        :disabled="learning.profileInterview.loading"
        type="submit"
      >
        发送
      </button>
    </form>

    <button
      v-else
      class="finish"
      type="button"
      @click="finish"
    >
      完成访谈
    </button>
  </aside>
</template>
