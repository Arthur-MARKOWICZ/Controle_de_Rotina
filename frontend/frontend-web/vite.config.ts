import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/**
 * Prefixo novo de API? Atualize esta lista, nginx.conf e API_PROXY.md.
 * Depois reinicie o Vite (npm run dev).
 */
const API_PROXY_PREFIXES = [
  '/usuarios',
  '/auth',
  '/me',
  '/tarefas',
  '/capacidades',
  '/planos',
] as const

const backendProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: Object.fromEntries(API_PROXY_PREFIXES.map((path) => [path, backendProxy])),
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    pool: 'threads',
  },
})
