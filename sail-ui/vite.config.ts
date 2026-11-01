import type { PluginOption } from 'vite'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import process from 'node:process'
import { fileURLToPath, URL } from 'node:url'
import { visualizer } from 'rollup-plugin-visualizer'
import AutoImport from 'unplugin-auto-import/vite'
import Component from 'unplugin-vue-components/vite'
import { loadEnv } from 'vite'
import Layouts from 'vite-plugin-vue-layouts'
import { defineConfig } from 'vitest/config'
import { VueRouterAutoImports } from 'vue-router/unplugin'
import VueRouter from 'vue-router/vite'

const RouteGenerateExclude = ['**/components/**', '**/layouts/**', '**/data/**', '**/types/**']

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.SAIL_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: true },
        '/ws': {
          target: env.SAIL_BACKEND_URL || 'http://127.0.0.1:8080',
          ws: true,
          changeOrigin: true,
        },
      },
    },
    plugins: [
      VueRouter({
        exclude: RouteGenerateExclude,
        dts: 'src/types/route-map.d.ts',
      }),
      vue(),
      tailwindcss(),
      ...(process.env.ANALYZE
        ? [visualizer({ gzipSize: true, brotliSize: true }) as PluginOption]
        : []),
      Layouts({
        defaultLayout: 'default',
      }),
      AutoImport({
        include: [/\.[tj]sx?$/, /\.vue$/],
        imports: ['vue', VueRouterAutoImports],
        dirs: ['src/composables/**/*.ts', 'src/constants/**/*.ts', 'src/stores/**/*.ts'],
        defaultExportByFilename: true,
        dts: 'src/types/auto-import.d.ts',
        // 清理或移动源码后同步移除失效声明，避免自动导入残留旧符号。
        dtsMode: 'overwrite',
      }),
      Component({
        dirs: ['src/components'],
        collapseSamePrefixes: true,
        directoryAsNamespace: true,
        dts: 'src/types/auto-import-components.d.ts',
        syncMode: 'overwrite',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    test: {
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
        include: [
          'src/lib/**/*.ts',
          'src/services/**/*.ts',
          'src/stores/**/*.ts',
          'src/composables/**/*.ts',
          'src/utils/**/*.ts',
          'src/validators/**/*.ts',
          'src/router/guard/**/*.ts',
        ],
        exclude: ['src/**/*.d.ts'],
      },
    },
  }
})
