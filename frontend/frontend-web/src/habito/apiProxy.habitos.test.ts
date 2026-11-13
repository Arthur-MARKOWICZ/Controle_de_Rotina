import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * T15: proxy `/habitos` registrado (Vite + Nginx + doc) —
 * evita HTML no JSON quando o path é compartilhado SPA/API.
 */
describe('proxy /habitos (T15)', () => {
  const root = resolve(__dirname, '../..')

  it('vite.config.ts usa spaApiSharedProxy para /habitos', () => {
    const vite = readFileSync(resolve(root, 'vite.config.ts'), 'utf8')
    expect(vite).toMatch(/['"]\/habitos['"]\s*:\s*spaApiSharedProxy/)
  })

  it('nginx.conf tem location ^~ /habitos com bypass JSON', () => {
    const nginx = readFileSync(resolve(root, 'nginx.conf'), 'utf8')
    expect(nginx).toContain('location ^~ /habitos')
    expect(nginx).toContain('$spa_api_json')
  })

  it('API_PROXY.md lista /habitos', () => {
    const doc = readFileSync(resolve(root, 'API_PROXY.md'), 'utf8')
    expect(doc).toMatch(/\|\s*`\/habitos`\s*\|/)
  })

  it('vite não coloca /habitos na lista só-API', () => {
    const vite = readFileSync(resolve(root, 'vite.config.ts'), 'utf8')
    const prefixes = vite.match(/const API_PROXY_PREFIXES = \[([\s\S]*?)\]/)?.[1] ?? ''
    expect(prefixes).not.toContain("'/habitos'")
  })

  it('nginx de /habitos devolve index.html para HTML', () => {
    const nginx = readFileSync(resolve(root, 'nginx.conf'), 'utf8')
    const bloco = nginx.match(/location \^~ \/habitos \{[\s\S]*?\n    \}/)?.[0]
    expect(bloco).toBeDefined()
    expect(bloco).toContain('try_files /index.html')
  })

  it('vite e nginx concordam no path /habitos', () => {
    const vite = readFileSync(resolve(root, 'vite.config.ts'), 'utf8')
    const nginx = readFileSync(resolve(root, 'nginx.conf'), 'utf8')
    expect(vite).toMatch(/['"]\/habitos['"]/)
    expect(nginx).toContain('location ^~ /habitos')
  })

  it('API_PROXY.md documenta o bypass JSON vs HTML', () => {
    const doc = readFileSync(resolve(root, 'API_PROXY.md'), 'utf8')
    expect(doc.toLowerCase()).toMatch(/html/)
    expect(doc.toLowerCase()).toMatch(/json/)
  })
})
