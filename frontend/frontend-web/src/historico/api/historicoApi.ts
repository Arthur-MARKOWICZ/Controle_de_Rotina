import { ApiError } from '../../auth/api/authApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

export type EscopoHistorico = 'DIA' | 'SEMANA'

export type HistoricoResumoResponse = {
  periodo: {
    escopo: EscopoHistorico
    inicio: string
    fim: string
  }
  tarefasFinalizadas: number
  blocosConcluidos: number
  minutosTrabalhados: number
  xpGanho: number
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

export function buildHistoricoDiaUrl(data?: string): string {
  if (!data) {
    return '/historico/dia'
  }
  return `/historico/dia?data=${encodeURIComponent(data)}`
}

export function buildHistoricoSemanaUrl(semanaInicio?: string): string {
  if (!semanaInicio) {
    return '/historico/semana'
  }
  return `/historico/semana?semanaInicio=${encodeURIComponent(semanaInicio)}`
}

export async function consultarHistoricoDia(data?: string): Promise<HistoricoResumoResponse> {
  const response = await apiFetch(buildHistoricoDiaUrl(data))
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível carregar o histórico do dia.')
  }
  return readApiJson(response)
}

export async function consultarHistoricoSemana(
  semanaInicio?: string,
): Promise<HistoricoResumoResponse> {
  const response = await apiFetch(buildHistoricoSemanaUrl(semanaInicio))
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível carregar o histórico da semana.')
  }
  return readApiJson(response)
}
