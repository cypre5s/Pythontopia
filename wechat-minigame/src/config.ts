// 小镇渲染端的可调配置（无运行时副作用，纯常量）。
// 这里集中放「后端联动」与「跳转配套学习小程序」两类配置，便于开发者按需替换。

/** 后端联动（可选、非阻塞）。详见 docs/clients/api-contract.md。 */
export const BACKEND = {
  /** Base URL：微信开发者工具本地调试用 localhost（需勾选「不校验合法域名」）。 */
  baseUrl: 'http://localhost:18080',
  /** 聚合启动端点；后端为 POST（见契约 §4.1），返回 world/player/quests 等。 */
  bootstrapPath: '/api/game/bootstrap',
  /** 登录态 token 在本地存储里的 key（与配套学习小程序共享约定）。 */
  tokenKey: 'pyt_token',
  /** 单次请求超时（毫秒）；超时即安静回退，不阻塞渲染。 */
  timeoutMs: 4000,
} as const;

/**
 * 跳转「配套学习小程序」配置（feature: deep-link）。
 *
 * 本工程只渲染像素小镇，画像 / 课件 / 练习等学习面板都在**另一个独立小程序**里。
 * 玩家在学习类建筑前确认后，可用 `wx.navigateToMiniProgram` 跳到那个小程序。
 *
 * 开发者必须把 `appId` 替换成配套学习小程序的真实 AppID 才会启用跳转；
 * 留空（默认）则**不显示跳转按钮**，仅在对话里提示「学习内容在配套小程序中」。
 */
export interface CompanionConfig {
  /** 配套学习小程序 AppID；留空禁用跳转。 */
  appId: string;
  /** 目标小程序入口页路径；留空则打开其首页。可带 query，如 '/pages/town/index'。 */
  path: string;
  /** 目标小程序版本；仅当本小游戏为开发版/体验版时生效。 */
  envVersion: 'develop' | 'trial' | 'release';
}

export const COMPANION: CompanionConfig = {
  appId: '',
  path: '',
  envVersion: 'release',
};

/** 配套学习小程序是否已配置（决定是否展示跳转入口）。 */
export function companionEnabled(): boolean {
  return COMPANION.appId.length > 0;
}
