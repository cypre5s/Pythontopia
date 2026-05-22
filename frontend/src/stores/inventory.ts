import { defineStore } from 'pinia';
import { fetchInventory, useInventoryItem } from '@api/worldInventory';
import type { InventoryItemDto, ItemCategory } from '@api/dto/InventoryItemDto';
import type { ItemCatalogEntryDto } from '@api/dto/ItemCatalogEntryDto';

/**
 * 玩家背包 store。
 *
 * 数据来源：
 *   - bootstrap：world store 的 `bootstrap()` 一次性下发 inventory 数组，调用 `setBootstrapItems`；
 *   - lazy reload：`load()` 在需要刷新（例如商店购买后）时单独 GET /api/world/inventory；
 *   - mutation：`useItem(itemKey)` POST /api/world/inventory/{key}/use 返回新数量，本地 upsert。
 *
 * 注意：M4 阶段商人 / 礼物消耗都走后端推送 InventoryItemDto，不在前端做加减法。
 */
interface InventoryState {
  items: InventoryItemDto[];
  catalog: Record<string, ItemCatalogEntryDto>;
  loading: boolean;
  error: string | null;
}

export const useInventoryStore = defineStore('inventory', {
  state: (): InventoryState => ({
    items: [],
    catalog: {},
    loading: false,
    error: null,
  }),
  getters: {
    totalItems(state): number {
      return state.items.reduce((sum, item) => sum + item.qty, 0);
    },
    quantityOf(state) {
      return (itemKey: string): number =>
        state.items.find((item) => item.itemKey === itemKey)?.qty ?? 0;
    },
    byCategory(state) {
      return (category: ItemCategory): InventoryItemDto[] =>
        state.items.filter((item) => item.category === category);
    },
  },
  actions: {
    async load(): Promise<void> {
      this.loading = true;
      this.error = null;
      try {
        this.items = await fetchInventory();
      } catch (error) {
        this.error = error instanceof Error ? error.message : String(error);
      } finally {
        this.loading = false;
      }
    },
    async useItem(itemKey: string): Promise<InventoryItemDto> {
      const updated = await useInventoryItem(itemKey);
      this.upsertItem(updated);
      return updated;
    },
    upsertItem(item: InventoryItemDto): void {
      const idx = this.items.findIndex((entry) => entry.itemKey === item.itemKey);
      if (item.qty <= 0) {
        if (idx >= 0) {
          this.items.splice(idx, 1);
        }
        return;
      }
      if (idx >= 0) {
        const next = [...this.items];
        next[idx] = item;
        this.items = next;
      } else {
        this.items = [...this.items, item];
      }
    },
    setBootstrapItems(items: InventoryItemDto[]): void {
      this.items = items;
    },
    setCatalog(entries: ItemCatalogEntryDto[]): void {
      const next: Record<string, ItemCatalogEntryDto> = {};
      for (const entry of entries) {
        next[entry.itemKey] = entry;
      }
      this.catalog = next;
    },
  },
});
