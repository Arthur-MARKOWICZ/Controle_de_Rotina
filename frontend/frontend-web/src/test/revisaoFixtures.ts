import type { DecidirResponse, PageDecidir } from '../decidir/api/decidirApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import type { PageTarefa, TarefaResponse } from '../tarefa/api/tarefaApi'

export function tarefa(parcial: Partial<TarefaResponse> & { id: string }): TarefaResponse {
  return {
    titulo: `Tarefa ${parcial.id}`,
    descricao: '',
    prioridade: 'MEDIA',
    prazo: '2026-11-10T23:59:00Z',
    status: 'ATRASADA',
    duracaoMinutos: 60,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    deletedAt: null,
    deletada: false,
    esperas: [],
    bloqueada: false,
    ...parcial,
  }
}

export function pagina(content: TarefaResponse[]): PageTarefa {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 50 }
}

export function itemDecidir(parcial: Partial<DecidirResponse> & { id: string }): DecidirResponse {
  return {
    titulo: `Item ${parcial.id}`,
    descricao: null,
    decidirAte: null,
    projetoId: null,
    tarefaId: null,
    tags: [],
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    ...parcial,
  }
}

export function paginaDecidir(content: DecidirResponse[]): PageDecidir {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 50 }
}

export function plano(parcial: Partial<PlanoResponse> & { id: string; data: string }): PlanoResponse {
  return {
    versao: 1,
    status: 'RASCUNHO',
    temConflito: false,
    mensagem: null,
    propostaId: 'prop-1',
    propostaPendente: true,
    geradoEm: '2026-11-13T15:00:00Z',
    createdAt: '2026-11-13T15:00:00Z',
    updatedAt: '2026-11-13T15:00:00Z',
    blocos: [],
    itensNaoAlocados: [],
    ...parcial,
  }
}
