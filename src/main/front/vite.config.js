import { fileURLToPath, URL } from 'node:url'
import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig({

  name: "eventbus-plus",
  type: "module",
  files: ["dist"],

  build: {
    lib: {
      entry: resolve(__dirname, 'src/eventbus-plus.js'),
      name: 'eventbus-plus',
      fileName: 'eventbus-plus',
    },
  },

  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server:{
    proxy: {
      '/eventbus': {
        target: 'http://localhost:49090',
        changeOrigin: true,
        ws: true
      }
  }
}


})
