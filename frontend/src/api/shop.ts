// R-13：商铺 API 客户端，配合 ShopPage.vue 与后端 ShopController 对齐。
// 路径：`/api/world/shops/{shopKey}` GET 列表 + POST `/buy` 购买。
// 不暴露 axios 实例细节，业务层只调函数。
import { http } from './http';
import type {
  ShopListingDto,
  ShopBuyRequest,
  ShopBuyResponseDto,
} from './dto/ShopDto';

export async function fetchShopListings(shopKey: string): Promise<ShopListingDto[]> {
  const res = await http.get<ShopListingDto[]>(
    `/api/world/shops/${encodeURIComponent(shopKey)}`,
  );
  return res.data;
}

export async function buyShopItem(
  shopKey: string,
  req: ShopBuyRequest,
): Promise<ShopBuyResponseDto> {
  const res = await http.post<ShopBuyResponseDto>(
    `/api/world/shops/${encodeURIComponent(shopKey)}/buy`,
    req,
  );
  return res.data;
}
