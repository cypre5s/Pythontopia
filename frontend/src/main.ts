// 运行时 polyfill 必须最先执行（早于 pdfjs-dist 等依赖 Promise.withResolvers 的模块）。
import './polyfills';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import { setNetworkErrorHandler } from './api/http';
import { shouldShowNetworkToast } from './api/networkNotice';
import { router } from './app/router';
import { initWebVitals } from './services/webVitals';
import { useSessionStore } from './stores/session';
import { useToastStore } from './stores/toast';
import './styles/tokens.css';
import './styles/pixel-ui.css';
import './styles/split-layout.css';
import './styles.css';

const pinia = createPinia();
const app = createApp(App);

app.use(pinia);
app.use(router);
const session = useSessionStore();
session.hydrateFromStorage();
session.configureHttp(() => {
  void router.push({ name: 'login' });
});

// 请求遇到 NETWORK_ERROR / TIMEOUT 时给玩家温和提示，而非静默失败；
// toast 自带 3s 自动消失 + 5s 冷却（见 http.ts），避免后端抖动时反复刷屏。
const toast = useToastStore();
setNetworkErrorHandler((code) => {
  const env = (import.meta as ImportMeta & { readonly env?: Parameters<typeof shouldShowNetworkToast>[1] }).env ?? {};
  if (!shouldShowNetworkToast(code, env)) {
    return;
  }
  if (code === 'TIMEOUT') {
    toast.push({ kind: 'error', text: '请求超时，请检查网络后重试' });
  } else {
    toast.push({ kind: 'error', text: '无法连接服务器，请检查网络或刷新页面' });
  }
});

app.mount('#app');

// 接入真实 Web Vitals 采集（FCP/LCP/CLS/TTFB），转发给 perfMonitor 供 PerfHud 展示。
initWebVitals();
