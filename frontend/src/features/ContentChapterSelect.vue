<template>
  <fieldset class="content-tag-select">
    <legend>关联章节</legend>
    <p class="field-hint">可选多个章节，也可以暂不分类。</p>
    <div class="content-tag-options">
      <label
        v-for="chapter in chapters"
        :key="chapter.id"
        class="checkbox-label"
        ><input v-model="selected" type="checkbox" :value="chapter.id" />{{
          chapter.title
        }}</label
      >
    </div>
    <p v-if="!chapters.length" class="field-hint">
      还没有章节，可稍后到「课程与知识点」创建并关联。
    </p>
  </fieldset>
</template>
<script setup lang="ts">
import { computed } from "vue";
import type { Chapter } from "../shared/types";
const props = defineProps<{ modelValue: number[]; chapters: Chapter[] }>();
const emit = defineEmits<{ "update:modelValue": [value: number[]] }>();
const selected = computed({
  get: () => props.modelValue,
  set: (value: number[]) => emit("update:modelValue", value),
});
</script>
