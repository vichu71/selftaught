import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  // En desarrollo el navegador habla con un único origen: /api se reenvía al backend
  server: { proxy: { '/api': 'http://backend:8080' } },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/**/*.spec.ts'],
    coverage: { provider: 'v8', include: ['src/**/*.{ts,vue}'] },
  },
})
