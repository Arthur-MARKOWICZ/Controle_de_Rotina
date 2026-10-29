export type Prioridade = 'ALTA' | 'MEDIA' | 'BAIXA'

export type StatusTarefa =
  | 'PENDENTE'
  | 'EM_PROGRESSO'
  | 'FINALIZADA'
  | 'ATRASADA'
  | 'CANCELADA'

export type TarefaFormValues = {
  titulo: string
  descricao: string
  prioridade: Prioridade | ''
  prazo: string
  duracaoMinutos: number | ''
  status?: StatusTarefa | ''
}

export type TarefaFormErros = {
  titulo?: string
  descricao?: string
  prioridade?: string
  prazo?: string
  duracaoMinutos?: string
  status?: string
}

type ValidarOpcoes = {
  modo?: 'criar' | 'editar'
  agora?: Date
}

export function validarTarefaFormCampos(
  values: TarefaFormValues,
  opcoes: ValidarOpcoes = {},
): TarefaFormErros {
  const modo = opcoes.modo ?? 'criar'
  const agora = opcoes.agora ?? new Date()
  const erros: TarefaFormErros = {}

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

  if (!values.prioridade) {
    erros.prioridade = 'Selecione a prioridade.'
  }

  if (values.prazo) {
    const prazoDate = new Date(values.prazo)
    if (Number.isNaN(prazoDate.getTime())) {
      erros.prazo = 'Informe um prazo válido.'
    } else if (prazoDate.getTime() < agora.getTime()) {
      erros.prazo = 'O prazo não pode estar no passado.'
    }
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

  if (modo === 'editar') {
    if (!values.status) {
      erros.status = 'Selecione o status.'
    } else if (values.status === 'ATRASADA') {
      const prazoDate = values.prazo ? new Date(values.prazo) : null
      if (prazoDate && !Number.isNaN(prazoDate.getTime()) && prazoDate.getTime() >= agora.getTime()) {
        erros.status =
          'Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.'
      }
    }
  }

  return erros
}

export function primeiroErroForm(erros: TarefaFormErros): string | null {
  for (const mensagem of Object.values(erros)) {
    if (mensagem) {
      return mensagem
    }
  }
  return null
}

/** Compat: primeira mensagem de erro ou null se válido. */
export function validarTarefaForm(
  values: TarefaFormValues,
  opcoes: ValidarOpcoes = {},
): string | null {
  return primeiroErroForm(validarTarefaFormCampos(values, opcoes))
}
