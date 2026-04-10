import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  base: '/dist',
  server: {
    host: "0.0.0.0",
    proxy: {
      // 与 src/api/axios.js 中 baseURL `/develop` 一致
      '/develop': {
        // target: 'http://172.17.215.193',
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/develop/, '')
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
   // 解决 Vite 使用 scss 出现警告 
   css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        silenceDeprecations: ['legacy-js-api'] // 禁用关于遗留 JS API 的警告
      }
    }
  },
  plugins: [vue()],
})
