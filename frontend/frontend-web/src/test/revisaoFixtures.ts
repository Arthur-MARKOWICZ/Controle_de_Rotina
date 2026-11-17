import type { DecidirResponse, PageDecidir } from '../decidir/api/decidirApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import type { TagResponse } from '../tag/api/tagApi'
import type { PageTarefa, TarefaResponse } from '../tarefa/api/tarefaApi'

/** TarefaResponse completa (todos os campos do contrato); sobrescreva só o que o teste precisa. */
export function tarefa(parcial: Partial<TarefaResponse> & { id: string }): TarefaResponse {
  return {
    titulo: `Tarefa ${parcial.id}`,
    descricao: '',
    prioridade: 'MEDIA',
    prazo: '2026-11-10T23:59:00Z',
    status: 'ATRASADA',
    duracaoMinutos: 60,
    restanteMinutos: parcial.duracaoMinutos ?? 60,
    ordem: 0,
    projetoId: null,
    projetoNome: null,
    tags: [],
    tagMatchScore: null,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    deletedAt: null,
    deletada: false,
    parentId: null,
    parentTitulo: null,
    temFilhos: false,
    esperas: [],
    bloqueada: false,
    ...parcial,
  }
}

/** TagResponse completa; `TagResumo` é subconjunto, então serve onde se espera resumo. */
export function tag(parcial: Partial<TagResponse> & { id: string }): TagResponse {
  return {
    nome: `tag-${parcial.id}`,
    cor: '#6B7280',
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    ...parcial,
  }
}

export function pagina(content: TarefaResponse[]): PageTarefa {
  return { content, page: { size: 50, number: 0, totalElements: content.length, totalPages: 1 } }
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
  return { content, page: { size: 50, number: 0, totalElements: content.length, totalPages: 1 } }
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
