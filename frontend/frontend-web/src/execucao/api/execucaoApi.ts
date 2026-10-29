import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { Prioridade, StatusTarefa } from '../../tarefa/validation/tarefaForm'
import type { XpConclusao } from '../../xp/types'

export type StatusExecucao = 'NAO_INICIADO' | 'EM_ANDAMENTO' | 'CONCLUIDO' | 'ABORTADO'

export type TarefaResumoExecucao = {
  id: string
  titulo: string
  prioridade: Prioridade
  status: StatusTarefa
  duracaoMinutos: number
}

export type BlocoExecucaoResponse = {
  id: string
  duracaoMinutos: number
  ordem: number
}

export type ExecucaoResponse = {
  id: string | null
  blocoPlanoId: string | null
  status: StatusExecucao
  iniciadoEm: string | null
  concluidoEm: string | null
  abortadoEm: string | null
  xp?: XpConclusao | null
}

export type ItemExecucaoResponse = {
  bloco: BlocoExecucaoResponse
  tarefaResumo: TarefaResumoExecucao
  execucao: ExecucaoResponse
}

export type PlanoAprovadoResumoResponse = {
  id: string
  data: string
  versao: number
}

export type ExecucoesDoDiaResponse = {
  planoAprovado: PlanoAprovadoResumoResponse | null
  itens: ItemExecucaoResponse[]
}

export function buildExecucoesQuery(data?: string): string {
  if (!data) {
    return '/execucoes'
  }
  const query = new URLSearchParams()
  query.set('data', data)
  return `/execucoes?${query.toString()}`
}

export async function listarExecucoes(data?: string): Promise<ExecucoesDoDiaResponse> {
  const response = await apiFetch(buildExecucoesQuery(data))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as execuções.')
  }
  return readApiJson<ExecucoesDoDiaResponse>(response)
}

export async function iniciarExecucao(blocoId: string): Promise<ExecucaoResponse> {
  const response = await apiFetch('/execucoes/iniciar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ blocoId }),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível iniciar a execução.')
  }
  return readApiJson<ExecucaoResponse>(response)
}

export async function concluirExecucao(id: string): Promise<ExecucaoResponse> {
  const response = await apiFetch(`/execucoes/${id}/concluir`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível concluir a execução.')
  }
  return readApiJson<ExecucaoResponse>(response)
}

export async function abortarExecucao(id: string): Promise<ExecucaoResponse> {
  const response = await apiFetch(`/execucoes/${id}/abortar`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível abortar a execução.')
  }
  return readApiJson<ExecucaoResponse>(response)
}
