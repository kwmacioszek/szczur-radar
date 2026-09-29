import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // Same-origin locally too: `npm run dev` forwards /api/* to the API on
    // :8000 (path and Host preserved), like Caddy does in production.
    proxy: {
      '/api': 'http://localhost:8000',
    },
  },
})
