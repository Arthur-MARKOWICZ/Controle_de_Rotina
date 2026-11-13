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
  '/capacidades',
  '/planos',
  '/execucoes',
  // /historico (exato) é rota SPA; só os subpaths da API
  '/historico/dia',
  '/historico/semana',
  '/historico/calibracao',
] as const

const backendProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
}

/**
 * SPA e API compartilham o path (ex.: /tarefas, /calendario, /projetos).
 * Navegação do browser (Accept: text/html) → index.html;
 * fetch da API (Accept: application/json) → proxy no backend.
 */
const spaApiSharedProxy = {
  ...backendProxy,
  bypass(req: { headers: { accept?: string } }) {
    if (req.headers.accept?.includes('text/html')) {
      return '/index.html'
    }
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      ...Object.fromEntries(API_PROXY_PREFIXES.map((path) => [path, backendProxy])),
      '/calendario': spaApiSharedProxy,
      '/projetos': spaApiSharedProxy,
      '/habitos': spaApiSharedProxy,
      '/tags': spaApiSharedProxy,
      '/decidir': spaApiSharedProxy,
      '/tarefas': spaApiSharedProxy,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    pool: 'threads',
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'html', 'lcov', 'json-summary'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.test.{ts,tsx}',
        'src/test/**',
        'src/main.tsx',
        'src/**/*.d.ts',
        'src/**/types.ts',
      ],
      // Catraca (ver AGENTS.md — Testes): só sobe, nunca desce. Alvo: 70/60.
      thresholds: {
        lines: 86,
        branches: 81,
      },
    },
  },
})
