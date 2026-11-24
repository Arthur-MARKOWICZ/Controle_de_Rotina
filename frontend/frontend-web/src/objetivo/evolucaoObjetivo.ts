import { dataHojeIso } from '../planner/data'
import type { ObjetivoEvolucao, ObjetivoResponse } from './api/objetivoApi'
import { rotuloArquivamento, rotuloConclusao } from './prazoObjetivo'

export type DegrauEvolucao = {
  /** Data civil `yyyy-MM-dd` (BRT). */
  data: string
  noDia: number
  acumulado: number
}

export type MarcadorEncerramento = {
  data: string
  /** Ex.: "Concluído em 12 nov". */
  rotulo: string
}

export type SerieEvolucao = {
  /** Minutos sem data (tarefas finalizadas antes do registro). O gráfico parte deste valor. */
  antesDoHistorico: number
  /** Último acumulado, igual ao `minutosConcluidos` do progresso. */
  total: number
  degraus: DegrauEvolucao[]
  /** Primeiro e último dia do eixo. Só fazem sentido quando há degraus. */
  inicio: string
  fim: string
  marcador: MarcadorEncerramento | null
  /** Falso quando só há "antes do histórico": o texto do total aparece, sem gráfico. */
  temGrafico: boolean
}

type DadosDoObjetivo = Pick<ObjetivoResponse, 'status' | 'concluidoEm' | 'arquivadoEm'>

const DIA_MS = 24 * 60 * 60 * 1000

export function diasEntreDatas(deIso: string, ateIso: string): number {
  return Math.round((Date.parse(ateIso) - Date.parse(deIso)) / DIA_MS)
}

function maior(a: string, b: string): string {
  return a >= b ? a : b
}

function menor(a: string, b: string): string {
  return a <= b ? a : b
}

/** Dia do encerramento: a conclusão quando existe, senão o arquivamento (mesma regra da API). */
function marcadorDeEncerramento(
  objetivo: DadosDoObjetivo,
  hojeIso: string,
): MarcadorEncerramento | null {
  if (objetivo.status === 'ATIVO') {
    return null
  }
  const instante = objetivo.concluidoEm ?? objetivo.arquivadoEm
  if (!instante || Number.isNaN(Date.parse(instante))) {
    return null
  }
  const rotulo = objetivo.concluidoEm
    ? rotuloConclusao(objetivo.concluidoEm, hojeIso)
    : rotuloArquivamento(objetivo.arquivadoEm, hojeIso)
  return rotulo ? { data: dataHojeIso(new Date(instante)), rotulo } : null
}

/**
 * Prepara a resposta da API para o gráfico. Devolve `null` quando não há nada a mostrar
 * (sem pontos e sem minutos antes do histórico).
 *
 * Eixo: ativo vai do primeiro ponto até hoje; encerrado vai até o mais tarde entre o encerramento e o
 * último ponto, porque tarefas vinculadas ainda contam depois que o objetivo é encerrado.
 */
export function montarSerie(
  evolucao: ObjetivoEvolucao,
  objetivo: DadosDoObjetivo,
  hojeIso: string = dataHojeIso(),
): SerieEvolucao | null {
  const antes = Math.max(0, evolucao.antesDoHistorico)
  const degraus: DegrauEvolucao[] = [...evolucao.pontos]
    .sort((a, b) => (a.data < b.data ? -1 : a.data > b.data ? 1 : 0))
    .map((ponto) => ({
      data: ponto.data,
      noDia: ponto.minutosNoDia,
      acumulado: ponto.minutosAcumulados,
    }))

  if (degraus.length === 0) {
    if (antes === 0) {
      return null
    }
    return {
      antesDoHistorico: antes,
      total: antes,
      degraus,
      inicio: hojeIso,
      fim: hojeIso,
      marcador: null,
      temGrafico: false,
    }
  }

  const primeiro = degraus[0].data
  const ultimo = degraus[degraus.length - 1].data
  const marcador = marcadorDeEncerramento(objetivo, hojeIso)

  const inicio = marcador ? menor(primeiro, marcador.data) : primeiro
  const fim = marcador ? maior(ultimo, marcador.data) : maior(ultimo, hojeIso)

  return {
    antesDoHistorico: antes,
    total: degraus[degraus.length - 1].acumulado,
    degraus,
    inicio,
    fim,
    marcador,
    temGrafico: true,
  }
}
