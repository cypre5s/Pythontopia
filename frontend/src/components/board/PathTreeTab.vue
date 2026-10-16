<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import type { LearningPathNodeDto } from '@/api/dto/Path';
import { resolvePathNodeDeepLink } from '@/utils/questDeepLink';
import { kcDisplayName } from '@/data/kcNames';

const learning = useLearningStore();
const router = useRouter();

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

function actionLabelFor(node: LearningPathNodeDto): string {
  switch (node.nodeType) {
    case 'SOLVE_PROBLEM':
      return node.suggestedProblemId ? '去做题' : '去练习';
    case 'READ_RESOURCE':
      return '去工坊读资源';
    case 'FIX_BUG':
      return '去矿洞修 Bug';
    case 'PLANT_KC':
      // 兼容历史节点；新路径不再生成种植任务。
      return '前往主线解锁知识点';
    case 'PROFILE_UPDATE':
      return '去画像屋更新';
    default:
      return '去做';
  }
}

function tooltipFor(node: LearningPathNodeDto): string {
  return `${actionLabelFor(node)}：${node.kcCode}`;
}

async function clickNode(node: LearningPathNodeDto): Promise<void> {
  const target = resolvePathNodeDeepLink(node);
  await router.push(target);
}
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
      还没有生成本周路径。点击右上角"规划本周路径"由多智能体生成。
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
              :data-testid="`path-node-${node.id}`"
            >
              <button
                type="button"
                class="node-button"
                :title="tooltipFor(node)"
                :data-testid="`path-node-link-${node.id}`"
                @click="clickNode(node)"
              >
                <span class="type">{{ node.nodeType === 'PLANT_KC' ? '主线' : node.nodeType }}</span>
                <span class="kc">{{ kcDisplayName(node.kcCode) }}</span>
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
                <span class="action">{{ actionLabelFor(node) }} →</span>
              </button>
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
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
  display: grid;
  gap: 12px;
}
header h3 {
  margin: 0;
}
header small {
  color: var(--pyt-color-text-hint);
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
  padding: 0;
  display: grid;
  gap: 4px;
}
.node-button {
  display: grid;
  gap: 4px;
  width: 100%;
  text-align: left;
  background: transparent;
  border: none;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  color: inherit;
  font: inherit;
}
.node-button:hover,
.node-button:focus-visible {
  background: #ffe6a4;
  outline: 2px solid var(--pyt-color-border);
  outline-offset: -2px;
}
.node-row .type {
  font-weight: 600;
  color: var(--pyt-color-text-primary);
}
.node-row .kc {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.node-row .reason {
  margin: 0;
  line-height: 1.5;
  color: var(--pyt-color-text-primary);
}
.node-row .hint {
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
.node-row .action {
  margin-top: 4px;
  align-self: end;
  font-size: 12px;
  font-weight: 600;
  color: #2f5b8a;
}
.empty {
  padding: 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
</style>
