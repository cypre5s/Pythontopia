<script setup lang="ts">
import { onMounted, watch } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import BugMinePanel from '@/components/bugmine/BugMinePanel.vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';

const learning = useLearningStore();
const toast = useToastStore();

// S1：错题矿洞「入口」私有 id（submissionId / sessionId）不经 URL，改由 store 承载。
// 上游（训练场判题失败「进入 Bug 矿洞」/ L5 变式通过）在导航前 setPendingBugMineEntry，
// 本页 consume 后用于开/读会话；避免学生在地址栏看到或篡改他人 id（后端另有归属双键校验）。
async function bootstrap(entry: { submissionId: number | null; sessionId: number | null }): Promise<void> {
  await learning.loadBugMineErrorBook();
  if (entry.submissionId != null) {
    const session = await learning.openBugMine(entry.submissionId);
    if (!session) {
      toast.show(learning.bugMine.lastError ?? '无法打开矿洞会话', 'error');
    }
    return;
  }
  if (entry.sessionId != null) {
    await learning.loadBugMine(entry.sessionId);
    if (learning.bugMine.lastError) {
      toast.show(learning.bugMine.lastError, 'error');
    }
  }
}

onMounted(() => {
  // R-14：Bug 矿洞是独立的 /bug-mine 页面（mine_south 风景地图已删），
  // 不再切 Phaser 场景、不写 pendingTravel，直接进入错题调试面板。
  void bootstrap(learning.consumePendingBugMineEntry());
});

// 已在 /bug-mine 时又被带 store 入口 id 导航进来（同路由复用组件）：监听 store pending 字段，
// 有新入口 id 即 consume 后 bootstrap（consume 清空后再次触发返回 null，被守卫跳过，不重复）。
watch(
  [
    () => learning.bugMine.pendingEntrySubmissionId,
    () => learning.bugMine.pendingEntrySessionId,
  ],
  ([sub, sess]) => {
    if (sub != null || sess != null) {
      void bootstrap(learning.consumePendingBugMineEntry());
    }
  },
);
</script>

<template>
  <AppLayout>
    <section class="bug-mine-page">
      <BugMinePanel />
    </section>
  </AppLayout>
</template>

<style scoped>
.bug-mine-page {
  height: calc(100vh - 64px);
  min-height: calc(100vh - 64px);
  overflow: auto;
  padding: 16px;
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
}
</style>
