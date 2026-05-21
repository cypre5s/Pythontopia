<script setup lang="ts">
import { onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import { useLearningStore } from '@/stores/learning';

const learning = useLearningStore();

onMounted(async () => {
  await learning.loadProfile();
});
</script>

<template>
  <AppLayout>
    <section class="profile-page">
      <h1>我的画像</h1>
      <div
        v-if="learning.profile"
        class="profile-grid"
      >
        <article>
          <h2>专业背景</h2>
          <p>{{ learning.profile.major }}</p>
        </article>
        <article>
          <h2>知识基础</h2>
          <p>{{ learning.profile.baseLevel?.languagesKnown.join(', ') }}</p>
        </article>
        <article>
          <h2>学习目标</h2>
          <p>{{ learning.profile.learningGoal?.kind }}</p>
        </article>
        <article>
          <h2>总结</h2>
          <p>{{ learning.profile.narrativeSummary }}</p>
        </article>
      </div>
      <p v-else>
        画像尚未生成。
      </p>
    </section>
  </AppLayout>
</template>
