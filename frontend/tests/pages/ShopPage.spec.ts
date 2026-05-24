import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/shop', () => ({
  fetchShopListings: vi.fn(),
  buyShopItem: vi.fn(),
}));

import ShopPage from '@/pages/ShopPage.vue';
import { fetchShopListings } from '@/api/shop';
import type { ShopListingDto } from '@/api/dto/ShopDto';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

function createTestRouter(_path = '/shop-east'): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/shop-east',
        name: 'shop-east',
        component: ShopPage,
        props: { shopKey: 'shop_east', displayName: '工具铺' },
      },
      { path: '/world', name: 'world', component: { template: '<div />' } },
    ],
  });
}

describe('ShopPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(fetchShopListings).mockReset();
  });

  it('renders an empty configured shop without a white page', async () => {
    vi.mocked(fetchShopListings).mockResolvedValue([]);
    const router = createTestRouter();
    await router.push('/shop-east');
    await router.isReady();

    const wrapper = mount(ShopPage, {
      props: { shopKey: 'shop_east', displayName: '工具铺' },
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    expect(fetchShopListings).toHaveBeenCalledWith('shop_east');
    expect(wrapper.get('[data-testid="shop-title"]').text()).toContain('工具铺');
    expect(wrapper.get('[data-testid="shop-empty"]').text()).toContain('暂无商品配置');
    expect(wrapper.find('[data-testid="shop-page"]').exists()).toBe(true);
  });

  it('renders backend listing keys and prices from the real shop API shape', async () => {
    const listings: ShopListingDto[] = [
      {
        shopKey: 'merchant_port',
        itemKey: 'python_book',
        priceCoins: 100,
        priceGems: 0,
        stock: 5,
        availableDays: null,
      },
    ];
    vi.mocked(fetchShopListings).mockResolvedValue(listings);
    const router = createTestRouter();
    await router.push('/shop-east');
    await router.isReady();

    const wrapper = mount(ShopPage, {
      props: { shopKey: 'merchant_port', displayName: '商船' },
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="shop-item-python_book"]').text()).toContain('python_book');
    expect(wrapper.get('[data-testid="shop-item-python_book"]').text()).toContain('100');
    expect(wrapper.get('[data-testid="shop-item-python_book"]').text()).toContain('库存 5');
  });
});
