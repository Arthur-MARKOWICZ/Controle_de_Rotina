import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { Prioridade, StatusTarefa } from '../../tarefa/validation/tarefaForm'

export type StatusPlano = 'RASCUNHO' | 'APROVADO' | 'DESCARTADO'

export type TarefaResumoResponse = {
  id: string
  titulo: string
  prioridade: Prioridade
  status: StatusTarefa
  duracaoMinutos: number
  prazo: string | null
  projetoId?: string | null
  projetoNome?: string | null
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
  propostaId: string | null
  propostaPendente: boolean
  geradoEm: string
  createdAt: string
  updatedAt: string
  blocos: BlocoPlanoResponse[]
}

export type BuscarPlanoParams = {
  data: string
  versao?: number
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
    throw await lerErroApi(response, 'Não foi possível carregar o plano.')
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
    throw await lerErroApi(response, 'Não foi possível gerar o plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

/** Datas ISO (YYYY-MM-DD) com plano da mesma proposta, a partir de `inicio` (até 14 dias). */
export async function listarDatasDaProposta(
  inicio: string,
  propostaId: string,
  dias = 14,
): Promise<string[]> {
  const datas: string[] = []
  const [y, m, d] = inicio.split('-').map(Number)
  for (let i = 0; i < dias; i++) {
    const utc = new Date(Date.UTC(y, m - 1, d + i))
    const data = utc.toISOString().slice(0, 10)
    const plano = await buscarPlano({ data })
    if (plano?.propostaId === propostaId) {
      datas.push(data)
    }
  }
  return datas
}

export async function aprovarPlano(planoId: string): Promise<PlanoResponse> {
  const response = await apiFetch(`/planos/${planoId}/aprovar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível aprovar o plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function aceitarProposta(propostaId: string): Promise<PlanoResponse> {
  const response = await apiFetch(`/planos/propostas/${propostaId}/aceitar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível aceitar a proposta.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function descartarProposta(propostaId: string): Promise<void> {
  const response = await apiFetch(`/planos/propostas/${propostaId}/descartar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível descartar a proposta.')
  }
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
    throw await lerErroApi(response, 'Não foi possível adicionar a tarefa ao plano.')
  }
  return readApiJson<PlanoResponse>(response)
}

export async function removerBloco(planoId: string, blocoId: string): Promise<void> {
  const response = await apiFetch(`/planos/${planoId}/blocos/${blocoId}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível remover o bloco.')
  }
}
