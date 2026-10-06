export type CadastroRequest = {
  nome: string
  email: string
  senha: string
}

export type LoginRequest = {
  email: string
  senha: string
}

export type AccessTokenResponse = {
  accessToken: string
}

export type UsuarioResponse = {
  id: string
  nome: string
  email: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function cadastrarUsuario(body: CadastroRequest): Promise<UsuarioResponse> {
  const response = await fetch('/usuarios', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    throw new ApiError(response.status, 'Não foi possível concluir o cadastro.')
  }

  return (await response.json()) as UsuarioResponse
}

export async function login(body: LoginRequest): Promise<AccessTokenResponse> {
  const response = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    throw new ApiError(response.status, 'Credenciais inválidas.')
  }

  return (await response.json()) as AccessTokenResponse
}

export async function logout(): Promise<void> {
  await fetch('/auth/logout', {
    method: 'POST',
    credentials: 'include',
  })
}
