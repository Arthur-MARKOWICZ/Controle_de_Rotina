const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isEmailValido(email: string): boolean {
  if (!email || email.trim() === '') {
    return false
  }
  return EMAIL.test(email.trim())
}
