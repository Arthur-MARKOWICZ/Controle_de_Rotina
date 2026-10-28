import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '../api/authApi'
import { AuthProvider } from '../session/AuthContext'
import { clearAccessToken, getAccessToken } from '../session/tokenStore'
import { LoginPage } from './LoginPage'

vi.mock('../api/authApi', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Home autenticada</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.mocked(authApi.login).mockReset()
  })

  it('exibe proposta de valor na tela pública', () => {
    renderLogin()
    expect(
      screen.getByText(/diga o que precisa fazer; o sistema monta o plano/i),
    ).toBeInTheDocument()
  })

  it('mostra erro genérico quando as credenciais são inválidas', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new authApi.ApiError(401, 'Credenciais inválidas.'))
    const user = userEvent.setup()
    renderLogin()

    await user.type(screen.getByLabelText(/^email$/i), 'ana@email.com')
    await user.type(screen.getByLabelText(/^senha$/i), 'Senha@123')
    await user.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/credenciais inválidas/i)
    expect(getAccessToken()).toBeNull()
  })

  it('guarda access token e redireciona após login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'jwt-ok' })
    const user = userEvent.setup()
    renderLogin()

    await user.type(screen.getByLabelText(/^email$/i), 'ana@email.com')
    await user.type(screen.getByLabelText(/^senha$/i), 'Senha@123')
    await user.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() => {
      expect(screen.getByText('Home autenticada')).toBeInTheDocument()
    })
    expect(getAccessToken()).toBe('jwt-ok')
  })
})
