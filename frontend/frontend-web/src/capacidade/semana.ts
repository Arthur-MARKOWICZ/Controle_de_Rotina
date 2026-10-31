import type { DiaSemana } from './api/capacidadeSemanalApi'

const DIAS_ORDEM: DiaSemana[] = [
  'SEGUNDA',
  'TERCA',
  'QUARTA',
  'QUINTA',
  'SEXTA',
  'SABADO',
  'DOMINGO',
]

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/** Formata Date local como YYYY-MM-DD. */
export function formatarDataIso(data: Date): string {
  return `${data.getFullYear()}-${pad(data.getMonth() + 1)}-${pad(data.getDate())}`
}

/** Interpreta YYYY-MM-DD como data local (meio-dia evita DST edge). */
export function parseDataIsoLocal(iso: string): Date {
  const [ano, mes, dia] = iso.split('-').map(Number)
  return new Date(ano, mes - 1, dia, 12, 0, 0)
}

/** Segunda-feira da semana que contém `ref` (ISO date). */
export function inicioSemana(ref: Date = new Date()): string {
  const d = new Date(ref.getFullYear(), ref.getMonth(), ref.getDate(), 12, 0, 0)
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return formatarDataIso(d)
}

/** Domingo da semana que contém `ref` (ISO date). */
export function fimSemana(ref: Date = new Date()): string {
  const inicio = parseDataIsoLocal(inicioSemana(ref))
  inicio.setDate(inicio.getDate() + 6)
  return formatarDataIso(inicio)
}

export function adicionarDiasIso(iso: string, dias: number): string {
  const d = parseDataIsoLocal(iso)
  d.setDate(d.getDate() + dias)
  return formatarDataIso(d)
}

/** Mapa DiaSemana → data ISO da semana cujo início é `segundaIso`. */
export function datasDaSemana(segundaIso: string): Record<DiaSemana, string> {
  const mapa = {} as Record<DiaSemana, string>
  for (let i = 0; i < DIAS_ORDEM.length; i++) {
    mapa[DIAS_ORDEM[i]] = adicionarDiasIso(segundaIso, i)
  }
  return mapa
}

function mesCurtoPt(data: Date): string {
  return data
    .toLocaleDateString('pt-BR', { month: 'short' })
    .replace(/\./g, '')
    .trim()
    .toLowerCase()
}

/** Ex.: "5–11 out" ou "28 set – 4 out" quando cruza mês. */
export function formatarIntervaloSemana(segundaIso: string): string {
  const domingo = adicionarDiasIso(segundaIso, 6)
  const de = parseDataIsoLocal(segundaIso)
  const ate = parseDataIsoLocal(domingo)
  if (de.getMonth() === ate.getMonth() && de.getFullYear() === ate.getFullYear()) {
    return `${de.getDate()}–${ate.getDate()} ${mesCurtoPt(ate)}`
  }
  return `${de.getDate()} ${mesCurtoPt(de)} – ${ate.getDate()} ${mesCurtoPt(ate)}`
}
