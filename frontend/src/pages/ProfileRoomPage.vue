<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import ProfileInterviewPanel from '@/components/profile/ProfileInterviewPanel.vue';
import AgentNotebook from '@/components/agents/AgentNotebook.vue';
import TwinWeekly from '@/components/companion/TwinWeekly.vue';
import { useLearningStore } from '@/stores/learning';
import { useWorkshopUiStore } from '@/stores/workshopUi';

const learning = useLearningStore();
const workshopUi = useWorkshopUiStore();
const router = useRouter();
const showNotebook = ref(false);
const showWeekly = ref(false);

function onTwinPrompt(prompt: string): void {
  workshopUi.setPendingPrompt(prompt);
  void router.push('/workshop');
}

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
        <button
          class="weekly-btn"
          @click="showWeekly = !showWeekly"
        >
          {{ showWeekly ? '收起周报' : '本周 AI 团队' }}
        </button>
        <button
          class="weekly-btn"
          @click="showNotebook = !showNotebook"
        >
          {{ showNotebook ? '收起笔记' : 'Agent 笔记本' }}
        </button>
        <TwinWeekly
          v-if="showWeekly"
          class="weekly-panel"
          @prompt="onTwinPrompt"
        />
        <AgentNotebook
          v-if="showNotebook"
          class="weekly-panel"
          @close="showNotebook = false"
        />
      </div>
      <ProfileInterviewPanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.weekly-btn {
  margin-top: 12px;
  padding: 6px 14px;
  font-family: 'Zpix', monospace;
  font-size: 12px;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: 4px;
  cursor: pointer;
  image-rendering: pixelated;
}
.weekly-btn:hover { background: var(--pyt-color-bg-soft); }
.weekly-panel { margin-top: 10px; }
</style>
