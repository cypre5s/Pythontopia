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
  border: 1px solid var(--admin-panel-border, #e2e8f0);
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
  color: var(--admin-text-strong, #0f172a);
  font-size: 13px;
  table-layout: fixed;
  text-align: left;
}

.admin-table-scroll > table th {
  height: var(--table-header-height, 44px);
  padding: 0 16px;
  border-bottom: 1px solid var(--admin-panel-border, #e2e8f0);
  background: var(--admin-table-header-bg, #eef4fb);
  color: var(--admin-text-strong, #0f172a);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.admin-table-scroll > table th.admin-table__sticky {
  position: sticky;
  right: 0;
  z-index: 2;
  background: var(--admin-table-header-bg, #eef4fb);
}

.admin-table-scroll > table td {
  height: var(--table-row-height, 52px);
  padding: 10px 16px;
  border-bottom: 1px solid var(--admin-panel-border, #e2e8f0);
  color: #334155;
  vertical-align: middle;
}

.admin-table-scroll > table td.admin-table__sticky {
  position: sticky;
  right: 0;
  z-index: 1;
  background: var(--admin-panel-bg, #fff);
  box-shadow: -8px 0 8px rgb(15 23 42 / 5%);
}

.admin-table-scroll > table tbody tr:last-child td {
  border-bottom: 0;
}

.admin-table-scroll > table tbody tr:hover {
  background: var(--admin-table-hover-bg, #f8fbff);
}

.admin-table-scroll > table tbody tr:hover td.admin-table__sticky,
.admin-table-scroll > table tbody tr.is-selected td.admin-table__sticky {
  background: var(--admin-table-hover-bg, #f8fbff);
}
</style>
