<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();

const paths = computed(() => learning.pathState.archived);

// 每次激活页签都重新拉：只在列表为空时拉会让「重新规划后刚归档的那版」永远看不到。
onMounted(async () => {
  try {
    await learning.loadArchivedPaths();
  } catch {
    /* 历史路径是只读视图，拉取失败保持上一次快照即可 */
  }
});
</script>

<template>
  <section
    class="archived-tab"
    data-testid="archived-tab"
  >
    <p
      v-if="paths.length === 0"
      class="empty"
    >
      还没有历史路径。第一次规划之后旧路径会归档到这里。
    </p>
    <ul
      v-else
      class="path-list"
    >
      <li
        v-for="path in paths"
        :key="path.id"
        class="path-card"
      >
        <header>
          <h3>v{{ path.version }}</h3>
          <small>生成时间 {{ path.generatedAt }}</small>
        </header>
        <p class="goal">
          {{ path.goalSummary }}
        </p>
        <small>共 {{ path.nodes.length }} 个节点</small>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.empty {
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.path-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}
.path-card {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
  display: grid;
  gap: 6px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
header h3 {
  margin: 0;
}
.goal {
  margin: 0;
  color: var(--pyt-color-text-primary);
  line-height: 1.5;
}
small {
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
</style>
