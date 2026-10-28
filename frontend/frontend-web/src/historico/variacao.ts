/** Variação percentual; `null` quando a base é 0 (indefinido). */
export function variacaoPercentual(atual: number, anterior: number): number | null {
  if (anterior === 0) {
    return null
  }
  return Math.round(((atual - anterior) / anterior) * 100)
}

/** Ex.: "+12%", "-20%", "0%" ou "—" se indefinido. */
export function formatarVariacaoPercentual(variacao: number | null): string {
  if (variacao === null) {
    return '—'
  }
  if (variacao > 0) {
    return `+${variacao}%`
  }
  return `${variacao}%`
}
