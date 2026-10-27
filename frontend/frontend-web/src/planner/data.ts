import type { OrigemCapacidade } from '../capacidade/api/capacidadeResolvidaApi'
import type { StatusTarefa } from '../tarefa/validation/tarefaForm'

const STATUS_ELEGIVEIS: StatusTarefa[] = ['PENDENTE', 'EM_PROGRESSO', 'ATRASADA']

/** Data civil de `ref` em America/Sao_Paulo (YYYY-MM-DD). */
export function dataHojeIso(ref: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(ref)
}

export function minutosUsados(blocos: { duracaoMinutos: number }[]): number {
  return blocos.reduce((acc, bloco) => acc + bloco.duracaoMinutos, 0)
}

export function minutosDisponiveis(
  horas: number | null | undefined,
  _origem?: OrigemCapacidade,
): number {
  if (horas == null || horas <= 0) {
    return 0
  }
  return horas * 60
}

export function formatarCapacidadeMinutos(minutos: number): string {
  if (minutos <= 0) {
    return '0min'
  }
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  if (horas === 0) {
    return `${resto}min`
  }
  if (resto === 0) {
    return `${horas}h`
  }
  return `${horas}h ${resto}min`
}

export function statusElegiveisParaPlano(): StatusTarefa[] {
  return [...STATUS_ELEGIVEIS]
}

/** Tarefas elegíveis que ainda não têm bloco no plano. */
export function filtrarElegiveisForaDoPlano<
  T extends { id: string; status: StatusTarefa; deletada?: boolean },
>(tarefas: T[], tarefaIdsNoPlano: Set<string> | string[]): T[] {
  const noPlano = tarefaIdsNoPlano instanceof Set ? tarefaIdsNoPlano : new Set(tarefaIdsNoPlano)
  return tarefas.filter(
    (tarefa) =>
      !tarefa.deletada &&
      STATUS_ELEGIVEIS.includes(tarefa.status) &&
      !noPlano.has(tarefa.id),
  )
}
