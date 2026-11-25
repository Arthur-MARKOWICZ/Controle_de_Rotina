import { ApiError } from '../api/authApi'
import {
  clearAccessToken,
  encerrarSessaoContaExcluida,
  getAccessToken,
  setAccessToken,
} from './tokenStore'

type AccessTokenResponse = { accessToken: string }

function mensagemHtmlEnganoso(status: number, html: string, url: string): string {
  const lower = html.toLowerCase()
  const alvo = url ? ` (${url})` : ''
  if (status === 502 || status === 503 || status === 504 || lower.includes('bad gateway')) {
    return `Backend indisponível (gateway)${alvo}. Espere o container do backend subir e recarregue.`
  }
  if (lower.includes('<div id="root"') || lower.includes('controle de rotina')) {
    return (
      `Resposta HTML da SPA em vez de JSON${alvo}. ` +
      'Limpe o cache do site (Ctrl+Shift+R) ou abra em aba anônima. ' +
      'Se persistir: docker compose up -d --build frontend'
    )
  }
  return `A API devolveu HTML (HTTP ${status})${alvo}. Aguarde o backend e recarregue sem cache.`
}

/** Lê o body como JSON; se vier HTML, explica a causa (cache/proxy/backend). */
export async function readApiJson<T>(response: Response): Promise<T> {
  const text = await response.text()
  const inicio = text.trimStart().slice(0, 15).toLowerCase()
  if (inicio.startsWith('<!doctype') || inicio.startsWith('<html')) {
    throw new ApiError(
      response.status,
      mensagemHtmlEnganoso(response.status, text, response.url || ''),
    )
  }
  if (!text) {
    if (response.status === 401) {
      throw new ApiError(response.status, 'Não autenticado. Faça login novamente.')
    }
    throw new ApiError(response.status, 'Resposta vazia da API.')
  }
  try {
    return JSON.parse(text) as T
  } catch {
    throw new ApiError(response.status, 'Resposta inválida da API (não é JSON).')
  }
}

/** `title` do ProblemDetail que a API manda quando o JWT é válido mas a conta foi excluída. */
export const TITULO_CONTA_EXCLUIDA = 'Conta excluída'

/** `detail` do 401 de senha errada (form do Perfil): a sessão é válida, não há o que renovar. */
const DETALHE_CREDENCIAIS_INVALIDAS = 'Credenciais inválidas.'

type Problema401 = { title?: string; detail?: string }

/** Lê o ProblemDetail de um 401 sem consumir o body original; corpo vazio/HTML/inválido → {}. */
async function lerProblema401(response: Response): Promise<Problema401> {
  try {
    const body: unknown = JSON.parse(await response.clone().text())
    return body && typeof body === 'object' ? (body as Problema401) : {}
  } catch {
    return {}
  }
}

let refreshPromise: Promise<boolean> | null = null

async function tentarRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch('/auth/refresh', {
          method: 'POST',
          credentials: 'include',
          cache: 'no-store',
          headers: { Accept: 'application/json' },
        })
        if (!response.ok) {
          clearAccessToken()
          return false
        }
        const body = await readApiJson<AccessTokenResponse>(response)
        if (!body.accessToken) {
          clearAccessToken()
          return false
        }
        setAccessToken(body.accessToken)
        return true
      } catch {
        clearAccessToken()
        return false
      } finally {
        refreshPromise = null
      }
    })()
  }
  return refreshPromise
}

function buildHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}

export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: buildHeaders(init),
  })

  if (response.status !== 401) {
    return response
  }

  const problema = await lerProblema401(response)
  if (problema.title === TITULO_CONTA_EXCLUIDA) {
    encerrarSessaoContaExcluida()
    return response
  }
  if (problema.detail === DETALHE_CREDENCIAIS_INVALIDAS) {
    return response
  }

  const refreshed = await tentarRefresh()
  if (!refreshed) {
    return response
  }

  return fetch(input, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: buildHeaders(init),
  })
}
