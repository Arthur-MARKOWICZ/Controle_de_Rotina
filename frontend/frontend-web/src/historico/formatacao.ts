import { adicionarDiasIso, parseDataIsoLocal } from '../capacidade/semana'

export function formatarMinutosTrabalhados(minutos: number): string {
  const total = Math.max(0, Math.floor(minutos))
  const horas = Math.floor(total / 60)
  const resto = total % 60
  if (horas === 0) {
    return `${resto}min`
  }
  if (resto === 0) {
    return `${horas}h`
  }
  return `${horas}h${String(resto).padStart(2, '0')}`
}

/** ISO YYYY-MM-DD → dd/mm/aaaa (pt-BR). */
export function formatarDataCurtaPtBr(iso: string): string {
  return parseDataIsoLocal(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

/** ISO YYYY-MM-DD → rótulo curto do dia da semana (Seg, Ter, …). */
export function formatarDiaSemanaCurto(iso: string): string {
  const data = parseDataIsoLocal(iso)
  const weekday = new Intl.DateTimeFormat('pt-BR', { weekday: 'short' }).format(data)
  const limpo = weekday.replace(/\.$/, '')
  return limpo.charAt(0).toUpperCase() + limpo.slice(1)
}

/**
 * Título dinâmico do card do dia: "Hoje", "Ontem" ou "Terça, 20/10".
 */
export function tituloDiaHistorico(dataIso: string, hojeIso: string): string {
  if (dataIso === hojeIso) {
    return 'Hoje'
  }
  if (dataIso === adicionarDiasIso(hojeIso, -1)) {
    return 'Ontem'
  }
  const data = parseDataIsoLocal(dataIso)
  const weekday = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' }).format(data)
  const curto = weekday.replace(/-feira$/i, '')
  const capitalizado = curto.charAt(0).toUpperCase() + curto.slice(1)
  const diaMes = data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
  return `${capitalizado}, ${diaMes}`
}
