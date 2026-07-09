import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 8127,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8137',
        changeOrigin: true
      }
    }
  }
})
