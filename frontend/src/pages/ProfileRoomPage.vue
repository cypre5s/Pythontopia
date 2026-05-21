<script setup lang="ts">
import { onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import ProfileInterviewPanel from '@/components/profile/ProfileInterviewPanel.vue';
import { useLearningStore } from '@/stores/learning';

const learning = useLearningStore();

onMounted(async () => {
  if (!learning.profileInterview.sessionId) {
    await learning.openInterview();
  }
});
</script>

<template>
  <AppLayout>
    <section class="profile-room-page">
      <div class="profile-room-scene">
        <div class="profile-npc">
          ProfileAgent
        </div>
        <p>[占位] 画像屋 NPC 会通过右侧面板访谈生成学生画像。</p>
      </div>
      <ProfileInterviewPanel />
    </section>
  </AppLayout>
</template>
