<template>
  <fieldset class="knowledge-point-select">
    <legend>关联知识点</legend>
    <p class="muted">仅显示当前章节的知识点；更新活动会保存新的内容版本。</p>
    <label v-for="point in available" :key="point.id" class="checkbox-label"
      ><input
        type="checkbox"
        :value="point.id"
        :checked="modelValue.includes(point.id)"
        @change="toggle(point.id, ($event.target as HTMLInputElement).checked)"
      />{{ point.title }}</label
    >
    <p v-if="!available.length" class="muted">
      当前章节尚无知识点，请先到课程与知识点页面创建。
    </p>
  </fieldset>
</template>
<script setup lang="ts">
import { computed, watch } from "vue";
import { selectedChapterKnowledgePoints } from "../shared/curriculum";
import type { KnowledgePoint } from "../shared/curriculum";
const props = defineProps<{
    modelValue: number[];
    chapterId: number;
    points: KnowledgePoint[];
  }>(),
  emit = defineEmits<{ "update:modelValue": [value: number[]] }>();
const available = computed(() =>
  props.points.filter((p) => p.chapter_id === props.chapterId),
);
function toggle(id: number, checked: boolean) {
  const selected = checked
    ? [...new Set([...props.modelValue, id])]
    : props.modelValue.filter((value) => value !== id);
  emit(
    "update:modelValue",
    selectedChapterKnowledgePoints(props.points, props.chapterId, selected),
  );
}
watch([() => props.chapterId, () => props.points], () => {
  const value = selectedChapterKnowledgePoints(
    props.points,
    props.chapterId,
    props.modelValue,
  );
  if (value.length !== props.modelValue.length)
    emit("update:modelValue", value);
});
</script>
<style scoped>
.knowledge-point-select {
  grid-column: 1/-1;
  border: 1px solid var(--line);
  padding: 1rem;
  max-height: 250px;
  overflow: auto;
  border-radius: 6px;
}
.knowledge-point-select legend {
  font-size: 0.8rem;
  font-weight: 600;
  padding: 0 0.4rem;
}
.knowledge-point-select .checkbox-label {
  font-size: 0.8rem;
  font-weight: 400;
  margin: 0.55rem 0;
}
.knowledge-point-select p {
  font-size: 0.75rem;
  margin: 0.2rem 0 0.8rem;
}
</style>
