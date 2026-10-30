import type { DiaSemana } from '../capacidade/api/capacidadeSemanalApi'
import { parseDataIsoLocal } from '../capacidade/semana'
import type { ReservaProjetoResponse } from '../projeto/api/reservaProjetoApi'

const DIAS_JS: DiaSemana[] = [
  'DOMINGO',
  'SEGUNDA',
  'TERCA',
  'QUARTA',
  'QUINTA',
  'SEXTA',
  'SABADO',
]

export function diaSemanaDeDataIso(iso: string): DiaSemana {
  return DIAS_JS[parseDataIsoLocal(iso).getDay()]
}

function formatarTempoReservado(minutos: number): string {
  if (minutos <= 0) {
    return '0h'
  }
  if (minutos % 60 === 0) {
    return `${minutos / 60}h`
  }
  if (minutos < 60) {
    return `${minutos} min`
  }
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return `${horas}h ${resto} min`
}

/** Copy curto: reservas do dia vs tempo livre (sem jargão de motor). */
export function formatarExplicacaoReservas(
  reservasDoDia: ReservaProjetoResponse[],
  nomesPorProjetoId: Record<string, string>,
): string | null {
  if (reservasDoDia.length === 0) {
    return null
  }

  const partes = reservasDoDia.map((reserva) => {
    const nome = nomesPorProjetoId[reserva.projetoId] ?? 'projeto'
    const tempo = formatarTempoReservado(reserva.minutosReservados)
    return `${tempo} reservadas ao projeto ${nome}`
  })

  return `${partes.join('; ')}; o restante segue a prioridade geral.`
}
