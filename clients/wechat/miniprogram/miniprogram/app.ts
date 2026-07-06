import { API_BASE } from './config';
import { hydrateSession } from './store/session';

App<IAppOption>({
  globalData: {
    apiBase: API_BASE,
  },
  onLaunch() {
    // 启动时从本地存储恢复登录态（token + user），供各页面与请求层使用。
    hydrateSession();
  },
});
