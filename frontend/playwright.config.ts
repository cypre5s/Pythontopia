import { defineConfig } from '@playwright/test';

// 引导手册截图 / E2E 专用配置。
// 关键约束：
//   - testDir 固定为 e2e/，绝不扫 tests/（tests/ 是 vitest 的目录，两套 runner 严格分离）；
//   - 视口冻结 1440x900（截图契约，见 docs/guide/screenshot-manifest.md）；
//   - 不内置 webServer：本地栈（vite :5173 + backend :18080）由外部启动，
//     测试只通过 BASE_URL 访问，栈没起来时测试快速失败而不是悄悄拉起半套环境。
const BASE_URL = process.env.BASE_URL ?? 'http://127.0.0.1:5173';

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.artifacts',
  fullyParallel: false,
  // 12 张截图共享同一个演示账号会话（worker 内缓存），单 worker 串行保证注册只发生一次、
  // 截图顺序与编号一致，也避免对并行开发中的后端造成压力。
  workers: 1,
  // 公网验收针对 2C4G ECS：重试 2 次吸收偶发页面加载超时抖动
  // （world ~29MB 像素资产 / training 面板冷载在持续压测下偶发超时）。
  retries: 2,
  forbidOnly: !!process.env.CI,
  // 首次进 /world 需要预加载像素素材，留足单测预算；其余页面远快于此。
  timeout: 120_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  use: {
    baseURL: BASE_URL,
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    colorScheme: 'light',
    // 降低 CSS/GSAP 动画抖动，让「元素可见」等待条件更稳定（不改变页面内容本身）。
    contextOptions: { reducedMotion: 'reduce' },
    trace: 'retain-on-failure',
    video: 'off',
    launchOptions: {
      // 无头 Chromium 的软件 WebGL（SwiftShader）需要显式放行，
      // 否则 Phaser 会静默退回 Canvas2D 渲染，像素小镇画面保真度下降。
      args: ['--enable-unsafe-swiftshader'],
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});
