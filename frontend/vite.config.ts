import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    // R-12 遗留：vite 默认 port 被占用时回退 5174，但后端 CORS allowlist 只有 5173 →
    // 预检 OPTIONS /api/auth/login 直接 403，玩家以为登录炸了。strictPort=true 让
    // 端口冲突直接 fail-fast，逼开发者释放 5173 或同步改后端 allowlist。
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:18080',
      '/healthz': 'http://localhost:18080',
    },
  },
  build: {
    // R-13 Phase 5 迭代 1：把 phaser / tone / howler 单独切 chunk，
    // 避免主 bundle 1.88MB（其中 ~700KB 是 Phaser）影响首屏 LCP。
    chunkSizeWarningLimit: 1600,
    rollupOptions: {
      output: {
        manualChunks: {
          phaser: ['phaser'],
          'audio-tone': ['tone'],
          'audio-howler': ['howler'],
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-axios': ['axios'],
        },
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@api': fileURLToPath(new URL('./src/api', import.meta.url)),
      '@game': fileURLToPath(new URL('./src/game', import.meta.url)),
      '@stores': fileURLToPath(new URL('./src/stores', import.meta.url)),
    },
  },
});
