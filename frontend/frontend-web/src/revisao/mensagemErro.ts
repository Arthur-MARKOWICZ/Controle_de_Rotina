import { ApiError } from '../auth/api/authApi'

export function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}
