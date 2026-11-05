<script setup lang="ts">
import { computed } from "vue";
import { ChevronLeft, ChevronRight } from "@lucide/vue";
import { Button } from "../ui/button";
const props = withDefaults(
  defineProps<{
    page: number;
    total: number;
    pageSize: number;
    label?: string;
  }>(),
  { label: "条记录" },
);
const emit = defineEmits<{
  "update:page": [value: number];
  "update:pageSize": [value: number];
}>();
const pages = computed(() =>
  Math.max(1, Math.ceil(props.total / props.pageSize)),
);
const start = computed(() =>
  props.total ? (props.page - 1) * props.pageSize + 1 : 0,
);
</script>
<template>
  <div class="table-pagination">
    <span
      >共 {{ total }} {{ label
      }}<span v-if="total">
        · 显示 {{ start }}–{{ Math.min(page * pageSize, total) }}</span
      ></span
    >
    <div class="pagination-controls">
      <label class="pagination-size"
        >每页<select
          :value="pageSize"
          aria-label="每页条数"
          @change="
            emit(
              'update:pageSize',
              Number(($event.target as HTMLSelectElement).value),
            );
            emit('update:page', 1);
          "
        >
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select></label
      >
      <span>第 {{ page }} / {{ pages }} 页</span>
      <Button
        variant="outline"
        size="icon-sm"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="emit('update:page', page - 1)"
        ><ChevronLeft :size="16"
      /></Button>
      <Button
        variant="outline"
        size="icon-sm"
        :disabled="page >= pages"
        aria-label="下一页"
        @click="emit('update:page', page + 1)"
        ><ChevronRight :size="16"
      /></Button>
    </div>
  </div>
</template>
