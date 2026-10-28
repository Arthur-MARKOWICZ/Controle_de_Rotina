/**
 * Critérios compartilhados com o backend (SenhaForteValidator):
 * 8–72 caracteres; ao menos 1 minúscula, 1 maiúscula, 1 dígito e 1 especial.
 */
const SENHA_FORTE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/

export type RegraSenhaId =
  | 'tamanho'
  | 'minuscula'
  | 'maiuscula'
  | 'digito'
  | 'especial'

export type RegraSenha = {
  id: RegraSenhaId
  label: string
  ok: boolean
}

export function regrasSenha(senha: string): RegraSenha[] {
  const valor = senha ?? ''
  return [
    {
      id: 'tamanho',
      label: '8 a 72 caracteres',
      ok: valor.length >= 8 && valor.length <= 72,
    },
    {
      id: 'minuscula',
      label: 'Uma letra minúscula',
      ok: /[a-z]/.test(valor),
    },
    {
      id: 'maiuscula',
      label: 'Uma letra maiúscula',
      ok: /[A-Z]/.test(valor),
    },
    {
      id: 'digito',
      label: 'Um número',
      ok: /\d/.test(valor),
    },
    {
      id: 'especial',
      label: 'Um caractere especial',
      ok: /[^A-Za-z0-9]/.test(valor),
    },
  ]
}

export function isSenhaForte(senha: string): boolean {
  if (!senha || senha.trim() === '') {
    return false
  }
  return SENHA_FORTE.test(senha)
}
