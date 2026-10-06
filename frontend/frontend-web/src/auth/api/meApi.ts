import { ApiError, type UsuarioResponse } from './authApi'
import { apiFetch } from '../session/apiClient'

export type AtualizarMeRequest = {
  senhaAtual: string
  nome?: string
  email?: string
  novaSenha?: string
}

export async function buscarMe(): Promise<UsuarioResponse> {
  const response = await apiFetch('/me')

  if (!response.ok) {
    throw new ApiError(response.status, 'Não foi possível carregar o perfil.')
  }

  return (await response.json()) as UsuarioResponse
}

export async function atualizarMe(body: AtualizarMeRequest): Promise<UsuarioResponse> {
  const response = await apiFetch('/me', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const message =
      response.status === 401
        ? 'Senha atual incorreta.'
        : response.status === 409
          ? 'Este email já está cadastrado.'
          : 'Não foi possível atualizar o perfil.'
    throw new ApiError(response.status, message)
  }

  return (await response.json()) as UsuarioResponse
}
