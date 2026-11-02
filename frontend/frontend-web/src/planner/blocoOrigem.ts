import type { BlocoPlanoResponse, OrigemBloco } from './api/planoApi'

export function origemDoBloco(bloco: BlocoPlanoResponse): OrigemBloco {
  if (bloco.origem) {
    return bloco.origem
  }
  if (bloco.habitoId || bloco.habito) {
    return 'HABITO'
  }
  return 'TAREFA'
}

export function ehBlocoHabito(bloco: BlocoPlanoResponse): boolean {
  return origemDoBloco(bloco) === 'HABITO'
}

export function tituloDoBloco(bloco: BlocoPlanoResponse): string {
  if (ehBlocoHabito(bloco)) {
    return bloco.habito?.titulo ?? 'Hábito'
  }
  return bloco.tarefa?.titulo ?? 'Tarefa'
}

/** Chave estável por origem (bloco.id muda a cada versão). */
export function chaveOrigemBloco(bloco: BlocoPlanoResponse): string {
  if (ehBlocoHabito(bloco)) {
    return `H:${bloco.habitoId ?? bloco.habito?.id ?? bloco.id}`
  }
  return `T:${bloco.tarefaId ?? bloco.tarefa?.id ?? bloco.id}`
}

export function tarefaIdsNoPlano(blocos: BlocoPlanoResponse[]): string[] {
  return blocos
    .filter((bloco) => !ehBlocoHabito(bloco))
    .map((bloco) => bloco.tarefaId ?? bloco.tarefa?.id)
    .filter((id): id is string => Boolean(id))
}
