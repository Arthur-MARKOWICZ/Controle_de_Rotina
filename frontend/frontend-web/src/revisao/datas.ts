import { adicionarDiasIso, inicioSemana, parseDataIsoLocal } from '../capacidade/semana'

const DATA_ISO = /^\d{4}-\d{2}-\d{2}$/

/**
 * Segunda-feira seguinte à semana de `hojeIso` (data civil BRT, YYYY-MM-DD).
 * No domingo, é o dia seguinte; na segunda, é a segunda da semana que vem.
 */
export function proximaSegunda(hojeIso: string): string {
  if (!DATA_ISO.test(hojeIso) || Number.isNaN(parseDataIsoLocal(hojeIso).getTime())) {
    throw new RangeError(`Data inválida: ${hojeIso}`)
  }
  return adicionarDiasIso(inicioSemana(parseDataIsoLocal(hojeIso)), 7)
}
