<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useLearningStore } from '@stores/learning';

const learning = useLearningStore();

const paths = computed(() => learning.pathState.archived);

onMounted(async () => {
  if (learning.pathState.archived.length === 0) {
    await learning.loadArchivedPaths();
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
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
  color: #6e4f1d;
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
  border: 2px solid #c9a86b;
  border-radius: 8px;
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
  color: #4d3613;
  line-height: 1.5;
}
small {
  color: #6e4f1d;
  font-size: 12px;
}
</style>
