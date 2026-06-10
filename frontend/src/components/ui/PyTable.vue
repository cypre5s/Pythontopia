<script setup lang="ts">
/**
 * PyTable：像素描边数据表（教师名单/KC 列表等）。columns + rows 驱动，行 hover 高亮。
 * 每列可用具名插槽 `#<col.key>="{ row, value }"` 自定义单元格渲染。
 */
interface Column {
  key: string;
  label: string;
  align?: 'left' | 'center' | 'right';
}

withDefaults(
  defineProps<{
    columns: Column[];
    rows: Record<string, unknown>[];
    rowKey?: string;
    empty?: string;
  }>(),
  {
    rowKey: undefined,
    empty: '暂无数据',
  },
);

function cellText(value: unknown): string {
  return value == null ? '' : String(value);
}
</script>

<template>
  <table class="py-table">
    <thead>
      <tr>
        <th
          v-for="col in columns"
          :key="col.key"
          :class="`py-table__th py-table__th--${col.align ?? 'left'}`"
          scope="col"
        >
          {{ col.label }}
        </th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="rows.length === 0">
        <td
          class="py-table__empty"
          :colspan="columns.length"
        >
          {{ empty }}
        </td>
      </tr>
      <tr
        v-for="(row, index) in rows"
        v-else
        :key="rowKey ? String(row[rowKey]) : index"
        class="py-table__row"
      >
        <td
          v-for="col in columns"
          :key="col.key"
          :class="`py-table__td py-table__td--${col.align ?? 'left'}`"
        >
          <slot
            :name="col.key"
            :row="row"
            :value="row[col.key]"
          >
            {{ cellText(row[col.key]) }}
          </slot>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.py-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}
.py-table__th {
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-hint);
  font-weight: 700;
  padding: 8px 12px;
  border-bottom: 2px solid var(--pyt-color-border);
  white-space: nowrap;
}
.py-table__td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--pyt-color-border-soft);
}
.py-table__th--left,
.py-table__td--left {
  text-align: left;
}
.py-table__th--center,
.py-table__td--center {
  text-align: center;
}
.py-table__th--right,
.py-table__td--right {
  text-align: right;
}
.py-table__row:hover {
  background: var(--pyt-color-warning-bg);
}
.py-table__row:last-child .py-table__td {
  border-bottom: none;
}
.py-table__empty {
  padding: 16px;
  text-align: center;
  color: var(--pyt-color-text-muted);
}
</style>
