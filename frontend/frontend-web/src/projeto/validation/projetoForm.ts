export type ProjetoFormValues = {
  nome: string
  descricao: string
}

export const PROJETO_FORM_VAZIO: ProjetoFormValues = {
  nome: '',
  descricao: '',
}

export function validarProjetoForm(values: ProjetoFormValues): string | null {
  const nome = values.nome.trim()
  if (!nome) {
    return 'Informe o nome do projeto.'
  }
  if (nome.length > 80) {
    return 'O nome deve ter no máximo 80 caracteres.'
  }
  if (values.descricao.length > 500) {
    return 'A descrição deve ter no máximo 500 caracteres.'
  }
  return null
}
