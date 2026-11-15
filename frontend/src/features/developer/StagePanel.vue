<template>
  <div class="developer-panel">
    <section class="developer-section" aria-labelledby="developer-stage-target">
      <h4 id="developer-stage-target" class="game-card-title"><GameIcon name="target" />选择目标</h4>
      <div class="developer-columns">
        <label class="town-field" for="developer-quest">任务
          <select id="developer-quest" v-model="questId" :disabled="action.busy">
            <option value="">请选择任务</option>
            <option v-for="item in overview?.quests" :key="item.id" :value="item.id">{{ item.title }}</option>
          </select>
        </label>
        <label class="town-field" for="developer-node">目标阶段
          <select id="developer-node" v-model="nodeId" :disabled="action.busy || !quest">
            <option value="">请选择阶段</option>
            <option v-for="item in quest?.nodes" :key="item.id" :value="item.id">
              {{ item.title }} · {{ stateLabels[item.status] ?? item.status }}
            </option>
          </select>
        </label>
      </div>
      <p v-if="!overview?.quests.length" class="developer-help">本班没有已配置任务。可通过内容定位查看课程，不会自动创建章节任务。</p>
      <p v-else class="developer-help">定位到目标阶段的开始。其他阶段按前置路径调整，当前对白结束；不会生成成绩或学习证明。</p>
    </section>
    <section v-if="node" class="developer-section" aria-labelledby="developer-stage-path">
      <h4 id="developer-stage-path" class="game-card-title"><GameIcon name="list" />前置路径</h4>
      <p class="developer-help">
        前置：{{ (node.dependencies ?? []).map(titleOf).join('、') || '无' }} · {{ node.aggregation ?? 'ALL' }}
        <template v-if="node.condition"> · 需要分支 {{ titleOf(node.condition.node) }} / {{ node.condition.choice }}</template>
      </p>
      <div class="developer-columns">
        <label v-for="entry in branchNodes" :key="entry.id" class="town-field">
          {{ entry.title }} · 分支
          <select v-model="choices[entry.id]" :disabled="action.busy">
            <option value="">按目标推导</option>
            <option v-for="choice in entry.choices" :key="choice.id" :value="choice.id">{{ choice.label }}</option>
          </select>
        </label>
        <label v-for="entry in anyNodes" :key="`any-${entry.id}`" class="town-field">
          {{ entry.title }} · ANY 前置
          <select v-model="anyDependencies[entry.id]" :disabled="action.busy">
            <option value="">请选择</option>
            <option v-for="dep in entry.dependencies" :key="dep" :value="dep">{{ titleOf(dep) }}</option>
          </select>
        </label>
      </div>
      <button class="town-secondary" :disabled="action.busy" @click="previewStage"><GameIcon name="list" />预览阶段调整</button>
      <p class="developer-help">建议调整前先保存体验检查点。</p>
      <ToolFeedback v-if="action.name === 'preview'" :error="action.error" />
    </section>
    <section v-if="preview" class="developer-confirm" aria-label="确认体验调整">
      <h4 class="game-card-title"><GameIcon name="journal" />定位到“{{ preview.target }}”的开始</h4>
      <p>标记为体验前置：{{ preview.completed.map(item => item.title).join('、') || '无' }}</p>
      <div class="developer-actions">
        <button class="town-primary" :disabled="action.busy" @click="confirmStage">{{ action.busy ? '处理中…' : '确认定位' }}</button>
        <button class="town-secondary" :disabled="action.busy" @click="preview = undefined">取消</button>
      </div>
      <ToolFeedback v-if="action.name === 'confirm'" :error="action.error" :message="action.message" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import GameIcon from "../../shared/GameIcon.vue";
import ToolFeedback from "./ToolFeedback.vue";
import { useDeveloperTools, useToolAction } from "./useDeveloperTools";
import type { StageInput, StagePreview } from "./types";

const { overview, session, activeTab } = useDeveloperTools();
const action = reactive(useToolAction());
const questId = ref("");
const nodeId = ref("");
const choices = ref<Record<string, string>>({});
const anyDependencies = ref<Record<string, string>>({});
const preview = ref<(StagePreview & { body: StageInput })>();
const quest = computed(() => overview.value?.quests.find(item => item.id === questId.value));
const node = computed(() => quest.value?.nodes.find(item => item.id === nodeId.value));
const branchNodes = computed(() => quest.value?.nodes.filter(item => item.choices?.length) ?? []);
const anyNodes = computed(() => quest.value?.nodes.filter(item => item.aggregation === "ANY" && (item.dependencies?.length ?? 0) > 1) ?? []);
const stateLabels: Record<string, string> = {
  ready: "可开始", locked: "前置未完成", complete: "已完成", skipped: "另一分支",
};
const titleOf = (id: string) => quest.value?.nodes.find(item => item.id === id)?.title ?? id;
const clean = (values: Record<string, string>) => Object.fromEntries(Object.entries(values).filter(([, value]) => value));

function previewStage() {
  const body: StageInput = {
    quest_id: questId.value,
    node_id: nodeId.value,
    choices: clean(choices.value),
    any_dependencies: clean(anyDependencies.value),
  };
  preview.value = undefined;
  return action.run("preview", async ({ client, current }) => {
    await session.prepareExperience();
    if (!current()) return;
    const result = await client.previewStage(body);
    if (current() && activeTab.value === "stage") preview.value = { ...result, body };
  });
}

function confirmStage() {
  const confirmed = preview.value;
  if (!confirmed) return;
  return action.run("confirm", async ({ client, current }) => {
    const completed = await session.restartCurrentClassroom(client.stageMutation(confirmed.body, confirmed.expected));
    if (current() && !completed) throw new Error("阶段调整未完成，请重试。");
  });
}

watch(questId, () => {
  nodeId.value = "";
  choices.value = {};
  anyDependencies.value = {};
});
watch([nodeId, choices, anyDependencies, activeTab], () => {
  preview.value = undefined;
  action.clear();
}, { deep: true });
</script>
