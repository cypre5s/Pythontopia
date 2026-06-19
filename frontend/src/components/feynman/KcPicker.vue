<script setup lang="ts">
/**
 * 知识点选择器：对 65 个 KC 中文名做下拉筛选 + 模糊搜索的无障碍 combobox。
 * 遵循 W3C APG combobox 规范：role=combobox/listbox/option、aria-activedescendant
 * 虚拟焦点（真实焦点留在输入框），上下键导航、Enter 选中、Esc 收起。
 * 只显示中文名（不暴露 KC 编码），但可按「循环 / 列表 / 文件 / 031」等关键词或编码搜索。
 */
import { computed, nextTick, ref } from 'vue';
import { KC_NAMES } from '@/data/kcNames';

const emit = defineEmits<{ (e: 'select', code: string): void }>();

interface KcEntry {
  code: string;
  name: string;
}

const ALL: readonly KcEntry[] = Object.entries(KC_NAMES)
  .map(([code, name]) => ({ code, name }))
  .sort((a, b) => a.code.localeCompare(b.code));

const LIST_ID = 'kc-picker-listbox';
const query = ref('');
const open = ref(false);
const activeIndex = ref(-1);
const inputRef = ref<HTMLInputElement | null>(null);

function isSubsequence(haystack: string, needle: string): boolean {
  if (!needle) return true;
  let i = 0;
  for (const ch of haystack) {
    if (ch === needle[i]) {
      i += 1;
      if (i === needle.length) return true;
    }
  }
  return false;
}

// 评分：名字子串命中（越靠前越高）> 编码子串 > 名字子序列模糊 > 编码子序列；负分=不匹配。
function score(entry: KcEntry, q: string): number {
  if (!q) return 1;
  const name = entry.name.toLowerCase();
  const code = entry.code.toLowerCase();
  const idx = name.indexOf(q);
  if (idx >= 0) return 1000 - idx;
  if (code.includes(q)) return 500;
  if (isSubsequence(name, q)) return 100;
  if (isSubsequence(code, q)) return 50;
  return -1;
}

const filtered = computed<KcEntry[]>(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return [...ALL];
  return ALL.map((entry) => ({ entry, s: score(entry, q) }))
    .filter((x) => x.s > 0)
    .sort((a, b) => b.s - a.s || a.entry.code.localeCompare(b.entry.code))
    .map((x) => x.entry);
});

const activeId = computed(() =>
  open.value && activeIndex.value >= 0 ? `kc-opt-${activeIndex.value}` : undefined,
);

function openList(): void {
  open.value = true;
  if (activeIndex.value < 0 && filtered.value.length > 0) {
    activeIndex.value = 0;
  }
}

function closeList(): void {
  open.value = false;
  activeIndex.value = -1;
}

function choose(entry: KcEntry | undefined): void {
  if (!entry) return;
  query.value = entry.name;
  closeList();
  emit('select', entry.code);
}

function onInput(): void {
  open.value = true;
  activeIndex.value = filtered.value.length > 0 ? 0 : -1;
}

async function move(delta: number): Promise<void> {
  if (!open.value) {
    openList();
    return;
  }
  const total = filtered.value.length;
  if (total === 0) return;
  activeIndex.value = (activeIndex.value + delta + total) % total;
  await nextTick();
  document.getElementById(`kc-opt-${activeIndex.value}`)?.scrollIntoView?.({ block: 'nearest' });
}

function onEnter(event: Event): void {
  if (open.value && activeIndex.value >= 0) {
    event.preventDefault();
    choose(filtered.value[activeIndex.value]);
  }
}
</script>

<template>
  <div
    class="kc-picker"
    @keydown.escape.stop.prevent="closeList"
  >
    <label
      id="kc-picker-label"
      class="kc-picker__label"
      for="kc-picker-input"
    >选择知识点开始</label>
    <div class="kc-picker__field">
      <input
        id="kc-picker-input"
        ref="inputRef"
        v-model="query"
        type="text"
        class="kc-picker__input"
        role="combobox"
        aria-autocomplete="list"
        :aria-controls="LIST_ID"
        :aria-expanded="open"
        :aria-activedescendant="activeId"
        autocomplete="off"
        placeholder="搜索知识点，如「循环」「列表」「文件」"
        data-testid="kc-picker-input"
        @focus="openList"
        @input="onInput"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.enter="onEnter"
        @blur="closeList"
      >
      <button
        type="button"
        class="kc-picker__toggle"
        :aria-label="open ? '收起知识点列表' : '展开知识点列表'"
        tabindex="-1"
        @mousedown.prevent="open ? closeList() : (inputRef?.focus(), openList())"
      >
        <span aria-hidden="true">▾</span>
      </button>
    </div>
    <ul
      v-show="open"
      :id="LIST_ID"
      class="kc-picker__list"
      role="listbox"
      aria-labelledby="kc-picker-label"
      data-testid="kc-picker-list"
    >
      <li
        v-if="filtered.length === 0"
        class="kc-picker__empty"
      >
        没有匹配的知识点，换个词试试
      </li>
      <li
        v-for="(entry, index) in filtered"
        :id="`kc-opt-${index}`"
        :key="entry.code"
        class="kc-picker__option"
        :class="{ 'kc-picker__option--active': index === activeIndex }"
        role="option"
        :aria-selected="index === activeIndex"
        @mousedown.prevent="choose(entry)"
        @mousemove="activeIndex = index"
      >
        {{ entry.name }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.kc-picker {
  position: relative;
  display: grid;
  gap: var(--pyt-space-2);
}

.kc-picker__label {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
}

.kc-picker__field {
  display: flex;
  align-items: stretch;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  background: var(--pyt-color-bg-primary);
  overflow: hidden;
}

.kc-picker__field:focus-within {
  border-color: var(--pyt-color-primary);
  box-shadow: 0 0 0 2px var(--pyt-color-gold);
}

.kc-picker__input {
  flex: 1 1 auto;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--pyt-color-text-primary);
  padding: var(--pyt-space-2) var(--pyt-space-3);
  font: inherit;
}

.kc-picker__input:focus {
  outline: none;
}

.kc-picker__toggle {
  flex: 0 0 auto;
  border: none;
  border-left: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  padding: 0 var(--pyt-space-3);
  cursor: pointer;
  font-size: var(--pyt-font-size-sm);
}

.kc-picker__list {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: var(--pyt-z-popover);
  max-height: 280px;
  overflow-y: auto;
  margin: 0;
  padding: var(--pyt-space-1);
  list-style: none;
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-modal);
}

.kc-picker__option {
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  line-height: 1.5;
}

.kc-picker__option--active {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
}

.kc-picker__empty {
  padding: var(--pyt-space-3);
  color: var(--pyt-color-text-muted);
  text-align: center;
}
</style>
