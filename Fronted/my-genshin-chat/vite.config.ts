import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/ai': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
      '/file': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
      '/user': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
      '/audio': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
      // WebSocket proxy for real-time audio
      '/ws-audio': {
        target: 'ws://localhost:8000',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
