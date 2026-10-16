<script setup lang="ts">
import { computed } from 'vue';

const PAGE_SIZES = [10, 20, 50, 100] as const;

const props = defineProps<{
  page: number;
  totalPages: number;
  pageSize: number;
  totalElements: number;
  itemLabel: string;
  disabled?: boolean;
}>();

const pageItems = computed<Array<number | 'ellipsis'>>(() => {
  if (props.totalPages <= 7) {
    return Array.from({ length: props.totalPages }, (_, index) => index);
  }

  const start = Math.max(0, Math.min(props.page - 2, props.totalPages - 5));
  const end = Math.min(props.totalPages - 1, start + 4);
  const items: Array<number | 'ellipsis'> = [];
  if (start > 0) {
    items.push(0);
    if (start > 1) items.push('ellipsis');
  }
  for (let page = start; page <= end; page += 1) items.push(page);
  if (end < props.totalPages - 1) {
    if (end < props.totalPages - 2) items.push('ellipsis');
    items.push(props.totalPages - 1);
  }
  return items;
});

const emit = defineEmits<{
  (event: 'page-change', page: number): void;
  (event: 'page-size-change', pageSize: number): void;
}>();

function changeSize(event: Event): void {
  const size = Number((event.target as HTMLSelectElement).value);
  if (PAGE_SIZES.includes(size as typeof PAGE_SIZES[number])) {
    emit('page-size-change', size);
  }
}

</script>

<template>
  <nav
    class="admin-pagination"
    aria-label="列表分页"
  >
    <span class="admin-pagination__summary">
      共 {{ totalElements }} {{ itemLabel }}
    </span>
    <div class="admin-pagination__controls">
      <label>
        <span>每页</span>
        <select
          class="admin-control"
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
      <div class="admin-pagination__buttons">
        <button
          class="admin-button"
          type="button"
          data-testid="admin-pagination-previous-page"
          aria-label="上一页"
          :disabled="disabled || page <= 0"
          @click="emit('page-change', page - 1)"
        >
          ‹
        </button>
        <template
          v-for="(pageItem, index) in pageItems"
          :key="`${pageItem}-${index}`"
        >
          <span
            v-if="pageItem === 'ellipsis'"
            class="admin-pagination__ellipsis"
            aria-hidden="true"
          >…</span>
          <button
            v-else
            class="admin-button admin-pagination__page"
            :class="{ 'admin-button--primary': pageItem === page }"
            type="button"
            :data-testid="`admin-pagination-page-${pageItem + 1}`"
            :aria-label="`第 ${pageItem + 1} 页`"
            :aria-current="pageItem === page ? 'page' : undefined"
            :disabled="disabled || pageItem === page"
            @click="emit('page-change', pageItem)"
          >
            {{ pageItem + 1 }}
          </button>
        </template>
        <button
          class="admin-button"
          type="button"
          data-testid="admin-pagination-next-page"
          aria-label="下一页"
          :disabled="disabled || page + 1 >= totalPages"
          @click="emit('page-change', page + 1)"
        >
          ›
        </button>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.admin-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
  color: var(--admin-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.admin-pagination__controls,
.admin-pagination__controls label,
.admin-pagination__buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin-pagination__controls {
  justify-content: center;
  flex-wrap: wrap;
}

.admin-pagination__controls label {
  gap: 6px;
  white-space: nowrap;
}

.admin-pagination select {
  min-width: 68px;
}

.admin-pagination__page {
  min-width: 36px;
  padding: 0 10px;
}

.admin-pagination__ellipsis {
  min-width: 16px;
  text-align: center;
}

@media (max-width: 640px) {
  .admin-pagination { align-items: center; flex-direction: column; }
  .admin-pagination__controls { width: 100%; }
  .admin-pagination__buttons { flex-wrap: wrap; }
}
</style>
