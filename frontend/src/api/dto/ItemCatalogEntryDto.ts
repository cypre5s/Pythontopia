import type { ItemCategory } from './InventoryItemDto';

export interface ItemCatalogEntryDto {
  itemKey: string;
  displayName: string;
  category: ItemCategory;
  basePrice: number;
  description?: string;
}
