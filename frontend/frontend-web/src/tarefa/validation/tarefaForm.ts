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
  duracaoHoras: number | ''
  status?: StatusTarefa | ''
}

type ValidarOpcoes = {
  modo?: 'criar' | 'editar'
  agora?: Date
}

export function validarTarefaForm(
  values: TarefaFormValues,
  opcoes: ValidarOpcoes = {},
): string | null {
  const modo = opcoes.modo ?? 'criar'
  const agora = opcoes.agora ?? new Date()

  const titulo = values.titulo.trim()
  if (!titulo) {
    return 'Informe o título.'
  }
  if (titulo.length > 120) {
    return 'O título deve ter no máximo 120 caracteres.'
  }

  const descricao = values.descricao.trim()
  if (!descricao) {
    return 'Informe a descrição.'
  }
  if (descricao.length > 2000) {
    return 'A descrição deve ter no máximo 2000 caracteres.'
  }

  if (!values.prioridade) {
    return 'Selecione a prioridade.'
  }

  if (!values.prazo) {
    return 'Informe o prazo.'
  }

  const prazoDate = new Date(values.prazo)
  if (Number.isNaN(prazoDate.getTime())) {
    return 'Informe um prazo válido.'
  }
  if (prazoDate.getTime() < agora.getTime()) {
    return 'O prazo não pode estar no passado.'
  }

  if (values.duracaoHoras === '' || values.duracaoHoras == null) {
    return 'Informe a duração em horas.'
  }
  if (!Number.isInteger(values.duracaoHoras) || values.duracaoHoras < 1) {
    return 'A duração deve ser um número inteiro de horas, mínimo 1.'
  }

  if (modo === 'editar') {
    if (!values.status) {
      return 'Selecione o status.'
    }
    if (values.status === 'ATRASADA' && prazoDate.getTime() >= agora.getTime()) {
      return 'Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.'
    }
  }

  return null
}
