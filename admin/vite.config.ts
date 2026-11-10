import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig({
  base: "/teacher/",
  publicDir: fileURLToPath(new URL("../frontend/public", import.meta.url)),
  build: { copyPublicDir: false },
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  server: {
    host: "127.0.0.1",
    strictPort: true,
    fs: { allow: [fileURLToPath(new URL(".", import.meta.url)), fileURLToPath(new URL("../shared/world", import.meta.url))] },
    proxy: { "/api": process.env.API_PROXY ?? "http://127.0.0.1:18081" },
  },
  test: {
    include: ["tests/**/*.test.{ts,tsx}"],
    environment: "jsdom",
    setupFiles: ["./tests/setup.ts"],
    clearMocks: true,
  },
});
