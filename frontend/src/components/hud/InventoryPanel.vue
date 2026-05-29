<script setup lang="ts">
/**
 * InventoryPanel.vue —— W3 HUD 背包面板。
 *
 * 设计要点：
 *   1. 4 列像素 grid，每格显示 itemKey 对应的 emoji 占位图标 + qty 数字，
 *      鼠标 hover 弹出 tooltip 展示 displayName + description。
 *   2. 数据完全从 useInventoryStore 读取：items 即玩家当前背包，catalog 用于
 *      补全 description（store.catalog[itemKey].description 可缺省）；displayName
 *      已经在 InventoryItemDto 上，因此 catalog 不存在也能展示。
 *   3. 点击格子向父级 emit `use` 事件，由父级 Page 决定走 inventory.useItem(itemKey)
 *      还是装备 / 赠送等其它路径，组件本身不直接调 store 写操作，方便复用。
 *   4. 背包为空时显示「背包空空如也」占位，避免空白 grid 影响 UI 完整性。
 *   5. 不在此处订阅 Phaser / world store，跨场景调出（town / farm / mine 等）
 *      只依赖 Pinia store 自动响应，挂载到任何 Page 都能复用。
 */
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useInventoryStore } from '@stores/inventory';
import type {
  InventoryItemDto,
  ItemCategory,
} from '@api/dto/InventoryItemDto';

const emit = defineEmits<{
  (e: 'use', payload: { itemId: string }): void;
  (e: 'equip', payload: { itemId: string }): void;
  (e: 'gift', payload: { itemId: string }): void;
}>();

const store = useInventoryStore();
const { items, catalog } = storeToRefs(store);

const contextItem = ref<InventoryItemDto | null>(null);

function openContext(item: InventoryItemDto): void {
  contextItem.value = contextItem.value?.itemKey === item.itemKey ? null : item;
}

function closeContext(): void {
  contextItem.value = null;
}

function doAction(action: 'use' | 'equip' | 'gift', item: InventoryItemDto): void {
  contextItem.value = null;
  const payload = { itemId: item.itemKey };
  switch (action) {
    case 'use': emit('use', payload); break;
    case 'equip': emit('equip', payload); break;
    case 'gift': emit('gift', payload); break;
  }
}

function isEquippable(item: InventoryItemDto): boolean {
  return item.category === 'tool';
}

function isGiftable(item: InventoryItemDto): boolean {
  return item.category === 'gift' || item.category === 'consumable' || item.category === 'book';
}

const CATEGORY_ICONS: Record<ItemCategory, string> = {
  seed: 'Sd',
  tool: 'Tl',
  gift: 'Gf',
  consumable: 'Pt',
  book: 'Bk',
  quest: 'Qt',
};

const ITEM_KEY_ICONS: Record<string, string> = {
  watering_can: 'Wc',
  hoe: 'Hoe',
  axe: 'Axe',
  pickaxe: 'Pk',
  fertilizer: 'Ft',
  bread: 'Bd',
  coffee: 'Cf',
  flower: 'Fl',
  bug_jar: 'Bg',
};

function iconOf(item: InventoryItemDto): string {
  return ITEM_KEY_ICONS[item.itemKey] ?? CATEGORY_ICONS[item.category] ?? 'Itm';
}

function descriptionOf(itemKey: string): string {
  return catalog.value[itemKey]?.description ?? '';
}

const isEmpty = computed<boolean>(() => items.value.length === 0);
const hoveredKey = ref<string | null>(null);

function onEnter(itemKey: string): void {
  hoveredKey.value = itemKey;
}

function onLeave(itemKey: string): void {
  if (hoveredKey.value === itemKey) {
    hoveredKey.value = null;
  }
}

function onUse(item: InventoryItemDto): void {
  openContext(item);
}
</script>

