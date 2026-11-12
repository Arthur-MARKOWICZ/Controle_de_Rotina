import type { TarefaResponse } from './api/tarefaApi'
import type { FiltrosListaUrl } from './listaUrl'

export function temFiltroIsolador(filtros: FiltrosListaUrl): boolean {
  return (
    filtros.prioridade.length > 0 ||
    Boolean(filtros.prazoDe) ||
    Boolean(filtros.prazoAte) ||
    Boolean(filtros.duracaoDe) ||
    Boolean(filtros.duracaoAte) ||
    Boolean(filtros.projetoId) ||
    filtros.tagIds.length > 0
  )
}

/** Lista sem filtro isolador: só raízes, filhos no expandir. */
export function ehVisaoArvore(filtros: FiltrosListaUrl): boolean {
  return filtros.visao === 'todas' && !temFiltroIsolador(filtros)
}

/** Excluídas permanece plana: restaurar filho já reabre o pai na API. */
export function ehVisaoPlana(filtros: FiltrosListaUrl): boolean {
  return filtros.visao === 'excluidas'
}

/** Topo da lista filtrada: raízes + filhos cujo pai não está no resultado. */
export function tarefasNoNivelDaLista(tarefas: TarefaResponse[]): TarefaResponse[] {
  const ids = new Set(tarefas.map((tarefa) => tarefa.id))
  return tarefas.filter((tarefa) => !tarefa.parentId || !ids.has(tarefa.parentId))
}

export function filhosNaPagina(tarefas: TarefaResponse[], parentId: string): TarefaResponse[] {
  return tarefas
    .filter((tarefa) => tarefa.parentId === parentId)
    .slice()
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}
