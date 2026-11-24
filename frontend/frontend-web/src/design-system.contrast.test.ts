import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const tokensCss = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'styles', 'tokens.css'),
  'utf8',
)

/** Texto sobre fundo: AA ≥ 4,5:1. */
const PARES: [string, string][] = [
  ['--ink', '--bg'],
  ['--ink', '--panel'],
  ['--ink', '--raised'],
  ['--muted', '--panel'],
  ['--muted', '--bg'],
  ['--muted', '--raised'],
  ['--accent', '--panel'],
  ['--accent', '--accent-soft'],
  ['--on-accent', '--accent'],
  ['--on-xp', '--xp'],
  ['--danger', '--panel'],
  ['--danger', '--danger-bg'],
  ['--success', '--panel'],
  ['--success', '--success-bg'],
  ['--warning', '--panel'],
  ['--warning', '--warning-bg'],
]

/** Componentes e bordas de controle (WCAG 1.4.11): ≥ 3:1. */
const PARES_NAO_TEXTO: [string, string][] = [
  ['--line-strong', '--panel'],
  ['--line-strong', '--bg'],
  ['--xp', '--panel'],
  ['--focus', '--panel'],
]

function lerBloco(seletor: RegExp): Map<string, string> {
  const corpo = tokensCss.match(seletor)?.[1] ?? ''
  const tokens = new Map<string, string>()
  for (const [, nome, valor] of corpo.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    tokens.set(nome, valor.trim())
  }
  return tokens
}

function resolverHex(tokens: Map<string, string>, nome: string): string {
  const valor = tokens.get(nome)
  if (!valor) throw new Error(`token ${nome} não definido`)
  const referencia = valor.match(/^var\((--[\w-]+)\)$/)
  if (referencia) return resolverHex(tokens, referencia[1])
  if (!/^#[0-9a-fA-F]{6}$/.test(valor)) throw new Error(`${nome} não é hex: ${valor}`)
  return valor
}

function luminancia(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const canal = parseInt(hex.slice(i, i + 2), 16) / 255
    return canal <= 0.03928 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** Matiz OKLCH em graus (0–360). */
function matizOklch(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const canal = parseInt(hex.slice(i, i + 2), 16) / 255
    return canal <= 0.04045 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4
  })
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
  const bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  return ((Math.atan2(bb, a) * 180) / Math.PI + 360) % 360
}

function contraste(a: string, b: string): number {
  const [maior, menor] = [luminancia(a), luminancia(b)].sort((x, y) => y - x)
  return (maior + 0.05) / (menor + 0.05)
}

const claro = lerBloco(/:root\s*\{([\s\S]*?)\n\}/)
const escuro = new Map([...claro, ...lerBloco(/:root\[data-theme="dark"\]\s*\{([\s\S]*?)\n\}/)])

describe('contraste dos tokens (T1)', () => {
  for (const [tema, tokens] of [
    ['claro', claro],
    ['escuro', escuro],
  ] as const) {
    it.each(PARES)(`${tema}: %s sobre %s ≥ 4,5:1`, (texto, fundo) => {
      const razao = contraste(resolverHex(tokens, texto), resolverHex(tokens, fundo))
      expect(razao).toBeGreaterThanOrEqual(4.5)
    })
  }

  for (const [tema, tokens] of [
    ['claro', claro],
    ['escuro', escuro],
  ] as const) {
    it.each(PARES_NAO_TEXTO)(`${tema}: %s sobre %s ≥ 3:1 (não texto)`, (cor, fundo) => {
      const razao = contraste(resolverHex(tokens, cor), resolverHex(tokens, fundo))
      expect(razao).toBeGreaterThanOrEqual(3)
    })

    it(`${tema}: XP e aviso ficam a ≥ 25° de matiz (não se confundem)`, () => {
      const xp = matizOklch(resolverHex(tokens, '--xp'))
      const aviso = matizOklch(resolverHex(tokens, '--warning'))
      const distancia = Math.min(Math.abs(xp - aviso), 360 - Math.abs(xp - aviso))
      expect(distancia).toBeGreaterThanOrEqual(25)
    })

    it(`${tema}: borda decorativa difere da superfície elevada`, () => {
      expect(resolverHex(tokens, '--line')).not.toBe(resolverHex(tokens, '--raised'))
    })
  }

  it('deveCalcularMatizDoVermelhoPuroPertoDe29Graus', () => {
    expect(matizOklch('#ff0000')).toBeCloseTo(29.2, 0)
  })

  it('deveDefinirTodosOsParesNoTemaClaro', () => {
    for (const [texto, fundo] of PARES) {
      expect(claro.has(texto) || claro.has(fundo)).toBe(true)
      expect(() => resolverHex(claro, texto)).not.toThrow()
      expect(() => resolverHex(claro, fundo)).not.toThrow()
    }
  })

  it('deveDefinirTodosOsParesNoTemaEscuro', () => {
    for (const [texto, fundo] of PARES) {
      expect(() => resolverHex(escuro, texto)).not.toThrow()
      expect(() => resolverHex(escuro, fundo)).not.toThrow()
    }
  })

  it('deveResolverReferenciaVarAteHex', () => {
    const comVar = new Map([['--bg', '#111111'], ['--ink', 'var(--bg)']])
    expect(resolverHex(comVar, '--ink')).toBe('#111111')
  })

  it('naoDeveAceitarTokenAusente', () => {
    expect(() => resolverHex(claro, '--token-inexistente')).toThrow(/não definido/)
  })

  it('naoDeveAceitarValorQueNaoEHex', () => {
    const invalidos = new Map([['--ink', 'rgb(0,0,0)']])
    expect(() => resolverHex(invalidos, '--ink')).toThrow(/não é hex/)
  })

  it('deveRetornarContrasteUmQuandoAsCoresSaoIguais', () => {
    expect(contraste('#111111', '#111111')).toBe(1)
  })
})
