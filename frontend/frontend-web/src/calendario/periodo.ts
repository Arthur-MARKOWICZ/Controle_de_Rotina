import {
  adicionarDiasIso,
  fimSemana,
  formatarDataIso,
  inicioSemana,
  parseDataIsoLocal,
} from '../capacidade/semana'

export type VisaoCalendario = 'semana' | 'mes'
export type LenteCalendario = 'plano' | 'prazos'

export type IntervaloCalendario = { dataDe: string; dataAte: string }

export type CelulaMes = {
  data: string | null
  foraDoMes: boolean
}

/** Intervalo da semana (seg→dom) que contém `refIso`. */
export function intervaloSemana(refIso: string): IntervaloCalendario {
  const ref = parseDataIsoLocal(refIso)
  return { dataDe: inicioSemana(ref), dataAte: fimSemana(ref) }
}

/** Intervalo do mês civil de `refIso` (1º → último dia). */
export function intervaloMes(refIso: string): IntervaloCalendario {
  const ref = parseDataIsoLocal(refIso)
  const inicio = new Date(ref.getFullYear(), ref.getMonth(), 1, 12, 0, 0)
  const fim = new Date(ref.getFullYear(), ref.getMonth() + 1, 0, 12, 0, 0)
  return { dataDe: formatarDataIso(inicio), dataAte: formatarDataIso(fim) }
}

export function intervaloPorVisao(visao: VisaoCalendario, refIso: string): IntervaloCalendario {
  return visao === 'semana' ? intervaloSemana(refIso) : intervaloMes(refIso)
}

/** Âncora após navegar: semana ±7 dias; mês ±1. */
export function navegarPeriodo(
  visao: VisaoCalendario,
  refIso: string,
  direcao: -1 | 1,
): string {
  if (visao === 'semana') {
    return adicionarDiasIso(refIso, direcao * 7)
  }
  const ref = parseDataIsoLocal(refIso)
  const alvo = new Date(ref.getFullYear(), ref.getMonth() + direcao, 1, 12, 0, 0)
  return formatarDataIso(alvo)
}

/** Grade do mês com padding seg→dom; dias fora do mês têm `data` null. */
export function celulasGradeMes(refIso: string): CelulaMes[] {
  const { dataDe, dataAte } = intervaloMes(refIso)
  const primeiro = parseDataIsoLocal(dataDe)
  const diaSemana = primeiro.getDay()
  const padInicio = diaSemana === 0 ? 6 : diaSemana - 1

  const celulas: CelulaMes[] = []
  for (let i = 0; i < padInicio; i++) {
    celulas.push({ data: null, foraDoMes: true })
  }

  let cursor = dataDe
  while (cursor <= dataAte) {
    celulas.push({ data: cursor, foraDoMes: false })
    cursor = adicionarDiasIso(cursor, 1)
  }

  while (celulas.length % 7 !== 0) {
    celulas.push({ data: null, foraDoMes: true })
  }

  return celulas
}

export function labelPeriodo(visao: VisaoCalendario, refIso: string): string {
  if (visao === 'semana') {
    const { dataDe, dataAte } = intervaloSemana(refIso)
    const de = parseDataIsoLocal(dataDe)
    const ate = parseDataIsoLocal(dataAte)
    const opts: Intl.DateTimeFormatOptions = { day: '2-digit', month: 'short' }
    return `${de.toLocaleDateString('pt-BR', opts)} – ${ate.toLocaleDateString('pt-BR', opts)}`
  }
  const ref = parseDataIsoLocal(refIso)
  const raw = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(ref)
  return raw.charAt(0).toUpperCase() + raw.slice(1)
}

export function diasDaSemanaIso(segundaIso: string): string[] {
  return Array.from({ length: 7 }, (_, i) => adicionarDiasIso(segundaIso, i))
}

const DIAS_CURTOS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

export function labelDiaCurto(indiceSegADom: number): string {
  return DIAS_CURTOS[indiceSegADom] ?? ''
}

export function parseVisao(valor: string | null): VisaoCalendario {
  return valor === 'mes' ? 'mes' : 'semana'
}

export function parseLente(valor: string | null): LenteCalendario {
  return valor === 'prazos' ? 'prazos' : 'plano'
}
