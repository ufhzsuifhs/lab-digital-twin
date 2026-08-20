import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  // 相对路径：拷到 Nginx 子目录、jar 静态资源、file 协议都能加载 JS，不依赖站点根路径
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    // three / G6 3D 体积大，避免误报打断构建
    chunkSizeWarningLimit: 4000,
    rollupOptions: {
      output: {
        // 明确打出静态分包，部署时 dist/assets 里能看到 three、antv 文件即打包成功
        manualChunks(id) {
          if (id.includes('node_modules/three')) return 'three'
          if (id.includes('node_modules/@antv')) return 'antv'
          if (id.includes('node_modules/echarts')) return 'echarts'
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) {
            return 'vue'
          }
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8220',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8220',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
