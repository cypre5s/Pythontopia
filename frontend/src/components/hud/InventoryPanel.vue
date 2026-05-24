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
  seed: '🌱',
  tool: '🔧',
  gift: '🎁',
  consumable: '🧪',
  book: '📖',
  quest: '📜',
};

const ITEM_KEY_ICONS: Record<string, string> = {
  watering_can: '💧',
  hoe: '⛏️',
  axe: '🪓',
  pickaxe: '⚒️',
  fertilizer: '🧴',
  bread: '🍞',
  coffee: '☕',
  flower: '🌸',
  bug_jar: '🪲',
};

function iconOf(item: InventoryItemDto): string {
  return ITEM_KEY_ICONS[item.itemKey] ?? CATEGORY_ICONS[item.category] ?? '📦';
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
            🧪 使用
          </button>
          <button
            v-if="isEquippable(item)"
            type="button"
            class="ctx-btn"
            data-testid="inventory-action-equip"
            @click="doAction('equip', item)"
          >
            ⚔ 装备
          </button>
          <button
            v-if="isGiftable(item)"
            type="button"
            class="ctx-btn"
            data-testid="inventory-action-gift"
            @click="doAction('gift', item)"
          >
            🎁 赠送 NPC
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
  background: rgba(34, 24, 14, 0.88);
  border: 3px solid #d4af37;
  border-radius: 8px;
  padding: 10px 12px;
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', 'Noto Sans CJK SC', monospace;
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.4);
  min-width: 220px;
  user-select: none;
}

.inventory-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
  color: #f1d35a;
  font-size: 13px;
}

.inventory-panel__title {
  margin: 0;
  font-size: 14px;
  letter-spacing: 1px;
}

.inventory-panel__count {
  font-size: 11px;
  color: rgba(241, 211, 90, 0.8);
}

.inventory-panel__empty {
  margin: 18px 0 6px;
  text-align: center;
  color: #ffd166;
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
  background: #fdf3d0;
  border: 2px solid #4d3613;
  border-radius: 4px;
  cursor: pointer;
  color: #4d3613;
  font-family: inherit;
  box-shadow: 1px 1px 0 #4d3613;
  transition: transform 80ms ease, background 80ms ease;
}

.inventory-slot__btn:hover,
.inventory-slot__btn:focus-visible {
  background: #fff7c2;
  transform: translate(-1px, -1px);
  outline: none;
  box-shadow: 2px 2px 0 #4d3613;
}

.inventory-slot__icon {
  font-size: 20px;
  line-height: 1;
}

.inventory-slot__qty {
  font-size: 11px;
  font-weight: 700;
  color: #4d3613;
}

.inventory-tooltip {
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  min-width: 140px;
  max-width: 220px;
  padding: 6px 8px;
  background: rgba(28, 24, 16, 0.95);
  border: 2px solid #d4af37;
  border-radius: 4px;
  color: #ffd166;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;
  z-index: 70;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.4);
  white-space: normal;
}

.inventory-tooltip__name {
  font-weight: 700;
  color: #ffe57f;
  margin-bottom: 2px;
}

.inventory-tooltip__desc {
  color: #f1d8a4;
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
  background: rgba(28, 24, 16, 0.96);
  border: 2px solid #d4af37;
  border-radius: 6px;
  z-index: 80;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
}

.ctx-btn {
  width: 100%;
  padding: 5px 8px;
  background: #fdf3d0;
  border: 2px solid #4d3613;
  border-radius: 3px;
  font-family: inherit;
  font-size: 11px;
  color: #4d3613;
  cursor: pointer;
  text-align: left;
  transition: background 80ms;
}

.ctx-btn:hover {
  background: #ffe57f;
}

.ctx-btn--cancel {
  background: rgba(193, 74, 47, 0.15);
  border-color: #b3402a;
  color: #ff7466;
}

.ctx-btn--cancel:hover {
  background: rgba(193, 74, 47, 0.3);
}
</style>
