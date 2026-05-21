<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useLearningStore } from '@stores/learning';
import type { LearningPathNodeDto } from '@/api/dto/Path';

const learning = useLearningStore();

onMounted(async () => {
  if (!learning.pathState.active && learning.pathState.phase === 'idle') {
    await learning.loadActivePath();
  }
});

const days = computed(() => {
  const path = learning.pathState.active;
  if (!path) return [];
  const grouped = new Map<number, LearningPathNodeDto[]>();
  for (const node of path.nodes) {
    const list = grouped.get(node.dayIndex) ?? [];
    list.push(node);
    grouped.set(node.dayIndex, list);
  }
  return Array.from(grouped.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([day, nodes]) => ({ day, nodes }));
});
</script>

<template>
  <section
    class="path-tab"
    data-testid="path-tab"
  >
    <p
      v-if="!learning.pathState.active"
      class="empty"
    >
      还没有生成本周路径。点击右上角“规划本周路径”由多智能体生成。
    </p>
    <article
      v-else
      class="path-card"
    >
      <header>
        <h3>
          v{{ learning.pathState.active.version }} · {{ learning.pathState.active.goalSummary }}
        </h3>
        <small>生成时间：{{ learning.pathState.active.generatedAt }}</small>
      </header>
      <ol class="day-list">
        <li
          v-for="entry in days"
          :key="entry.day"
        >
          <h4>第 {{ entry.day }} 天</h4>
          <ul>
            <li
              v-for="node in entry.nodes"
              :key="node.id"
              class="node-row"
            >
              <span class="type">{{ node.nodeType }}</span>
              <span class="kc">{{ node.kcCode }}</span>
              <p class="reason">
                {{ node.reasonSummary }}
              </p>
              <small
                v-if="node.suggestedResourceType"
                class="hint"
              >建议资源：{{ node.suggestedResourceType }}</small>
              <small
                v-if="node.suggestedProblemId"
                class="hint"
              >推荐题目 ID：{{ node.suggestedProblemId }}</small>
            </li>
          </ul>
        </li>
      </ol>
    </article>
  </section>
</template>

<style scoped>
.path-tab {
  display: grid;
  gap: 12px;
}
.path-card {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 14px;
  display: grid;
  gap: 12px;
}
header h3 {
  margin: 0;
}
header small {
  color: #6e4f1d;
  font-size: 12px;
}
.day-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}
.day-list h4 {
  margin: 0 0 6px;
}
.day-list ul {
  list-style: none;
  padding-left: 12px;
  display: grid;
  gap: 6px;
}
.node-row {
  background: #fff2c9;
  border-radius: 6px;
  padding: 8px;
  display: grid;
  gap: 4px;
}
.node-row .type {
  font-weight: 600;
  color: #4d3613;
}
.node-row .kc {
  font-size: 12px;
  color: #6e4f1d;
}
.node-row .reason {
  margin: 0;
  line-height: 1.5;
  color: #4d3613;
}
.node-row .hint {
  color: #6e4f1d;
  font-size: 12px;
}
.empty {
  padding: 14px;
  background: #fdf3d0;
  border-radius: 8px;
  text-align: center;
  color: #6e4f1d;
}
</style>
