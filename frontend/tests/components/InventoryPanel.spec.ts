import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import InventoryPanel from '@/components/hud/InventoryPanel.vue';
import { useInventoryStore } from '@stores/inventory';
import type {
  InventoryItemDto,
  ItemCategory,
} from '@api/dto/InventoryItemDto';
import type { ItemCatalogEntryDto } from '@api/dto/ItemCatalogEntryDto';

function makeItem(
  itemKey: string,
  partial: Partial<InventoryItemDto> = {},
): InventoryItemDto {
  const category: ItemCategory = partial.category ?? 'seed';
  return {
    itemKey,
    displayName: partial.displayName ?? `${itemKey}-显示名`,
    category,
    qty: partial.qty ?? 1,
    basePrice: partial.basePrice ?? 10,
  };
}

function makeCatalogEntry(
  itemKey: string,
  description: string,
  partial: Partial<ItemCatalogEntryDto> = {},
): ItemCatalogEntryDto {
  return {
    itemKey,
    displayName: partial.displayName ?? `${itemKey}-显示名`,
    category: partial.category ?? 'seed',
    basePrice: partial.basePrice ?? 10,
    description,
  };
}

describe('InventoryPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows placeholder text when inventory is empty', () => {
    const wrapper = mount(InventoryPanel);
    const empty = wrapper.find('[data-testid="inventory-empty"]');
    expect(empty.exists()).toBe(true);
    expect(empty.text()).toContain('背包空空如也');
    expect(wrapper.find('[data-testid="inventory-grid"]').exists()).toBe(false);
  });

  it('renders one slot with itemKey label and qty when single item is present', () => {
    const store = useInventoryStore();
    store.setBootstrapItems([makeItem('seed_potato', { qty: 5 })]);

    const wrapper = mount(InventoryPanel);
    const slot = wrapper.find('[data-testid="inventory-slot-seed_potato"]');
    expect(slot.exists()).toBe(true);
    const qty = wrapper.find('[data-testid="inventory-qty-seed_potato"]');
    expect(qty.text()).toBe('×5');
    expect(wrapper.find('[data-testid="inventory-empty"]').exists()).toBe(false);
  });

  it('renders multiple slots for multiple items', () => {
    const store = useInventoryStore();
    store.setBootstrapItems([
      makeItem('seed_potato', { qty: 3, category: 'seed' }),
      makeItem('watering_can', { qty: 1, category: 'tool' }),
      makeItem('bread', { qty: 2, category: 'consumable' }),
      makeItem('flower', { qty: 7, category: 'gift' }),
      makeItem('book_loop', { qty: 1, category: 'book' }),
    ]);

    const wrapper = mount(InventoryPanel);
    const slots = wrapper.findAll('[data-testid^="inventory-slot-"]');
    expect(slots).toHaveLength(5);
    expect(
      wrapper.find('[data-testid="inventory-qty-watering_can"]').text(),
    ).toBe('×1');
    expect(wrapper.find('[data-testid="inventory-qty-flower"]').text()).toBe(
      '×7',
    );
  });

  it('shows tooltip with displayName and description on hover', async () => {
    const store = useInventoryStore();
    store.setBootstrapItems([
      makeItem('seed_potato', { displayName: '土豆种子' }),
    ]);
    store.setCatalog([
      makeCatalogEntry('seed_potato', '在春季种植，约 6 天成熟。'),
    ]);

    const wrapper = mount(InventoryPanel);
    expect(
      wrapper.find('[data-testid="inventory-tooltip-seed_potato"]').exists(),
    ).toBe(false);

    await wrapper
      .find('[data-testid="inventory-use-seed_potato"]')
      .trigger('mouseenter');

    const tooltip = wrapper.find(
      '[data-testid="inventory-tooltip-seed_potato"]',
    );
    expect(tooltip.exists()).toBe(true);
    expect(tooltip.text()).toContain('土豆种子');
    expect(tooltip.text()).toContain('在春季种植');

    await wrapper
      .find('[data-testid="inventory-use-seed_potato"]')
      .trigger('mouseleave');
    expect(
      wrapper.find('[data-testid="inventory-tooltip-seed_potato"]').exists(),
    ).toBe(false);
  });

  it('opens context menu on slot click, then emits "use" when Use is clicked', async () => {
    const store = useInventoryStore();
    store.setBootstrapItems([makeItem('watering_can', { qty: 1, category: 'tool' })]);

    const wrapper = mount(InventoryPanel);
    await wrapper
      .find('[data-testid="inventory-use-watering_can"]')
      .trigger('click');

    expect(wrapper.find('[data-testid="inventory-context"]').exists()).toBe(true);

    await wrapper
      .find('[data-testid="inventory-action-use"]')
      .trigger('click');

    const useEvents = wrapper.emitted('use');
    expect(useEvents).toBeDefined();
    expect(useEvents).toHaveLength(1);
    expect(useEvents?.[0]).toEqual([{ itemId: 'watering_can' }]);
  });

  it('shows equip option for tool items and gift option for gift items', async () => {
    const store = useInventoryStore();
    store.setBootstrapItems([
      makeItem('watering_can', { qty: 1, category: 'tool' }),
      makeItem('flower', { qty: 1, category: 'gift' }),
    ]);

    const wrapper = mount(InventoryPanel);
    await wrapper.find('[data-testid="inventory-use-watering_can"]').trigger('click');
    expect(wrapper.find('[data-testid="inventory-action-equip"]').exists()).toBe(true);

    await wrapper.find('[data-testid="inventory-use-flower"]').trigger('click');
    expect(wrapper.find('[data-testid="inventory-action-gift"]').exists()).toBe(true);
  });

  it('reactively refreshes slots when the inventory store changes', async () => {
    const store = useInventoryStore();
    const wrapper = mount(InventoryPanel);
    expect(wrapper.find('[data-testid="inventory-empty"]').exists()).toBe(true);

    store.upsertItem(makeItem('seed_potato', { qty: 2 }));
    await flushPromises();
    expect(
      wrapper.find('[data-testid="inventory-slot-seed_potato"]').exists(),
    ).toBe(true);
    expect(
      wrapper.find('[data-testid="inventory-qty-seed_potato"]').text(),
    ).toBe('×2');

    store.upsertItem(makeItem('seed_potato', { qty: 9 }));
    await flushPromises();
    expect(
      wrapper.find('[data-testid="inventory-qty-seed_potato"]').text(),
    ).toBe('×9');

    store.upsertItem(makeItem('seed_potato', { qty: 0 }));
    await flushPromises();
    expect(
      wrapper.find('[data-testid="inventory-slot-seed_potato"]').exists(),
    ).toBe(false);
    expect(wrapper.find('[data-testid="inventory-empty"]').exists()).toBe(true);
  });
});
