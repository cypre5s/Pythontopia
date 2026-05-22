import type { ItemCategory } from './InventoryItemDto';

export interface ShopListingDto {
  itemKey: string;
  displayName: string;
  category: ItemCategory;
  basePrice: number;
  price: number;
  stock: number | null;
  description?: string;
}

export interface ShopDto {
  shopKey: string;
  displayName: string;
  listings: ShopListingDto[];
}

export interface ShopBuyRequest {
  itemKey: string;
  qty: number;
}

export interface ShopBuyResponseDto {
  itemKey: string;
  qty: number;
  totalPrice: number;
  newCoins: number;
}
