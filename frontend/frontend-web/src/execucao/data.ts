/** Minutos restantes do bloco em andamento (nunca negativo). */
export function minutosRestantes(
  iniciadoEm: string,
  duracaoMinutos: number,
  agora: Date = new Date(),
): number {
  const inicioMs = Date.parse(iniciadoEm)
  if (Number.isNaN(inicioMs) || duracaoMinutos < 1) {
    return 0
  }
  const decorridoMin = (agora.getTime() - inicioMs) / 60_000
  return Math.max(0, Math.ceil(duracaoMinutos - decorridoMin))
}

/** Minutos além da duração prevista (0 enquanto dentro do tempo). */
export function minutosExcedidos(
  iniciadoEm: string,
  duracaoMinutos: number,
  agora: Date = new Date(),
): number {
  const inicioMs = Date.parse(iniciadoEm)
  if (Number.isNaN(inicioMs)) {
    return 0
  }
  const decorridoMin = (agora.getTime() - inicioMs) / 60_000
  return Math.max(0, Math.ceil(decorridoMin - duracaoMinutos))
}

/** Formata minutos como "1h32" / "45min" / "0min". */
export function formatarTempoRestante(minutos: number): string {
  const m = Math.max(0, Math.floor(minutos))
  if (m < 60) {
    return `${m}min`
  }
  const horas = Math.floor(m / 60)
  const resto = m % 60
  if (resto === 0) {
    return `${horas}h`
  }
  return `${horas}h${String(resto).padStart(2, '0')}`
}
