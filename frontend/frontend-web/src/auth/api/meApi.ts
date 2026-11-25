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

export type ArquivoExportado = { blob: Blob; nomeArquivo: string }

const NOME_EXPORT_PADRAO = 'controle-rotina-export.json'

function nomeDoContentDisposition(header: string | null): string {
  const match = header?.match(/filename="?([^";]+)"?/i)
  return match?.[1] ?? NOME_EXPORT_PADRAO
}

/** `GET /me/export`: todos os dados do titular como arquivo JSON (LGPD). */
export async function exportarDados(): Promise<ArquivoExportado> {
  const response = await apiFetch('/me/export')

  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível exportar seus dados.')
  }

  const tipo = response.headers.get('Content-Type') ?? ''
  if (!tipo.includes('json')) {
    throw await lerErroApi(response, 'Não foi possível exportar seus dados.')
  }

  return {
    blob: await response.blob(),
    nomeArquivo: nomeDoContentDisposition(response.headers.get('Content-Disposition')),
  }
}

/** `DELETE /me`: exclui a conta e tudo dela. Irreversível. */
export async function excluirConta(senhaAtual: string): Promise<void> {
  const response = await apiFetch('/me', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ senhaAtual }),
  })

  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível excluir a conta.')
  }
}
