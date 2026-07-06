<script setup lang="ts">
/**
 * 百晓工坊右侧「书房」抽屉：可收起。展开时**常驻**资源列表（ResourceListPanel）——
 * 学生点击某一行不再「替换式」打开详情，而是由父级（WorkshopPage，store openResourceId）
 * 弹出资源详情弹窗（ResourceDetailPanel）。本组件只负责列表 + 收起/展开。
 */
import ResourceListPanel from '@/components/workshop/ResourceListPanel.vue';

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (event: 'update:open', value: boolean): void;
}>();

function toggle(): void {
  emit('update:open', !props.open);
}
</script>

<template>
  <aside
    class="drawer"
    :class="{ 'drawer--collapsed': !props.open }"
    data-testid="studio-drawer"
  >
    <button
      v-if="!props.open"
      type="button"
      class="drawer__handle"
      data-testid="studio-drawer-expand"
      title="展开书房"
      @click="toggle"
    >
      <span aria-hidden="true">📚</span>
      <span class="drawer__handle-text">我的书房</span>
      <span aria-hidden="true">▾</span>
    </button>

    <template v-else>
      <header class="drawer__bar">
        <span class="drawer__title">我的书房</span>
        <button
          type="button"
          class="drawer__collapse"
          data-testid="studio-drawer-collapse"
          title="收起书房"
          @click="toggle"
        >
          ›
        </button>
      </header>

      <div class="drawer__body">
        <ResourceListPanel />
      </div>
    </template>
  </aside>
</template>

<style scoped>
.drawer {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: inset 0 0 0 2px rgba(255, 247, 230, 0.55);
  overflow: hidden;
  font-family: var(--pyt-font-pixel);
}
.drawer--collapsed {
  height: auto;
  background: var(--pyt-color-bg-elevated);
  border-style: dashed;
}
.drawer__handle {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: var(--pyt-color-text-primary);
}
.drawer__handle:hover {
  background: var(--pyt-color-warning-bg);
}
.drawer__handle-text {
  letter-spacing: 1px;
  font-weight: 700;
  font-size: 13px;
}
.drawer__bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.4);
}
.drawer__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.drawer__collapse {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  width: 24px;
  height: 24px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: 4px;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
}
.drawer__collapse:hover {
  background: var(--pyt-color-warning-bg);
}
.drawer__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}
</style>
