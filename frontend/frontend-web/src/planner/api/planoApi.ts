import { ApiError } from '../../auth/api/authApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { Prioridade, StatusTarefa } from '../../tarefa/validation/tarefaForm'

export type StatusPlano = 'RASCUNHO' | 'APROVADO'

export type TarefaResumoResponse = {
  id: string
  titulo: string
  prioridade: Prioridade
  status: StatusTarefa
  duracaoHoras: number
  prazo: string
}

export type BlocoPlanoResponse = {
  id: string
  duracaoMinutos: number
  ordem: number
  tarefa: TarefaResumoResponse
}

export type PlanoResponse = {
  id: string
  data: string
  versao: number
  status: StatusPlano
  temConflito: boolean
  mensagem: string | null
  geradoEm: string
  createdAt: string
  updatedAt: string
  blocos: BlocoPlanoResponse[]
}

export type BuscarPlanoParams = {
  data: string
  versao?: number
}

async function lerErro(response: Response, fallback: string): Promise<ApiError> {
  try {
    const body = await readApiJson<{ detail?: string; title?: string }>(response)
    return new ApiError(response.status, body.detail || body.title || fallback)
  } catch (error) {
    if (error instanceof ApiError) {
      return error
    }
    return new ApiError(response.status, fallback)
  }
}

export function buildPlanoQuery(params: BuscarPlanoParams): string {
  const query = new URLSearchParams()
  query.set('data', params.data)
  if (params.versao != null) {
    query.set('versao', String(params.versao))
  }
  return `/planos?${query.toString()}`
}

export async function buscarPlano(params: BuscarPlanoParams): Promise<PlanoResponse | null> {
  const response = await apiFetch(buildPlanoQuery(params))
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível carregar o plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function gerarPlano(data: string): Promise<PlanoResponse> {
  const params = new URLSearchParams()
  params.set('data', data)
  const response = await apiFetch(`/planos/gerar?${params.toString()}`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível gerar o plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function aprovarPlano(planoId: string): Promise<PlanoResponse> {
  const response = await apiFetch(`/planos/${planoId}/aprovar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível aprovar o plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function adicionarBloco(
  planoId: string,
  tarefaId: string,
): Promise<PlanoResponse> {
  const response = await apiFetch(`/planos/${planoId}/blocos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tarefaId }),
  })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível adicionar a tarefa ao plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function removerBloco(planoId: string, blocoId: string): Promise<void> {
  const response = await apiFetch(`/planos/${planoId}/blocos/${blocoId}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível remover o bloco.')
  }
}
