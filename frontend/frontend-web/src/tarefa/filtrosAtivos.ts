import { PRIORIDADE_LABEL } from '../home/labels'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { TagResponse } from '../tag/api/tagApi'
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
  catalogoTags: TagResponse[] = [],
): ChipFiltro[] {
  const chips: ChipFiltro[] = []
  if (filtros.prioridade.length) {
    chips.push({
      id: 'prioridade',
      texto: `Prioridade: ${filtros.prioridade.map((p) => PRIORIDADE_LABEL[p]).join(', ')}`,
      remover: { prioridade: [] },
    })
  }
  if (filtros.semPrazo) {
    chips.push({
      id: 'prazo',
      texto: 'Sem prazo',
      remover: { semPrazo: false, prazoDe: '', prazoAte: '' },
    })
  } else if (filtros.prazoDe || filtros.prazoAte) {
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
  if (filtros.semProjeto) {
    chips.push({
      id: 'projeto',
      texto: 'Sem projeto',
      remover: { semProjeto: false, projetoId: '' },
    })
  } else if (filtros.projetoId) {
    const nome = projetosAtivos.find((p) => p.id === filtros.projetoId)?.nome ?? 'Projeto'
    chips.push({ id: 'projeto', texto: `Projeto: ${nome}`, remover: { projetoId: '' } })
  }
  if (filtros.semTag) {
    chips.push({
      id: 'tags',
      texto: 'Sem tag',
      remover: { semTag: false, tagIds: [] },
    })
  } else if (filtros.tagIds.length) {
    const nomes = filtros.tagIds.map(
      (id) => catalogoTags.find((tag) => tag.id === id)?.nome ?? id.slice(0, 8),
    )
    chips.push({
      id: 'tags',
      texto: `Tags: ${nomes.join(', ')}`,
      remover: { tagIds: [] },
    })
  }
  return chips
}

/** Volta ao default, mantendo a visão escolhida e o tamanho de página. */
export function filtrosLimpos(filtros: FiltrosListaUrl): FiltrosListaUrl {
  return { ...FILTROS_LISTA_INICIAIS, visao: filtros.visao, size: filtros.size }
}
