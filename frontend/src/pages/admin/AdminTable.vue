<script setup lang="ts">
withDefaults(defineProps<{
  label: string;
  minWidth?: number;
}>(), {
  minWidth: 820,
});
</script>

<template>
  <div
    class="admin-table-scroll"
    role="region"
    :aria-label="label"
    tabindex="0"
    :style="{ '--admin-table-min-width': `${minWidth}px` }"
  >
    <slot />
  </div>
</template>

<style>
.admin-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  -webkit-overflow-scrolling: touch;
  border: 1px solid var(--admin-border, #e2e8f0);
  border-radius: 10px;
}

.admin-table-scroll:focus-visible {
  outline: 3px solid var(--admin-focus, #3b82f6);
  outline-offset: 2px;
}

.admin-table-scroll > table {
  width: 100%;
  min-width: var(--admin-table-min-width, 820px);
  border-collapse: separate;
  border-spacing: 0;
  color: var(--admin-text, #0f172a);
  font-size: 13px;
  table-layout: fixed;
  text-align: left;
}

.admin-table-scroll > table th {
  height: 44px;
  padding: 0 16px;
  border-bottom: 1px solid var(--admin-border, #e2e8f0);
  background: var(--admin-table-header, #eef4fb);
  color: var(--admin-text, #0f172a);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.admin-table-scroll > table td {
  height: 52px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--admin-border, #e2e8f0);
  color: #334155;
  vertical-align: middle;
}

.admin-table-scroll > table tbody tr:last-child td {
  border-bottom: 0;
}

.admin-table-scroll > table tbody tr:hover {
  background: var(--admin-table-hover, #f8fbff);
}
</style>
