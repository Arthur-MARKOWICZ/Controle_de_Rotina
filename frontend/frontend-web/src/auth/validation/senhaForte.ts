/**
 * Critérios compartilhados com o backend (SenhaForteValidator):
 * 8–72 caracteres; ao menos 1 minúscula, 1 maiúscula, 1 dígito e 1 especial.
 */
const SENHA_FORTE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/

export function isSenhaForte(senha: string): boolean {
  if (!senha || senha.trim() === '') {
    return false
  }
  return SENHA_FORTE.test(senha)
}
