<script setup lang="ts">
// R-13：游戏内商铺页面。
//
// 设计要点：
//   1. props.shopKey 由路由静态注入（route.props 对象），不依赖 :param —— 因为
//      `/shop-east` 等是固定语义路由，shopKey 在路由表里写死，无需 URL 参数。
//   2. mount 时拉一次 fetchShopListings，loading/error 完整三态；空列表显示
//      「暂无商品配置」，绝不允许白屏。
//   3. 全部 data-testid 与 tests/pages/ShopPage.spec.ts 契约一致：
//      shop-page / shop-title / shop-empty / shop-item-${itemKey}
//   4. 像素风视觉与 HudShell / SettingsModal / OperationHints 一致（金边深底）。
//   5. 「返回世界」按钮跳回 /world，保持玩家不被困在商铺页。

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import { fetchShopListings } from '@/api/shop';
import type { ShopListingDto } from '@/api/dto/ShopDto';
import { useToastStore } from '@stores/toast';
import { playUiClick, playCoinJingle } from '@/composables/useStardewFx';

interface Props {
  shopKey: string;
  displayName: string;
}
const props = defineProps<Props>();

const router = useRouter();
const toast = useToastStore();

const listings = ref<ShopListingDto[]>([]);
const loading = ref(true);
const errorMessage = ref<string | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = null;
  try {
    listings.value = await fetchShopListings(props.shopKey);
    if (listings.value.length > 0) {
      playCoinJingle();
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    errorMessage.value = msg;
    toast.show(`无法加载商品列表：${msg}`, 'error');
  } finally {
    loading.value = false;
  }
}

function backToWorld(): void {
  playUiClick();
  void router.push({ name: 'world' });
}

onMounted(() => {
  void load();
});
</script>

<template>
  <AppLayout>
    <section
      class="shop-page"
      data-testid="shop-page"
    >
      <header class="shop-page__head">
        <h1
          class="shop-page__title"
          data-testid="shop-title"
        >
          {{ displayName }}
        </h1>
        <button
          type="button"
          class="shop-page__back"
          @click="backToWorld"
        >
          ← 返回世界
        </button>
      </header>

      <div
        v-if="loading"
        class="shop-page__loading"
        data-testid="shop-loading"
      >
        正在加载商品…
      </div>
      <div
        v-else-if="errorMessage"
        class="shop-page__error"
        data-testid="shop-error"
      >
        加载失败：{{ errorMessage }}
        <button
          type="button"
          class="shop-page__retry"
          @click="load"
        >
          重试
        </button>
      </div>
      <div
        v-else-if="listings.length === 0"
        class="shop-page__empty"
        data-testid="shop-empty"
      >
        暂无商品配置，请联系商人或稍后再来。
      </div>
      <ul
        v-else
        class="shop-page__list"
      >
        <li
          v-for="item in listings"
          :key="item.itemKey"
          class="shop-card"
          :data-testid="`shop-item-${item.itemKey}`"
        >
          <div class="shop-card__head">
            <h2 class="shop-card__name">
              {{ item.itemKey }}
            </h2>
            <span
              v-if="item.stock !== null"
              class="shop-card__stock"
            >
              库存 {{ item.stock }}
            </span>
            <span
              v-else
              class="shop-card__stock shop-card__stock--unlimited"
            >
              库存 无限
            </span>
          </div>
          <dl class="shop-card__meta">
            <div class="shop-card__row">
              <dt>金币</dt>
              <dd>{{ item.priceCoins }}</dd>
            </div>
            <div
              v-if="item.priceGems > 0"
              class="shop-card__row"
            >
              <dt>宝石</dt>
              <dd>{{ item.priceGems }}</dd>
            </div>
            <div
              v-if="item.availableDays && item.availableDays.length > 0"
              class="shop-card__row"
            >
              <dt>限购日</dt>
              <dd>{{ item.availableDays.join('/') }}</dd>
            </div>
          </dl>
        </li>
      </ul>
    </section>
  </AppLayout>
</template>

<style scoped>
.shop-page {
  max-width: min(960px, calc(100vw - 32px));
  margin: 16px auto;
  padding: 18px 20px;
  background: rgba(28, 24, 16, 0.94);
  border: 3px solid #d4af37;
  border-radius: 10px;
  color: #fff7e6;
  font-family: 'PythontopiaPixel', 'PingFang SC', 'Noto Sans CJK SC', monospace;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.shop-page__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 2px solid rgba(212, 175, 55, 0.4);
  padding-bottom: 10px;
  margin-bottom: 12px;
}

.shop-page__title {
  margin: 0;
  font-size: 22px;
  color: #ffd166;
  text-shadow: 1px 1px 0 #2a1a05;
  letter-spacing: 1px;
}

.shop-page__back {
  background: rgba(212, 175, 55, 0.18);
  border: 2px solid #d4af37;
  color: #fff7e6;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  transition: background 120ms;
}

.shop-page__back:hover {
  background: rgba(212, 175, 55, 0.32);
}

.shop-page__loading,
.shop-page__error,
.shop-page__empty {
  padding: 32px 12px;
  text-align: center;
  color: #fff7e6;
  font-size: 14px;
}

.shop-page__error {
  color: #ffb4ab;
}

.shop-page__retry {
  display: inline-block;
  margin-left: 12px;
  padding: 4px 10px;
  background: #d4af37;
  border: 2px solid #f1c453;
  color: #2a1a05;
  border-radius: 4px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
}

.shop-page__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.shop-card {
  background: rgba(77, 54, 19, 0.45);
  border: 2px solid #d4af37;
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.shop-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.shop-card__name {
  margin: 0;
  font-size: 15px;
  color: #ffd166;
}

.shop-card__stock {
  font-size: 11px;
  color: #fff7e6;
  background: rgba(0, 0, 0, 0.4);
  padding: 2px 6px;
  border-radius: 4px;
}

.shop-card__stock--unlimited {
  color: #b8d99a;
}

.shop-card__meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.shop-card__row {
  display: flex;
  justify-content: space-between;
  color: #fff7e6;
}

.shop-card__row dt {
  color: #d4af37;
  margin: 0;
}

.shop-card__row dd {
  margin: 0;
  font-weight: 700;
}

@media (max-width: 640px) {
  .shop-page {
    margin: 8px;
    padding: 12px;
  }
  .shop-page__title {
    font-size: 18px;
  }
  .shop-page__list {
    grid-template-columns: 1fr;
  }
}
</style>
