<script setup lang="ts">
import { computed } from 'vue';
import type { EvaluationDto } from '@/api/dto/Evaluation';
import type { LearningPathDto, LearningPathNodeDto } from '@/api/dto/Path';
import { kcDisplayName } from '@/data/kcNames';

const props = defineProps<{ evaluation: EvaluationDto; activePath: LearningPathDto | null }>();

const draftSummary = computed<LearningPathNodeDto[]>(() => props.evaluation.draftPath?.nodes ?? []);
const activeSummary = computed<LearningPathNodeDto[]>(() => props.activePath?.nodes ?? []);

function nodeLabel(node: LearningPathNodeDto): string {
  const action = nodeAction(node);
  return `Day ${node.dayIndex}.${node.orderInDay} · ${action} · ${kcDisplayName(node.kcCode)}`;
}

function nodeAction(node: LearningPathNodeDto): string {
  switch (node.nodeType) {
    case 'READ_RESOURCE':
      return '阅读';
    case 'SOLVE_PROBLEM':
      return '练习';
    case 'FIX_BUG':
      return '矿洞';
    case 'PLANT_KC':
      return '主线解锁';
    case 'PROFILE_UPDATE':
      return '画像';
    default:
      return '节点';
  }
}
</script>

<template>
  <section
    class="diff-view"
    data-testid="path-diff"
  >
    <header class="header">
      <h4>学习路径对比</h4>
      <p class="hint">
        {{ evaluation.pathDiffSummary || 'AI 生成解释 · 仅作建议' }}
      </p>
    </header>
    <div class="cols">
      <div class="col">
        <h5>当前 ACTIVE</h5>
        <ul
          v-if="activeSummary.length"
          class="nodes"
        >
          <li
            v-for="node in activeSummary"
            :key="node.id"
            class="node"
          >
            <span>{{ nodeLabel(node) }}</span>
            <p class="reason">
              {{ node.reasonSummary }}
            </p>
          </li>
        </ul>
        <p
          v-else
          class="empty"
        >
          尚无 ACTIVE 路径。
        </p>
      </div>
      <div class="col draft">
        <h5>新建议 DRAFT</h5>
        <ul
          v-if="draftSummary.length"
          class="nodes"
        >
          <li
            v-for="node in draftSummary"
            :key="node.id"
            class="node"
          >
            <span>{{ nodeLabel(node) }}</span>
            <p class="reason">
              {{ node.reasonSummary }}
            </p>
          </li>
        </ul>
        <p
          v-else
          class="empty"
        >
          本次评估未生成新路径。
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.diff-view {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
  display: grid;
  gap: 10px;
}
.header h4 {
  margin: 0;
}
.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}
.cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.col h5 {
  margin: 0 0 6px;
  color: var(--pyt-color-text-primary);
}
.col.draft h5::before {
  content: '* ';
}
.nodes {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 8px;
}
.node {
  background: var(--pyt-color-bg-soft);
  border-radius: 6px;
  padding: 8px 10px;
  border: 1px dashed var(--pyt-color-border);
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}
.node .reason {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
}
.empty {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}
</style>
