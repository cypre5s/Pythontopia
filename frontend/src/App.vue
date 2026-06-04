<script setup lang="ts">
// R-13：把 RouterView 包在 Suspense 内，叠加全局错误兜底，避免 chunk 加载失败时
// 玩家看到纯白屏。同步加 router.onError 监听，对 chunk 失败 emit 友好 toast 并
// 提供刷新建议。
import { onBeforeUnmount, onErrorCaptured, onMounted, ref } from 'vue';
import { RouterView, useRouter } from 'vue-router';
import AppToast from '@/components/system/AppToast.vue';
import DialogTree from '@/components/game/DialogTree.vue';
import PerfHud from '@/components/hud/PerfHud.vue';
import { useToastStore } from '@stores/toast';
import { useWorldStore } from '@stores/world';

const router = useRouter();
const toast = useToastStore();
const world = useWorldStore();
const fatalError = ref<string | null>(null);
const BUG_MINE_ENTRY_TILE = { x: 15, y: 2 };

router.onError((err) => {
  const message = err instanceof Error ? err.message : String(err);
  // 动态 chunk 加载失败常见提示，给一个温和的中文 toast
  if (/Loading chunk|Failed to fetch dynamically imported|Importing a module script failed/.test(message)) {
    toast.push({ kind: 'error', text: '页面资源加载失败，请刷新重试' });
  } else {
    toast.push({ kind: 'error', text: `路由错误：${message}` });
  }
});

onErrorCaptured((err) => {
  const message = err instanceof Error ? err.message : String(err);
  toast.push({ kind: 'error', text: `渲染异常：${message}` });
  // 不抛出，避免 Vue 卸载整棵树；但记录到 fatalError 给开发模式显示
  if (typeof window !== 'undefined' && (window as unknown as { __DEV__?: boolean }).__DEV__) {
    fatalError.value = message;
  }
  return false;
});

function onDialogNavigate(e: Event): void {
  const detail = (e as CustomEvent<{ route?: unknown }>).detail;
  const route = detail?.route;
  if (typeof route !== 'string' || route.length === 0) {
    return;
  }
  if (route.startsWith('/bug-mine')) {
    world.setPendingTravel('mine_south', BUG_MINE_ENTRY_TILE);
    window.dispatchEvent(new CustomEvent('pythontopia:world-travel'));
    void router.push({ path: '/world', query: { goto: 'mine_south' } });
    return;
  }
  void router.push(route);
}

onMounted(() => {
  window.addEventListener('pythontopia:dialog-navigate', onDialogNavigate);
});

onBeforeUnmount(() => {
  window.removeEventListener('pythontopia:dialog-navigate', onDialogNavigate);
});
</script>

<template>
  <Suspense>
    <template #default>
      <!-- R-13 Phase 5 迭代 6：路由切换 fade transition。
           世界场景（/world）不参与 transition，避免每次切场景 Phaser canvas 重建。 -->
      <!-- WorldPage 用 KeepAlive 缓存：离开 /world 不销毁 Phaser，避免每次返回重跑 BootScene -->
      <RouterView v-slot="{ Component, route }">
        <KeepAlive include="WorldPage">
          <Transition
            :name="route.name === 'world' ? 'route-none' : 'route-fade'"
            mode="out-in"
          >
            <component
              :is="Component"
              :key="route.name === 'world' ? 'world' : route.fullPath"
            />
          </Transition>
        </KeepAlive>
      </RouterView>
    </template>
    <template #fallback>
      <div
        class="app-suspense"
        data-testid="app-suspense"
      >
        <div class="app-suspense__card">
          <div
            class="app-suspense__spinner"
            aria-hidden="true"
          />
          <p>页面加载中…</p>
        </div>
      </div>
    </template>
  </Suspense>
  <AppToast />
  <DialogTree />
  <!-- 全局性能诊断框（F3 切换，默认关闭）：单例挂在根组件，覆盖所有路由（含 Phaser 游戏页）。 -->
  <PerfHud />
</template>

<style scoped>
.app-suspense {
  display: flex;
  min-height: 100dvh;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, var(--pyt-color-bg-primary) 0%, var(--pyt-color-bg-sunken) 100%);
}
.app-suspense__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  padding: 24px;
  background: var(--pyt-color-bg-elevated);
  border: 3px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: 14px;
  letter-spacing: 0.5px;
  box-shadow: 4px 4px 0 rgba(77, 54, 19, 0.3);
}
.app-suspense__spinner {
  width: 28px;
  height: 28px;
  border: 4px solid var(--pyt-color-gold);
  border-top-color: transparent;
  border-radius: 50%;
  animation: app-suspense-spin 0.9s linear infinite;
}
@keyframes app-suspense-spin {
  to { transform: rotate(360deg); }
}
@media (prefers-reduced-motion: reduce) {
  .app-suspense__spinner {
    animation-duration: 2.4s;
  }
}
</style>

<style>
/* R-13：路由 fade transition，非 scoped 让全局可用 */
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}
.route-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.route-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
/* world 场景禁用 transition，避免 Phaser canvas 切换时 destroy/recreate */
.route-none-enter-active,
.route-none-leave-active {
  transition: none;
}
@media (prefers-reduced-motion: reduce) {
  .route-fade-enter-active,
  .route-fade-leave-active {
    transition: opacity 100ms linear;
  }
  .route-fade-enter-from,
  .route-fade-leave-to {
    transform: none;
  }
}
</style>
