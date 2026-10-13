<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const PAGE_SIZES = [10, 20, 50, 100] as const;

const props = defineProps<{
  page: number;
  totalPages: number;
  pageSize: number;
  totalElements: number;
  itemLabel: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (event: 'page-change', page: number): void;
  (event: 'page-size-change', pageSize: number): void;
}>();

const jumpTarget = ref(props.page + 1);
const canJump = computed(() =>
  !props.disabled
  && props.totalPages > 0
  && Number.isInteger(jumpTarget.value)
  && jumpTarget.value >= 1
  && jumpTarget.value <= props.totalPages
  && jumpTarget.value !== props.page + 1,
);

watch(() => props.page, (page) => {
  jumpTarget.value = page + 1;
});

function changeSize(event: Event): void {
  const size = Number((event.target as HTMLSelectElement).value);
  if (PAGE_SIZES.includes(size as typeof PAGE_SIZES[number])) {
    emit('page-size-change', size);
  }
}

function changeJumpTarget(event: Event): void {
  const input = event.target as HTMLInputElement;
  jumpTarget.value = input.value === '' ? 0 : Number(input.value);
}

function jump(): void {
  if (canJump.value) emit('page-change', jumpTarget.value - 1);
}
</script>

<template>
  <nav
    class="admin-pagination"
    aria-label="列表分页"
  >
    <span class="admin-pagination__summary">
      共 {{ totalElements }} {{ itemLabel }} · 第 {{ totalPages === 0 ? 0 : page + 1 }} / {{ totalPages }} 页
    </span>
    <div class="admin-pagination__controls">
      <label>
        <span>每页</span>
        <select
          :value="pageSize"
          aria-label="每页显示条数"
          data-testid="admin-pagination-size"
          :disabled="disabled"
          @change="changeSize"
        >
          <option
            v-for="size in PAGE_SIZES"
            :key="size"
            :value="size"
          >
            {{ size }}
          </option>
        </select>
      </label>
      <form
        data-testid="admin-pagination-jump-form"
        @submit.prevent="jump"
      >
        <label>
          <span>跳转到第</span>
          <input
            :value="jumpTarget || ''"
            type="number"
            min="1"
            :max="Math.max(1, totalPages)"
            aria-label="跳转到页码"
            data-testid="admin-pagination-page-input"
            :disabled="disabled || totalPages === 0"
            @input="changeJumpTarget"
          >
          <span>页</span>
        </label>
        <button
          type="submit"
          :disabled="!canJump"
        >
          跳转
        </button>
      </form>
      <div class="admin-pagination__buttons">
        <button
          type="button"
          data-testid="admin-pagination-first-page"
          :disabled="disabled || page <= 0"
          aria-label="第一页"
          @click="emit('page-change', 0)"
        >
          «
        </button>
        <button
          type="button"
          data-testid="admin-pagination-previous-page"
          :disabled="disabled || page <= 0"
          @click="emit('page-change', page - 1)"
        >
          上一页
        </button>
        <button
          type="button"
          data-testid="admin-pagination-next-page"
          :disabled="disabled || page + 1 >= totalPages"
          @click="emit('page-change', page + 1)"
        >
          下一页
        </button>
        <button
          type="button"
          data-testid="admin-pagination-last-page"
          :disabled="disabled || totalPages === 0 || page + 1 >= totalPages"
          aria-label="最后一页"
          @click="emit('page-change', totalPages - 1)"
        >
          »
        </button>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.admin-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--admin-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.admin-pagination__controls,
.admin-pagination__controls form,
.admin-pagination__controls label,
.admin-pagination__buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin-pagination__controls form {
  margin: 0;
}

.admin-pagination__controls label {
  gap: 6px;
  white-space: nowrap;
}

.admin-pagination select,
.admin-pagination input,
.admin-pagination button {
  min-height: 34px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: #fff;
  color: var(--admin-text);
  font: inherit;
}

.admin-pagination select,
.admin-pagination input {
  padding: 0 8px;
}

.admin-pagination select {
  min-width: 68px;
}

.admin-pagination input {
  width: 68px;
}

.admin-pagination button {
  padding: 0 10px;
  font-weight: 600;
  cursor: pointer;
}

.admin-pagination button:hover:not(:disabled) {
  border-color: #93c5fd;
  color: var(--admin-primary-hover);
}

.admin-pagination button:disabled,
.admin-pagination select:disabled,
.admin-pagination input:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-pagination :focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .admin-pagination { align-items: flex-start; flex-direction: column; }
  .admin-pagination__controls { flex-wrap: wrap; }
  .admin-pagination__buttons { flex-wrap: wrap; }
}
</style>
