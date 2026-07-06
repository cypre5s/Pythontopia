// 后端 API 基址。
//
// 默认指向公网 Pythontopia 后端（阿里云 ECS，nginx 反代 :80 → 内部 :18080）。
// 实测 2026-06-13：http://47.120.29.62/api/* 在线（GET /api/course/pack 返回 packId 43；
// :18080 未对外开放，对公网只暴露 nginx :80，故这里用 80 端口而非 18080）。
// 微信开发者工具需在「本地设置 → 不校验合法域名、web-view（业务域名）、TLS 版本以及
// HTTPS 证书」勾选后才能访问明文 http 公网地址。
// 本机联调时改回 http://localhost:18080（本地后端直连端口）即可。
//
// 线上发布：改为你的后端 HTTPS 备案域名（例如 https://api.example.com），并在
// 微信公众平台「开发管理 → 开发设置 → 服务器域名 → request 合法域名」加入该域名。
export const API_BASE = 'http://47.120.29.62';

// 业务接口统一前缀（request 层会自动拼接）。
export const API_PREFIX = '/api';

// 本地存储 key（JWT 有效期 12h；过期或 401 由 request 层统一清理并重新登录）。
export const STORAGE_TOKEN = 'pyt_token';
export const STORAGE_USER = 'pyt_user';

// 路由常量。
export const LOGIN_ROUTE = '/pages/login/login';
export const HOME_ROUTE = '/pages/index/index';
export const WORKSHOP_ROUTE = '/pages/workshop/workshop';
export const MINE_ROUTE = '/pages/mine/mine';
export const MINE_SESSION_ROUTE = '/pages/mine/session';
// R-15 主线：冒险地图小节学习页 + 章末大测试页。
export const STAGE_ROUTE = '/pages/stage/stage';
export const TEST_ROUTE = '/pages/test/test';
