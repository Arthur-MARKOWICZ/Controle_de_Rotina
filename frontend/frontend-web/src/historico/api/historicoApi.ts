import { lerErroApi } from '../../auth/api/lerErroApi'
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
    throw await lerErroApi(response, 'Não foi possível carregar o histórico do dia.')
  }
  return readApiJson(response)
}

export async function consultarHistoricoSemana(
  semanaInicio?: string,
): Promise<HistoricoResumoResponse> {
  const response = await apiFetch(buildHistoricoSemanaUrl(semanaInicio))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o histórico da semana.')
  }
  return readApiJson(response)
}

export type MetricasCalibracao = {
  amostras: number
  suficiente: boolean
  /** `null` quando `suficiente` é falso (sem dados). */
  razao: number | null
  desvioMedioMinutos: number | null
}

export type RecorteTagCalibracao = MetricasCalibracao & {
  tagId: string
  nome: string
  cor: string
}

export type RecorteProjetoCalibracao = MetricasCalibracao & {
  projetoId: string
  nome: string
}

export type CalibracaoResponse = {
  janela: { inicio: string; fim: string }
  minimoAmostras: number
  geral: MetricasCalibracao
  porTag: RecorteTagCalibracao[]
  porProjeto: RecorteProjetoCalibracao[]
}

export const HISTORICO_CALIBRACAO_URL = '/historico/calibracao'

export async function consultarCalibracao(): Promise<CalibracaoResponse> {
  const response = await apiFetch(HISTORICO_CALIBRACAO_URL)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar sua calibração.')
  }
  return readApiJson(response)
}
