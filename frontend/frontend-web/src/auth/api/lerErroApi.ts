import { ApiError } from './authApi'
import { readApiJson } from '../session/apiClient'

type ProblemDetailBody = {
  detail?: string
  title?: string
  errors?: Array<{ property?: string; message?: string }>
}

/** Lê ProblemDetail (RFC 7807) e monta ApiError com mensagem útil para toasts. */
export async function lerErroApi(response: Response, fallback: string): Promise<ApiError> {
  try {
    const body = await readApiJson<ProblemDetailBody>(response)
    const deCampos = mensagemDeErrors(body.errors)
    const mensagem = body.detail || body.title || deCampos || fallback
    return new ApiError(response.status, mensagem)
  } catch (error) {
    if (error instanceof ApiError) {
      return error
    }
    return new ApiError(response.status, fallback)
  }
}

function mensagemDeErrors(
  errors: ProblemDetailBody['errors'],
): string | undefined {
  if (!errors?.length) {
    return undefined
  }
  const partes = errors
    .map((e) => e.message)
    .filter((m): m is string => Boolean(m && m.trim()))
  return partes.length > 0 ? partes.join(' ') : undefined
}