<template>
  <section
    class="inventory-panel"
    data-testid="inventory-panel"
    aria-label="背包"
  >
    <header class="inventory-panel__header">
      <h3 class="inventory-panel__title">
        背包
      </h3>
      <span
        class="inventory-panel__count"
        data-testid="inventory-count"
      >共 {{ items.length }} 项</span>
    </header>

    <p
      v-if="isEmpty"
      class="inventory-panel__empty"
      data-testid="inventory-empty"
    >
      背包空空如也
    </p>

    <ul
      v-else
      class="inventory-panel__grid"
      data-testid="inventory-grid"
    >
      <li
        v-for="item in items"
        :key="item.itemKey"
        class="inventory-slot"
        :data-testid="`inventory-slot-${item.itemKey}`"
      >
        <button
          type="button"
          class="inventory-slot__btn"
          :data-testid="`inventory-use-${item.itemKey}`"
          @click="onUse(item)"
          @mouseenter="onEnter(item.itemKey)"
          @mouseleave="onLeave(item.itemKey)"
          @focus="onEnter(item.itemKey)"
          @blur="onLeave(item.itemKey)"
        >
          <span
            class="inventory-slot__icon"
            aria-hidden="true"
          >{{ iconOf(item) }}</span>
          <span
            class="inventory-slot__qty"
            :data-testid="`inventory-qty-${item.itemKey}`"
          >×{{ item.qty }}</span>
        </button>
        <div
          v-if="hoveredKey === item.itemKey && contextItem?.itemKey !== item.itemKey"
          class="inventory-tooltip"
          role="tooltip"
          :data-testid="`inventory-tooltip-${item.itemKey}`"
        >
          <div class="inventory-tooltip__name">
            {{ item.displayName }}
          </div>
          <div
            v-if="descriptionOf(item.itemKey)"
            class="inventory-tooltip__desc"
          >
            {{ descriptionOf(item.itemKey) }}
          </div>
        </div>
        <div
          v-if="contextItem?.itemKey === item.itemKey"
          class="inventory-context"
          data-testid="inventory-context"
        >
          <button
            type="button"
            class="ctx-btn"
            data-testid="inventory-action-use"
            @click="doAction('use', item)"
          >
            [USE] 使用
          </button>
          <button
            v-if="isEquippable(item)"
            type="button"
            class="ctx-btn"
            data-testid="inventory-action-equip"
            @click="doAction('equip', item)"
          >
            装备
          </button>
          <button
            v-if="isGiftable(item)"
            type="button"
            class="ctx-btn"
            data-testid="inventory-action-gift"
            @click="doAction('gift', item)"
          >
            [GIFT] 赠送 NPC
          </button>
          <button
            type="button"
            class="ctx-btn ctx-btn--cancel"
            @click="closeContext"
          >
            ✕ 取消
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.inventory-panel {
  position: relative;
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  padding: 10px 12px;
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12', 'PingFang SC', monospace);
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  min-width: 220px;
  user-select: none;
  image-rendering: pixelated;
}

.inventory-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  background: var(--pyt-panel-header-bg);
  border-bottom: 2px solid var(--pyt-panel-border);
  margin: -10px -12px 8px;
  padding: 10px 12px;
  color: var(--pyt-text-accent);
  font-size: 13px;
}

.inventory-panel__title {
  margin: 0;
  font-size: 14px;
  letter-spacing: 1px;
  text-shadow: 1px 1px 0 #2a1a05;
}

.inventory-panel__count {
  font-size: 11px;
  color: var(--pyt-text-secondary);
}

.inventory-panel__empty {
  margin: 18px 0 6px;
  text-align: center;
  color: var(--pyt-text-accent);
  font-size: 12px;
}

.inventory-panel__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.inventory-slot {
  position: relative;
}

.inventory-slot__btn {
  width: 100%;
  aspect-ratio: 1 / 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 4px;
  background: rgba(0, 0, 0, 0.2);
  border: 2px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  cursor: pointer;
  color: var(--pyt-text-primary);
  font-family: inherit;
  box-shadow: 1px 1px 0 var(--pyt-panel-border-dark);
  transition: transform 80ms ease, background 80ms ease;
}

.inventory-slot__btn:hover,
.inventory-slot__btn:focus-visible {
  background: var(--pyt-btn-hover-bg);
  transform: translate(-1px, -1px);
  outline: none;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
}

.inventory-slot__icon {
  font-size: 20px;
  line-height: 1;
}

.inventory-slot__qty {
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-text-accent);
}

.inventory-tooltip {
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  min-width: 140px;
  max-width: 220px;
  padding: 6px 8px;
  background: var(--pyt-panel-bg);
  border: 2px solid var(--pyt-panel-border);
  border-radius: 2px;
  color: var(--pyt-text-accent);
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;
  z-index: 70;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark);
  white-space: normal;
}

.inventory-tooltip__name {
  font-weight: 700;
  color: var(--pyt-text-accent);
  margin-bottom: 2px;
}

.inventory-tooltip__desc {
  color: var(--pyt-text-secondary);
  font-size: 11px;
}

.inventory-context {
  position: absolute;
  top: calc(100% + 4px);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 100px;
  padding: 6px;
  background: var(--pyt-panel-bg);
  border: 2px solid var(--pyt-panel-border);
  border-radius: 2px;
  z-index: 80;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark);
}

.ctx-btn {
  width: 100%;
  padding: 5px 8px;
  background: var(--pyt-btn-bg);
  border: 2px solid var(--pyt-btn-border);
  border-radius: 2px;
  font-family: inherit;
  font-size: 11px;
  color: var(--pyt-text-primary);
  cursor: pointer;
  text-align: left;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
  transition: background 80ms;
}

.ctx-btn:hover {
  background: var(--pyt-btn-hover-bg);
}

.ctx-btn--cancel {
  background: rgba(230, 57, 70, 0.15);
  border-color: var(--pyt-danger);
  color: var(--pyt-danger);
}

.ctx-btn--cancel:hover {
  background: rgba(230, 57, 70, 0.3);
}
</style>
