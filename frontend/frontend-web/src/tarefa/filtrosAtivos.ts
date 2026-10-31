import { PRIORIDADE_LABEL } from '../home/labels'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import { formatarDataHora } from './datetime'
import { FILTROS_LISTA_INICIAIS, type FiltrosListaUrl } from './listaUrl'

export type ChipFiltro = { id: string; texto: string; remover: Partial<FiltrosListaUrl> }

function textoIntervalo(de: string, ate: string, formatar: (v: string) => string): string {
  if (de && ate) {
    return `${formatar(de)} – ${formatar(ate)}`
  }
  return de ? `a partir de ${formatar(de)}` : `até ${formatar(ate)}`
}

/** Filtros da URL que viram chips removíveis (visão e ordenação ficam de fora). */
export function chipsAtivos(
  filtros: FiltrosListaUrl,
  projetosAtivos: ProjetoResponse[],
): ChipFiltro[] {
  const chips: ChipFiltro[] = []
  if (filtros.prioridade.length) {
    chips.push({
      id: 'prioridade',
      texto: `Prioridade: ${filtros.prioridade.map((p) => PRIORIDADE_LABEL[p]).join(', ')}`,
      remover: { prioridade: [] },
    })
  }
  if (filtros.prazoDe || filtros.prazoAte) {
    chips.push({
      id: 'prazo',
      texto: `Prazo: ${textoIntervalo(filtros.prazoDe, filtros.prazoAte, formatarDataHora)}`,
      remover: { prazoDe: '', prazoAte: '' },
    })
  }
  if (filtros.duracaoDe || filtros.duracaoAte) {
    chips.push({
      id: 'duracao',
      texto: `Duração: ${textoIntervalo(filtros.duracaoDe, filtros.duracaoAte, (v) => `${v} min`)}`,
      remover: { duracaoDe: '', duracaoAte: '' },
    })
  }
  if (filtros.projetoId) {
    const nome = projetosAtivos.find((p) => p.id === filtros.projetoId)?.nome ?? 'Projeto'
    chips.push({ id: 'projeto', texto: `Projeto: ${nome}`, remover: { projetoId: '' } })
  }
  return chips
}

/** Volta ao default, mantendo a visão escolhida e o tamanho de página. */
export function filtrosLimpos(filtros: FiltrosListaUrl): FiltrosListaUrl {
  return { ...FILTROS_LISTA_INICIAIS, visao: filtros.visao, size: filtros.size }
}
