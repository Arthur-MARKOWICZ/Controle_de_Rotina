import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '../api/authApi'
import { AuthProvider, useAuth } from './AuthContext'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

vi.mock('../api/authApi', () => ({
  logout: vi.fn(),
}))

function Probe() {
  const { isAuthenticated, loginWithToken, logout } = useAuth()
  return (
    <div>
      <p>{isAuthenticated ? 'autenticado' : 'anonimo'}</p>
      <button type="button" onClick={() => loginWithToken('jwt-ok')}>
        Entrar
      </button>
      <button type="button" onClick={() => void logout().catch(() => undefined)}>
        Sair
      </button>
    </div>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.mocked(authApi.logout).mockReset().mockResolvedValue(undefined)
  })

  it('começa anônimo quando não há token', () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByText('anonimo')).toBeInTheDocument()
  })

  it('fica autenticado ao guardar o token', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Entrar' }))
    expect(screen.getByText('autenticado')).toBeInTheDocument()
    expect(getAccessToken()).toBe('jwt-ok')
  })

  it('lê token já persistido no store', () => {
    setAccessToken('jwt-existente')
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByText('autenticado')).toBeInTheDocument()
  })

  it('logout limpa o token mesmo se a API falhar', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('rede'))
    setAccessToken('jwt-ok')
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sair' }))
    await waitFor(() => expect(screen.getByText('anonimo')).toBeInTheDocument())
    expect(getAccessToken()).toBeNull()
  })

  it('logout chama a API com o token atual', async () => {
    setAccessToken('jwt-ok')
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sair' }))
    await waitFor(() => expect(authApi.logout).toHaveBeenCalledWith('jwt-ok'))
    expect(getAccessToken()).toBeNull()
  })

  it('useAuth fora do provider lança', () => {
    expect(() => render(<Probe />)).toThrow(/dentro de AuthProvider/)
  })

  it('login com string vazia permanece anônimo', async () => {
    function Vazio() {
      const { isAuthenticated, loginWithToken } = useAuth()
      return (
        <div>
          <p>{isAuthenticated ? 'autenticado' : 'anonimo'}</p>
          <button type="button" onClick={() => loginWithToken('')}>
            Entrar vazio
          </button>
        </div>
      )
    }
    render(
      <AuthProvider>
        <Vazio />
      </AuthProvider>,
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'Entrar vazio' }))
    expect(screen.getByText('anonimo')).toBeInTheDocument()
    expect(getAccessToken()).toBe('')
  })
})
