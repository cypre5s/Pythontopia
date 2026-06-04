<script setup lang="ts">
/**
 * 课件 `@` 引用菜单（展示型）。仿 Alethicode AtMentionMenu，像素牛皮纸风格。
 * 由 useCoursewareMention 提供 groups / activeIndex，选中通过 select 事件回传。
 */
import { computed } from 'vue';
import type { MentionGroup, MentionItem } from '@/composables/useCoursewareMention';

const props = defineProps<{
  visible: boolean;
  groups: MentionGroup[];
  activeIndex: number;
}>();

const emit = defineEmits<{
  (event: 'select', item: MentionItem): void;
}>();

interface FlatEntry {
  item: MentionItem;
  index: number;
}

const flatIndexByToken = computed<Map<string, number>>(() => {
  const map = new Map<string, number>();
  let cursor = 0;
  for (const group of props.groups) {
    for (const item of group.items) {
      map.set(item.token, cursor);
      cursor += 1;
    }
  }
  return map;
});

function entriesOf(group: MentionGroup): FlatEntry[] {
  return group.items.map((item) => ({ item, index: flatIndexByToken.value.get(item.token) ?? -1 }));
}
</script>

<template>
  <div
    v-if="props.visible && props.groups.length > 0"
    class="mention-menu"
    role="listbox"
    data-testid="courseware-mention-menu"
  >
    <div
      v-for="group in props.groups"
      :key="group.group"
      class="mention-group"
    >
      <div class="mention-group__title">
        {{ group.group }}
      </div>
      <button
        v-for="entry in entriesOf(group)"
        :key="entry.item.token"
        type="button"
        class="mention-item"
        :class="{ 'mention-item--active': entry.index === props.activeIndex }"
        role="option"
        :aria-selected="entry.index === props.activeIndex"
        :data-testid="`mention-item-${entry.item.token}`"
        @mousedown.prevent="emit('select', entry.item)"
      >
        <span class="mention-item__token">{{ entry.item.token }}</span>
        <span class="mention-item__label">{{ entry.item.label }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.mention-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 8px);
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: min(280px, 48vh);
  overflow-y: auto;
  padding: 8px 6px;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 0 6px 0 rgba(0, 0, 0, 0.18);
  z-index: 60;
  font-family: var(--pyt-font-pixel);
  scrollbar-width: thin;
}
.mention-group + .mention-group {
  border-top: 2px dashed rgba(110, 79, 29, 0.35);
  padding-top: 4px;
}
.mention-group__title {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
  letter-spacing: 0.5px;
}
.mention-item {
  display: grid;
  grid-template-columns: minmax(96px, auto) 1fr;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 6px 8px;
  border: 0;
  background: transparent;
  text-align: left;
  font: inherit;
  font-size: 12px;
  color: #3a2a14;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 120ms ease;
}
.mention-item:hover,
.mention-item--active {
  background: #ffe098;
  box-shadow: inset 0 0 0 1px var(--pyt-color-text-hint);
}
.mention-item__token {
  font-family: monospace;
  font-size: 11px;
  color: #1b4a6b;
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mention-item__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (prefers-reduced-motion: reduce) {
  .mention-item {
    transition: none;
  }
}
</style>
