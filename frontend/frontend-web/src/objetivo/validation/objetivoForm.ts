export const TITULO_MAX = 80
export const DESCRICAO_MAX = 500

export type ObjetivoFormValues = {
  titulo: string
  descricao: string
  /** `yyyy-MM-dd` ou vazio (sem prazo). */
  prazo: string
}

export type ObjetivoFormErros = {
  titulo?: string
  descricao?: string
  prazo?: string
}

export const OBJETIVO_FORM_VAZIO: ObjetivoFormValues = {
  titulo: '',
  descricao: '',
  prazo: '',
}

type Contexto = {
  /** Hoje em BRT (`yyyy-MM-dd`). */
  hojeIso: string
  /** Prazo salvo no objetivo em edição; reenviá-lo mesmo vencido é aceito pela API. */
  prazoOriginal?: string | null
}

export function validarObjetivoForm(
  values: ObjetivoFormValues,
  { hojeIso, prazoOriginal = null }: Contexto,
): ObjetivoFormErros {
  const erros: ObjetivoFormErros = {}

  const titulo = values.titulo.trim()
  if (!titulo) {
    erros.titulo = 'Informe o título do objetivo.'
  } else if (titulo.length > TITULO_MAX) {
    erros.titulo = `O título deve ter no máximo ${TITULO_MAX} caracteres.`
  }

  if (values.descricao.trim().length > DESCRICAO_MAX) {
    erros.descricao = `A descrição deve ter no máximo ${DESCRICAO_MAX} caracteres.`
  }

  const prazo = values.prazo
  if (prazo && !/^\d{4}-\d{2}-\d{2}$/.test(prazo)) {
    erros.prazo = 'Informe uma data válida.'
  } else if (prazo && prazo !== prazoOriginal && prazo < hojeIso) {
    erros.prazo = 'O prazo não pode estar no passado.'
  }

  return erros
}

export function primeiroErroObjetivo(erros: ObjetivoFormErros): string | null {
  return erros.titulo ?? erros.descricao ?? erros.prazo ?? null
}
