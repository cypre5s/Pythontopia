export type ItemCategory =
  | 'seed'
  | 'tool'
  | 'gift'
  | 'consumable'
  | 'book'
  | 'quest';

export interface InventoryItemDto {
  itemKey: string;
  displayName: string;
  category: ItemCategory;
  qty: number;
  basePrice: number;
}
