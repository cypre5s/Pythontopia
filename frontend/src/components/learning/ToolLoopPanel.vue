<script setup lang="ts">
/**
 * ToolLoopPanel —— 混合工具编排（Phase F）渲染面板。
 *
 * 消费 tool-loop SSE（经 useToolLoop）：增量渲染有界工具调用步骤（复用 LoopProgress.vue），
 * 终态展示最终答复 / 澄清追问。flag-gated 端点（PYT_TOOL_LOOP）；默认不在主链路挂载。
 */
import { computed } from 'vue';
import LoopProgress from '@/components/hud/LoopProgress.vue';
import { useToolLoop } from '@/composables/useToolLoop';

const props = defineProps<{ sessionId: string }>();

const loop = useToolLoop();

const plan = computed(() => loop.plan.value);
const status = computed(() => loop.status.value);
const runError = computed(() => loop.runError.value);
const reply = computed<string>(
  () => loop.result.value?.loop?.final_text ?? loop.result.value?.clarification ?? '',
);
const toolTrail = computed<string[]>(() => loop.result.value?.loop?.tool_trail ?? []);

function ask(message: string): void {
  void loop.start({ sessionId: props.sessionId, message });
}

defineExpose({ ask });
</script>

<template>
  <section
    class="tool-loop-panel"
    data-testid="tool-loop-panel"
  >
    <LoopProgress
      :plan="plan"
      :status="status"
      :run-error="runError"
      @stop="loop.stop"
    />
    <p
      v-if="reply"
      class="tool-loop-panel__reply"
      data-testid="tool-loop-reply"
    >
      {{ reply }}
    </p>
    <ul
      v-if="toolTrail.length > 0"
      class="tool-loop-panel__trail"
      data-testid="tool-loop-trail"
    >
      <li
        v-for="(tool, idx) in toolTrail"
        :key="`${idx}-${tool}`"
      >
        {{ tool }}
      </li>
    </ul>
  </section>
</template>

<style scoped>
.tool-loop-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tool-loop-panel__reply {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--pyt-color-text, #2b2118);
}
.tool-loop-panel__trail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 11px;
  color: #5b4a30;
}
.tool-loop-panel__trail li {
  padding: 1px 8px;
  border: 1px solid #c2871f;
  border-radius: 999px;
}
</style>
