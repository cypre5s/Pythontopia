import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import { setNetworkErrorHandler } from './api/http';
import { shouldShowNetworkToast } from './api/networkNotice';
import { router } from './app/router';
import { useSessionStore } from './stores/session';
import { useToastStore } from './stores/toast';
import './styles/tokens.css';
import './styles/pixel-ui.css';
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

// W4-FINAL-POLISH：所有请求拿到 NETWORK_ERROR / TIMEOUT 时给玩家一个温和提示，
// 而不是页面静默看似一切正常。toast 本身已自带 3s 自动消失 + 5s 冷却（见 http.ts），
// 避免后端短暂抖动时反复刷屏。
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
