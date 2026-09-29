import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // Same-origin locally too: `npm run dev` forwards /api/* to the API on
    // :8000, like Caddy does in production. Use the object form: the string
    // shorthand sets changeOrigin: true, which rewrites Host to localhost:8000
    // and makes the API's Origin check reject every login with 403.
    proxy: {
      '/api': { target: 'http://localhost:8000', changeOrigin: false },
    },
  },
})
