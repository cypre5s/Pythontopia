import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useInventoryStore } from '@stores/inventory';
import type { InventoryItemDto } from '@/api/dto/InventoryItemDto';

const mocks = vi.hoisted(() => ({
  fetchInventory: vi.fn(),
  useInventoryItem: vi.fn(),
}));

vi.mock('@/api/worldInventory', () => mocks);

function makeItem(
  itemKey: string,
  qty: number,
  category: InventoryItemDto['category'] = 'seed',
): InventoryItemDto {
  return {
    itemKey,
    displayName: itemKey,
    category,
    qty,
    basePrice: 10,
  };
}

describe('inventory store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchInventory.mockReset();
    mocks.useInventoryItem.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('load() GET /api/world/inventory and fills items', async () => {
    mocks.fetchInventory.mockResolvedValue([
      makeItem('seed_print', 5),
      makeItem('seed_input', 3),
    ]);
    const store = useInventoryStore();
    await store.load();
    expect(mocks.fetchInventory).toHaveBeenCalledTimes(1);
    expect(store.items).toHaveLength(2);
    expect(store.totalItems).toBe(8);
    expect(store.quantityOf('seed_print')).toBe(5);
    expect(store.quantityOf('seed_unknown')).toBe(0);
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
  });

  it('load() captures error and unblocks loading', async () => {
    mocks.fetchInventory.mockRejectedValue(new Error('boom'));
    const store = useInventoryStore();
    await store.load();
    expect(store.error).toBe('boom');
    expect(store.loading).toBe(false);
    expect(store.items).toEqual([]);
  });

  it('useItem() POST /use endpoint and upserts result', async () => {
    const store = useInventoryStore();
    store.setBootstrapItems([makeItem('seed_print', 5)]);
    mocks.useInventoryItem.mockResolvedValue(makeItem('seed_print', 4));
    await store.useItem('seed_print');
    expect(mocks.useInventoryItem).toHaveBeenCalledWith('seed_print');
    expect(store.quantityOf('seed_print')).toBe(4);
  });

  it('upsertItem removes entry when qty hits zero', () => {
    const store = useInventoryStore();
    store.setBootstrapItems([makeItem('seed_print', 1)]);
    store.upsertItem(makeItem('seed_print', 0));
    expect(store.items).toHaveLength(0);
  });

  it('upsertItem appends new key when missing', () => {
    const store = useInventoryStore();
    store.upsertItem(makeItem('tool_hoe', 1, 'tool'));
    expect(store.items).toHaveLength(1);
    expect(store.byCategory('seed')).toHaveLength(0);
    expect(store.byCategory('tool')).toHaveLength(1);
  });

  it('setBootstrapItems replaces the full list and is called by world.bootstrap', () => {
    const store = useInventoryStore();
    store.setBootstrapItems([makeItem('seed_print', 5)]);
    store.setBootstrapItems([makeItem('tool_hoe', 1, 'tool')]);
    expect(store.items).toEqual([makeItem('tool_hoe', 1, 'tool')]);
    expect(store.byCategory('seed')).toHaveLength(0);
    expect(store.byCategory('tool')).toHaveLength(1);
  });
});
