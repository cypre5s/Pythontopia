<script setup lang="ts">
/**
 * 百晓工坊右侧「书房」抽屉：可收起，内含 ResourceListPanel（书房列表）+
 * ResourceDetailPanel（详情）。详情走「替换式」——选中资源后用详情替换列表，顶部给
 * 「← 返回书房」。open / selectedId 由父级（WorkshopPage，绑路由 ?id）控制。
 */
import ResourceListPanel from '@/components/workshop/ResourceListPanel.vue';
import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';

const props = defineProps<{
  open: boolean;
  selectedId: number | null;
}>();

const emit = defineEmits<{
  (event: 'update:open', value: boolean): void;
  (event: 'back'): void;
}>();

function toggle(): void {
  emit('update:open', !props.open);
}

function back(): void {
  emit('back');
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
        <button
          v-if="props.selectedId !== null"
          type="button"
          class="drawer__back"
          data-testid="studio-drawer-back"
          @click="back"
        >
          ← 返回书房
        </button>
        <span
          v-else
          class="drawer__title"
        >我的书房</span>
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
        <ResourceDetailPanel
          v-if="props.selectedId !== null"
          :resource-id="props.selectedId"
        />
        <ResourceListPanel v-else />
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
.drawer__back {
  font-family: inherit;
  font-size: 12px;
  padding: 3px 10px;
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-text-primary);
}
.drawer__back:active {
  transform: translate(2px, 2px);
  box-shadow: none;
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
