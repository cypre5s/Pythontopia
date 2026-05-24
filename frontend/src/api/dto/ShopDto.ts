// R-13：与后端 `com.pythontopia.world.shop.dto.ShopListingDto` 严格对齐。
// 历史前端 DTO 写了 displayName/category/basePrice/price 等不存在的字段，会导致
// 商铺页运行时 toFixed/Number 报错或者空白。后端真实 record：
//   shopKey/itemKey/priceCoins(int)/priceGems(int)/stock(Integer)/availableDays(List<Integer>)

export interface ShopListingDto {
  shopKey: string;
  itemKey: string;
  priceCoins: number;
  priceGems: number;
  stock: number | null;
  availableDays: number[] | null;
}

export interface ShopBuyRequest {
  itemKey: string;
  qty: number;
}

// 与后端 `BuyResponse` 对齐（字段允许后端补齐，前端先按主要字段使用）
export interface ShopBuyResponseDto {
  itemKey: string;
  qty: number;
  totalCoins: number;
  totalGems: number;
  remainingStock: number | null;
}
