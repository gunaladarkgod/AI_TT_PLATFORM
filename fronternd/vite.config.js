import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

/**
 * 文档源文件保留在项目根目录 docs/v1.0。通过虚拟模块内嵌内容，避免开发服务器把中文文件名
 * 转成乱码 URL 后出现 ENOENT，同时不复制或写死第二份文档。
 */
function projectDocumentationPlugin() {
  const documents = {
    'virtual:project-usage-guide': fileURLToPath(new URL('../docs/v1.0/使用说明文档v1.0.md', import.meta.url)),
    'virtual:project-technical-guide': fileURLToPath(new URL('../docs/v1.0/技术说明文档.md', import.meta.url)),
    'virtual:project-github-workflow': fileURLToPath(new URL('../docs/v1.0/GitHub协作流程.md', import.meta.url)),
  }
  const documentPaths = new Set(Object.values(documents).map((path) => path.toLowerCase()))

  return {
    name: 'project-documentation',
    resolveId(id) {
      return Object.prototype.hasOwnProperty.call(documents, id) ? `\0${id}` : null
    },
    load(id) {
      const documentPath = documents[id.slice(1)]
      return documentPath ? `export default ${JSON.stringify(readFileSync(documentPath, 'utf8'))}` : null
    },
    // docs 位于前端根目录之外，显式监听后才能在开发模式修改 Markdown 时刷新帮助页面。
    configureServer(server) {
      server.watcher.add([...documentPaths])
      server.watcher.on('change', (file) => {
        if (!documentPaths.has(String(file).toLowerCase())) return
        Object.keys(documents).forEach((id) => {
          const module = server.moduleGraph.getModuleById(`\0${id}`)
          if (module) server.moduleGraph.invalidateModule(module)
        })
        server.ws.send({ type: 'full-reload' })
      })
    },
  }
}

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
  plugins: [vue(), projectDocumentationPlugin()],
})
