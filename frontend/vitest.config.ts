import { fileURLToPath, URL } from 'node:url';
import vue from '@vitejs/plugin-vue';
import { configDefaults, defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/_helpers/phaserMock.ts'],
    // e2e/ 是 Playwright 用例（npx playwright test），vitest 收集到会报
    // "Playwright Test did not expect test() to be called here"，
    // 每次 npm run test 白浩 15 个失败文件、掩蓋真实回归。
    exclude: [...configDefaults.exclude, 'e2e/**'],
    coverage: {
      enabled: true,
      provider: 'v8',
      reporter: ['text', 'html'],
    },
  },
  resolve: {
    alias: [
      // 模板里的 <img src="/assets/..."> 会被 @vitejs/plugin-vue 的 transformAssetUrls
      // 变成模块导入；public/ 下的绝对路径在 vitest 里解析不到，报
      // "The argument 'filename' must be a file URL object..." 导致整份 spec 无法加载。
      // 把 /assets/* 指回 public/assets/*，让这些 spec 能跑。
      {
        find: /^\/assets\//,
        replacement: fileURLToPath(new URL('./public/assets/', import.meta.url)),
      },
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      { find: '@api', replacement: fileURLToPath(new URL('./src/api', import.meta.url)) },
      { find: '@game', replacement: fileURLToPath(new URL('./src/game', import.meta.url)) },
      { find: '@stores', replacement: fileURLToPath(new URL('./src/stores', import.meta.url)) },
    ],
  },
});
