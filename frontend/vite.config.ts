import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5178,
    strictPort: true,
    proxy: { "/api": process.env.API_PROXY ?? "http://127.0.0.1:18081" },
  },
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          phaser: ["phaser"],
          "vendor-vue": ["vue", "pinia", "vue-router"],
        },
      },
    },
  },
});
