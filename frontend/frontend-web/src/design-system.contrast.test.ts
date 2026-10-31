import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const tokensCss = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'styles', 'tokens.css'),
  'utf8',
)

const PARES: [string, string][] = [
  ['--ink', '--bg'],
  ['--ink', '--panel'],
  ['--muted', '--panel'],
  ['--on-accent', '--accent'],
  ['--on-cta', '--cta'],
  ['--danger', '--danger-bg'],
  ['--success', '--success-bg'],
  ['--warning', '--warning-bg'],
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
})
