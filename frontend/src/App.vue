<script setup lang="ts">
// RouterView 包在 Suspense 内并叠加全局错误兜底：chunk 加载失败时给友好 toast，避免纯白屏。
import { onBeforeUnmount, onErrorCaptured, onMounted, ref } from 'vue';
import { RouterView, useRouter } from 'vue-router';
import AppToast from '@/components/system/AppToast.vue';
import DialogTree from '@/components/game/DialogTree.vue';
import PerfHud from '@/components/hud/PerfHud.vue';
import { useToastStore } from '@stores/toast';

const router = useRouter();
const toast = useToastStore();
const fatalError = ref<string | null>(null);

// 这些路由自身用 watch/computed 响应式处理 query/param 变化（如 /library?pageId=、/lecture/:chapter），
// 因此按 route.name 取稳定 :key，避免仅 query/param 变化时整页组件 remount（点讲义/切章的"重载感"根因）。
const STABLE_NAME_ROUTES = new Set(['world', 'library', 'workshop', 'lecture']);

router.onError((err) => {
  const message = err instanceof Error ? err.message : String(err);
  // 匹配动态 chunk 加载失败的常见报错文案
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
  // R-14：Bug 矿洞功能为 /bug-mine 页面（mine_south 风景地图已删），不再切 Phaser 场景，直接进页面。
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
      <!-- 路由切换 fade transition；/world 不参与，避免切场景时 Phaser canvas 重建。 -->
      <!-- WorldPage 用 KeepAlive 缓存：离开 /world 不销毁 Phaser，避免每次返回重跑 BootScene。 -->
      <!-- WorkshopPage 同样缓存：离开百晓工坊（返回小镇 / 去图书馆 / 闯关塔等任意跳转）不销毁本页，
           下次回来对话流、面板布局（历史/书房/调用）与书房弹窗都停在离开时那一刻。 -->
      <RouterView v-slot="{ Component, route }">
        <KeepAlive :include="['WorldPage', 'WorkshopPage']">
          <Transition
            :name="route.name === 'world' ? 'route-none' : 'route-fade'"
            mode="out-in"
          >
            <component
              :is="Component"
              :key="STABLE_NAME_ROUTES.has(route.name as string) ? String(route.name) : route.fullPath"
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
/* 路由 fade transition；非 scoped 以便全局可用 */
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
