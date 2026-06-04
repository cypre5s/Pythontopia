<script setup lang="ts">
/**
 * `/` 斜杠命令菜单（展示型），与 CoursewareMentionMenu 同款星露谷牛皮纸下拉。
 * 由 useSlashCommand 提供 groups / activeIndex，选中通过 select 事件回传。
 */
import { computed } from 'vue';
import type { SlashCommand, SlashCommandGroup } from '@/composables/useSlashCommand';

const props = defineProps<{
  visible: boolean;
  groups: SlashCommandGroup[];
  activeIndex: number;
}>();

const emit = defineEmits<{
  (event: 'select', cmd: SlashCommand): void;
}>();

interface FlatEntry {
  item: SlashCommand;
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

function entriesOf(group: SlashCommandGroup): FlatEntry[] {
  return group.items.map((item) => ({
    item,
    index: flatIndexByToken.value.get(item.token) ?? -1,
  }));
}
</script>

<template>
  <div
    v-if="props.visible && props.groups.length > 0"
    class="slash-menu"
    role="listbox"
    data-testid="slash-command-menu"
  >
    <div
      v-for="group in props.groups"
      :key="group.group"
      class="slash-group"
    >
      <div class="slash-group__title">
        {{ group.group }}
      </div>
      <button
        v-for="entry in entriesOf(group)"
        :key="entry.item.token"
        type="button"
        class="slash-item"
        :class="{ 'slash-item--active': entry.index === props.activeIndex }"
        role="option"
        :aria-selected="entry.index === props.activeIndex"
        :data-testid="`slash-item-${entry.item.intent}`"
        @mousedown.prevent="emit('select', entry.item)"
      >
        <span
          class="slash-item__icon"
          aria-hidden="true"
        >{{ entry.item.icon }}</span>
        <span class="slash-item__token">{{ entry.item.token }}</span>
        <span class="slash-item__label">{{ entry.item.label }}</span>
        <span class="slash-item__hint">{{ entry.item.hint }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.slash-menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 8px);
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: min(300px, 50vh);
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
.slash-group + .slash-group {
  border-top: 2px dashed rgba(110, 79, 29, 0.35);
  padding-top: 4px;
}
.slash-group__title {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
  letter-spacing: 0.5px;
}
.slash-item {
  display: grid;
  grid-template-columns: 20px minmax(72px, auto) auto 1fr;
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
.slash-item:hover,
.slash-item--active {
  background: #ffe098;
  box-shadow: inset 0 0 0 1px var(--pyt-color-text-hint);
}
.slash-item__icon {
  text-align: center;
  color: #8c6a23;
  font-weight: 700;
}
.slash-item__token {
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
.slash-item__label {
  font-weight: 600;
  white-space: nowrap;
}
.slash-item__hint {
  color: var(--pyt-color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (prefers-reduced-motion: reduce) {
  .slash-item {
    transition: none;
  }
}
</style>
