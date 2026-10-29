import { type UsuarioResponse } from './authApi'
import { lerErroApi } from './lerErroApi'
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
    throw await lerErroApi(response, 'Não foi possível carregar o perfil.')
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
    throw await lerErroApi(response, 'Não foi possível atualizar o perfil.')
  }

  return (await response.json()) as UsuarioResponse
}
