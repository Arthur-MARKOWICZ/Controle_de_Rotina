import type { DiaSemana } from '../../capacidade/api/capacidadeSemanalApi'

export const DIAS_SEMANA_ORDEM: { id: DiaSemana; label: string; curto: string }[] = [
  { id: 'SEGUNDA', label: 'Segunda', curto: 'Seg' },
  { id: 'TERCA', label: 'Terça', curto: 'Ter' },
  { id: 'QUARTA', label: 'Quarta', curto: 'Qua' },
  { id: 'QUINTA', label: 'Quinta', curto: 'Qui' },
  { id: 'SEXTA', label: 'Sexta', curto: 'Sex' },
  { id: 'SABADO', label: 'Sábado', curto: 'Sáb' },
  { id: 'DOMINGO', label: 'Domingo', curto: 'Dom' },
]

export type HabitoFormValues = {
  titulo: string
  descricao: string
  duracaoMinutos: number | ''
  diasSemana: DiaSemana[]
  horarioPreferencial: string
}

export type HabitoFormErros = {
  titulo?: string
  descricao?: string
  duracaoMinutos?: string
  diasSemana?: string
  horarioPreferencial?: string
}

export const HABITO_FORM_VAZIO: HabitoFormValues = {
  titulo: '',
  descricao: '',
  duracaoMinutos: 30,
  diasSemana: [],
  horarioPreferencial: '',
}

export function rotuloDiasSemana(dias: DiaSemana[]): string {
  if (dias.length === 0) {
    return ''
  }
  const set = new Set(dias)
  return DIAS_SEMANA_ORDEM.filter((d) => set.has(d.id))
    .map((d) => d.curto)
    .join(', ')
}

export function validarHabitoForm(values: HabitoFormValues): HabitoFormErros {
  const erros: HabitoFormErros = {}

  const titulo = values.titulo.trim()
  if (!titulo) {
    erros.titulo = 'Informe o título.'
  } else if (titulo.length > 120) {
    erros.titulo = 'O título deve ter no máximo 120 caracteres.'
  }

  const descricao = values.descricao.trim()
  if (descricao.length > 2000) {
    erros.descricao = 'A descrição deve ter no máximo 2000 caracteres.'
  }

  if (values.duracaoMinutos === '' || values.duracaoMinutos == null) {
    erros.duracaoMinutos = 'Informe a duração em minutos.'
  } else if (
    !Number.isInteger(values.duracaoMinutos) ||
    values.duracaoMinutos < 15 ||
    values.duracaoMinutos % 15 !== 0
  ) {
    erros.duracaoMinutos = 'A duração deve ser no mínimo 15 minutos e múltiplo de 15.'
  }

  if (values.diasSemana.length < 1) {
    erros.diasSemana = 'Selecione pelo menos um dia da semana.'
  }

  if (values.horarioPreferencial) {
    if (!/^\d{2}:\d{2}$/.test(values.horarioPreferencial)) {
      erros.horarioPreferencial = 'Informe um horário válido.'
    }
  }

  return erros
}

export function primeiroErroHabito(erros: HabitoFormErros): string | null {
  return (
    erros.titulo ??
    erros.descricao ??
    erros.duracaoMinutos ??
    erros.diasSemana ??
    erros.horarioPreferencial ??
    null
  )
}
