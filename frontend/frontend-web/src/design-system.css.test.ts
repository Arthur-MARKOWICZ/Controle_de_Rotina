import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const srcDir = dirname(fileURLToPath(import.meta.url))
const stylesDir = join(srcDir, 'styles')

function readAppCss(): string {
  const styleFiles = readdirSync(stylesDir)
    .filter((name) => name.endsWith('.css'))
    .sort()
    .map((name) => readFileSync(join(stylesDir, name), 'utf8'))
  const indexCss = readFileSync(join(srcDir, 'index.css'), 'utf8')
  return [indexCss, ...styleFiles].join('\n')
}

describe('design system CSS contract (T2)', () => {
  it('index.css importa todos os arquivos em styles/', () => {
    const indexCss = readFileSync(join(srcDir, 'index.css'), 'utf8')
    const styleFiles = readdirSync(stylesDir).filter((name) => name.endsWith('.css')).sort()
    for (const name of styleFiles) {
      expect(indexCss).toContain(`@import './styles/${name}'`)
    }
  })

  it('não usa !important no CSS da app', () => {
    const css = readAppCss()
    expect(css).not.toMatch(/!important/)
  })

  it('não usa cores hex fora de :root', () => {
    const css = readAppCss()
    const afterRoot = css.replace(/:root\s*\{[\s\S]*?\n\}/, '')
    expect(afterRoot).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
  })

  it('define tokens semânticos e classes base em :root', () => {
    const css = readAppCss()
    expect(css).toMatch(/--success\s*:/)
    expect(css).toMatch(/--warning\s*:/)
    expect(css).toMatch(/--danger\s*:/)
    expect(css).toMatch(/--danger-bg\s*:/)
    expect(css).toMatch(/--priority-alta\s*:/)
    expect(css).toMatch(/--priority-media\s*:/)
    expect(css).toMatch(/--priority-baixa\s*:/)
    expect(css).toMatch(/--status-atrasada\s*:/)
    expect(css).toMatch(/\.btn\s*\{/)
    expect(css).toMatch(/\.btn--primary/)
    expect(css).toMatch(/\.btn--secondary/)
    expect(css).toMatch(/\.btn--danger/)
    expect(css).toMatch(/\.btn--ghost/)
    expect(css).toMatch(/\.btn--sm/)
    expect(css).toMatch(/\.field\s*\{/)
    expect(css).toMatch(/\.card\s*\{/)
    expect(css).toMatch(/\.badge\s*\{/)
    expect(css).toMatch(/:focus-visible/)
  })

  it('define tokens de layout a11y (container único e toque 44px)', () => {
    const css = readAppCss()
    expect(css).toMatch(/--app-content-max\s*:/)
    expect(css).toMatch(/--touch-min\s*:\s*44px/)
    expect(css).toMatch(/\.btn--touch/)
  })

  it('app-shell: sidebar acompanha a altura do conteúdo sem buraco de fundo', () => {
    const shellCss = readFileSync(join(stylesDir, 'app-shell.css'), 'utf8')
    const sidebarBlock = shellCss.match(/\.app-shell__sidebar\s*\{[^}]+\}/)?.[0] ?? ''

    expect(sidebarBlock).toMatch(/min-height:\s*100%/)
    expect(sidebarBlock).not.toMatch(/height:\s*100vh/)
    expect(sidebarBlock).not.toMatch(/max-height:\s*100vh/)
    expect(shellCss).toMatch(
      /--app-shell-sidebar-bg:\s*var\(--panel\)/,
    )
    expect(shellCss).toMatch(
      /\.app-shell\s*\{[^}]*background-color:\s*var\(--bg\)/s,
    )
  })
})
