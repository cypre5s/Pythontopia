import { http } from './http';
import type { InventoryItemDto } from './dto/InventoryItemDto';

export async function fetchInventory(): Promise<InventoryItemDto[]> {
  const response = await http.get<InventoryItemDto[]>('/api/world/inventory');
  return response.data;
}

export async function useInventoryItem(itemKey: string): Promise<InventoryItemDto> {
  const response = await http.post<InventoryItemDto>(
    `/api/world/inventory/${encodeURIComponent(itemKey)}/use`,
  );
  return response.data;
}
