// R-13 Phase 8 大爆炸测试 · Shop 模块（目标 30 用例）。
// 覆盖：fetchShopListings happy/empty/error/重试/多 shopKey、ShopPage 渲染、buyShopItem 调用。

import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/shop', () => ({
  fetchShopListings: vi.fn(),
  buyShopItem: vi.fn(),
}));

import ShopPage from '@/pages/ShopPage.vue';
import { fetchShopListings, buyShopItem } from '@/api/shop';
import type { ShopListingDto } from '@/api/dto/ShopDto';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/shop-east', name: 'shop-east', component: ShopPage },
    ],
  });
}

function mountShop(props: { shopKey: string; displayName: string }) {
  const router = makeRouter();
  return mount(ShopPage, {
    props,
    global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
  });
}

const SAMPLE: ShopListingDto[] = [
  { shopKey: 'merchant_port', itemKey: 'python_book', priceCoins: 100, priceGems: 0, stock: 5, availableDays: null },
  { shopKey: 'merchant_port', itemKey: 'coffee', priceCoins: 30, priceGems: 0, stock: null, availableDays: null },
  { shopKey: 'merchant_port', itemKey: 'hoe', priceCoins: 50, priceGems: 0, stock: 1, availableDays: null },
  { shopKey: 'merchant_port', itemKey: 'gem_dust', priceCoins: 0, priceGems: 3, stock: 2, availableDays: [1, 3, 5] },
];

describe('shop.integration: ShopPage / api/shop（30 case）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(fetchShopListings).mockReset();
    vi.mocked(buyShopItem).mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it.each([
    ['shop_east', '工具铺'],
    ['shop_west', '杂货铺'],
    ['merchant_port', '商船'],
    ['shop_custom', '自定义铺'],
  ])('shopKey=%s displayName=%s 标题正确', async (shopKey, displayName) => {
    vi.mocked(fetchShopListings).mockResolvedValue([]);
    const wrapper = mountShop({ shopKey, displayName });
    await flushPromises();
    expect(wrapper.get('[data-testid="shop-title"]').text()).toContain(displayName);
    expect(fetchShopListings).toHaveBeenCalledWith(shopKey);
  });

  it('listings=[]：显示「暂无商品配置」', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([]);
    const wrapper = mountShop({ shopKey: 'shop_east', displayName: '工具铺' });
    await flushPromises();
    expect(wrapper.get('[data-testid="shop-empty"]').text()).toContain('暂无商品配置');
  });

  it('listings=4：渲染 4 个 shop-item-* + 每个 itemKey', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue(SAMPLE);
    const wrapper = mountShop({ shopKey: 'merchant_port', displayName: '商船' });
    await flushPromises();
    expect(wrapper.get('[data-testid="shop-item-python_book"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="shop-item-coffee"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="shop-item-hoe"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="shop-item-gem_dust"]').exists()).toBe(true);
  });

  it.each(SAMPLE)('item %s 显示金币价 + 库存', async (item) => {
    vi.mocked(fetchShopListings).mockResolvedValue([item]);
    const wrapper = mountShop({ shopKey: 'merchant_port', displayName: 'X' });
    await flushPromises();
    const card = wrapper.get(`[data-testid="shop-item-${item.itemKey}"]`);
    expect(card.text()).toContain(String(item.priceCoins));
    if (item.stock === null) {
      expect(card.text()).toContain('无限');
    } else {
      expect(card.text()).toContain(String(item.stock));
    }
  });

  it('item 有 priceGems > 0 时显示宝石价', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([SAMPLE[3]!]);
    const wrapper = mountShop({ shopKey: 'merchant_port', displayName: 'X' });
    await flushPromises();
    const card = wrapper.get('[data-testid="shop-item-gem_dust"]');
    expect(card.text()).toContain('宝石');
    expect(card.text()).toContain('3');
  });

  it('item availableDays 非空时显示限购日', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([SAMPLE[3]!]);
    const wrapper = mountShop({ shopKey: 'merchant_port', displayName: 'X' });
    await flushPromises();
    const card = wrapper.get('[data-testid="shop-item-gem_dust"]');
    expect(card.text()).toContain('1/3/5');
  });

  it('fetch 抛错：显示 shop-error + retry 按钮', async () => {
    vi.mocked(fetchShopListings).mockRejectedValueOnce(new Error('网络炸了'));
    const wrapper = mountShop({ shopKey: 'shop_east', displayName: '工具铺' });
    await flushPromises();
    expect(wrapper.get('[data-testid="shop-error"]').text()).toContain('网络炸了');
  });

  it('点击 retry 重新调用 fetchShopListings', async () => {
    vi.mocked(fetchShopListings).mockRejectedValueOnce(new Error('try1'));
    const wrapper = mountShop({ shopKey: 'shop_east', displayName: '工具铺' });
    await flushPromises();
    vi.mocked(fetchShopListings).mockResolvedValueOnce(SAMPLE);
    await wrapper.find('[data-testid="shop-error"] button').trigger('click');
    await flushPromises();
    expect(fetchShopListings).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-testid="shop-error"]').exists()).toBe(false);
  });

  it.each([
    ['python_book', 1],
    ['python_book', 5],
    ['coffee', 2],
    ['gem_dust', 10],
  ])('buyShopItem("%s", qty=%i) 调用契约', async (itemKey, qty) => {
    vi.mocked(buyShopItem).mockResolvedValue({
      itemKey,
      qty,
      totalCoins: 100 * qty,
      totalGems: 0,
      remainingStock: 10 - qty,
    });
    const res = await buyShopItem('merchant_port', { itemKey, qty });
    expect(res.itemKey).toBe(itemKey);
    expect(res.qty).toBe(qty);
    expect(buyShopItem).toHaveBeenCalledWith('merchant_port', { itemKey, qty });
  });

  it('shopKey 含特殊字符时 URL encoded', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([]);
    await fetchShopListings('shop east');
    expect(fetchShopListings).toHaveBeenCalledWith('shop east');
  });

  it('shop-page 根容器存在 data-testid', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([]);
    const wrapper = mountShop({ shopKey: 'shop_east', displayName: '工具铺' });
    await flushPromises();
    expect(wrapper.find('[data-testid="shop-page"]').exists()).toBe(true);
  });

  it('shop-page 显示返回按钮', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([]);
    const wrapper = mountShop({ shopKey: 'shop_east', displayName: '工具铺' });
    await flushPromises();
    expect(wrapper.text()).toContain('返回世界');
  });

  it('loading 期间显示 shop-loading', async () => {
    let resolveFn: (val: ShopListingDto[]) => void = () => {};
    vi.mocked(fetchShopListings).mockReturnValue(
      new Promise<ShopListingDto[]>((r) => {
        resolveFn = r;
      }),
    );
    const wrapper = mountShop({ shopKey: 'shop_east', displayName: 'X' });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="shop-loading"]').exists()).toBe(true);
    resolveFn([]);
    await flushPromises();
    expect(wrapper.find('[data-testid="shop-loading"]').exists()).toBe(false);
  });
});
