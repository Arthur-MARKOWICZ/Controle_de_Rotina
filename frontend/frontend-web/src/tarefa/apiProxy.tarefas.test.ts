import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Reload em /tarefas não pode cair na API: tela SPA e CRUD compartilham o path.
 * Mesmo contrato de /habitos, /projetos, /decidir: HTML → index.html; JSON → backend.
 */
describe('proxy /tarefas (SPA + API)', () => {
  const root = resolve(__dirname, '../..')

  it('vite.config.ts usa spaApiSharedProxy para /tarefas e tira o path da lista só-API', () => {
    const vite = readFileSync(resolve(root, 'vite.config.ts'), 'utf8')
    const prefixes = vite.match(/const API_PROXY_PREFIXES = \[([\s\S]*?)\]/)?.[1] ?? ''

    expect(prefixes).not.toContain("'/tarefas'")
    expect(vite).toMatch(/['"]\/tarefas['"]\s*:\s*spaApiSharedProxy/)
  })

  it('nginx.conf trata /tarefas como SPA+API (HTML → index, JSON → backend)', () => {
    const nginx = readFileSync(resolve(root, 'nginx.conf'), 'utf8')
    const bloco = nginx.match(/location \^~ \/tarefas \{[\s\S]*?\n    \}/)?.[0]

    expect(bloco).toBeDefined()
    expect(bloco).toContain('$spa_api_json')
    expect(bloco).toContain('try_files /index.html')
  })

  it('API_PROXY.md documenta /tarefas como tela e API', () => {
    const doc = readFileSync(resolve(root, 'API_PROXY.md'), 'utf8')
    expect(doc).toMatch(/`\/tarefas` \(navegação HTML\)/)
  })
})
