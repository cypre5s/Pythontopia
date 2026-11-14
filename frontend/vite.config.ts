import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";
export default defineConfig({
  plugins: [vue(), {
    name: 'python-runtime-cors',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url?.startsWith('/python-runtime/')) res.setHeader('Access-Control-Allow-Origin', '*');
        next();
      });
    },
  }],
  server: {
    port: 5178,
    strictPort: true,
    // Trace snapshots are generated HTML, not application pages or HMR inputs.
    watch: {
      ignored: ["**/.tmp*/**", "**/test-results/**", "**/playwright-report/**"],
    },
    proxy: {
      "/api": process.env.API_PROXY ?? "http://127.0.0.1:18081",
      "^/teacher(?:/|$)": { target: process.env.ADMIN_PROXY ?? "http://127.0.0.1:5179", ws: true },
    },
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
