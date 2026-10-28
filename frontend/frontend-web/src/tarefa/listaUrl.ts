import type { Prioridade } from './validation/tarefaForm'
import type { SortCampo, SortDirection } from './api/tarefaApi'

export type VisaoLista = 'todas' | 'pendentes' | 'em_progresso' | 'finalizadas' | 'excluidas'

export type FiltrosListaUrl = {
  visao: VisaoLista
  prioridade: Prioridade[]
  prazoDe: string
  prazoAte: string
  duracaoDe: string
  duracaoAte: string
  sort: SortCampo
  direction: SortDirection
  size: number
  page: number
}

export const FILTROS_LISTA_INICIAIS: FiltrosListaUrl = {
  visao: 'todas',
  prioridade: [],
  prazoDe: '',
  prazoAte: '',
  duracaoDe: '',
  duracaoAte: '',
  sort: 'prazo',
  direction: 'asc',
  size: 20,
  page: 0,
}

const VISOES: VisaoLista[] = [
  'todas',
  'pendentes',
  'em_progresso',
  'finalizadas',
  'excluidas',
]

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']
const SORTS: SortCampo[] = ['prazo', 'duracao', 'prioridade']
const DIRS: SortDirection[] = ['asc', 'desc']

function parseListaCsv<T extends string>(raw: string | null, permitidos: T[]): T[] {
  if (!raw) {
    return []
  }
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter((s): s is T => (permitidos as string[]).includes(s))
}

export function lerFiltrosDaUrl(params: URLSearchParams): FiltrosListaUrl {
  const visaoRaw = params.get('visao')
  const visao = VISOES.includes(visaoRaw as VisaoLista)
    ? (visaoRaw as VisaoLista)
    : FILTROS_LISTA_INICIAIS.visao

  const sortRaw = params.get('sort')
  const sort = SORTS.includes(sortRaw as SortCampo)
    ? (sortRaw as SortCampo)
    : FILTROS_LISTA_INICIAIS.sort

  const dirRaw = params.get('direction')
  const direction = DIRS.includes(dirRaw as SortDirection)
    ? (dirRaw as SortDirection)
    : FILTROS_LISTA_INICIAIS.direction

  const page = Math.max(0, Number(params.get('page') ?? FILTROS_LISTA_INICIAIS.page) || 0)
  const sizeNum = Number(params.get('size') ?? FILTROS_LISTA_INICIAIS.size)
  const size = [10, 20, 50, 100].includes(sizeNum) ? sizeNum : FILTROS_LISTA_INICIAIS.size

  return {
    visao,
    prioridade: parseListaCsv(params.get('prioridade'), PRIORIDADES),
    prazoDe: params.get('prazoDe') ?? '',
    prazoAte: params.get('prazoAte') ?? '',
    duracaoDe: params.get('duracaoDe') ?? '',
    duracaoAte: params.get('duracaoAte') ?? '',
    sort,
    direction,
    size,
    page,
  }
}

export function escreverFiltrosNaUrl(filtros: FiltrosListaUrl): URLSearchParams {
  const params = new URLSearchParams()
  if (filtros.visao !== 'todas') {
    params.set('visao', filtros.visao)
  }
  if (filtros.prioridade.length) {
    params.set('prioridade', filtros.prioridade.join(','))
  }
  if (filtros.prazoDe) {
    params.set('prazoDe', filtros.prazoDe)
  }
  if (filtros.prazoAte) {
    params.set('prazoAte', filtros.prazoAte)
  }
  if (filtros.duracaoDe) {
    params.set('duracaoDe', filtros.duracaoDe)
  }
  if (filtros.duracaoAte) {
    params.set('duracaoAte', filtros.duracaoAte)
  }
  if (filtros.sort !== 'prazo') {
    params.set('sort', filtros.sort)
  }
  if (filtros.direction !== 'asc') {
    params.set('direction', filtros.direction)
  }
  if (filtros.size !== 20) {
    params.set('size', String(filtros.size))
  }
  if (filtros.page > 0) {
    params.set('page', String(filtros.page))
  }
  return params
}

export function paramsApiDaVisao(visao: VisaoLista): {
  status?: import('./validation/tarefaForm').StatusTarefa[]
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
} {
  switch (visao) {
    case 'pendentes':
      return { status: ['PENDENTE'] }
    case 'em_progresso':
      return { status: ['EM_PROGRESSO'] }
    case 'finalizadas':
      return { status: ['FINALIZADA'] }
    case 'excluidas':
      return { somenteDeletadas: true }
    case 'todas':
    default:
      return {}
  }
}
