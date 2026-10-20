import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          // Loaded only by the archive scene, not by the editor or other routes.
          const path = id.replace(/\\/g, '/')
          if (path.includes('/three/build/three.core.js')) return 'three-core'
          if (path.includes('/three/build/three.module.js')) return 'three-renderer'
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
