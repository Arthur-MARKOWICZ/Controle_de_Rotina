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

  it('exibe marca e proposta de valor na tela pública', () => {
    renderLogin()
    expect(screen.getByText('Rotina')).toBeInTheDocument()
    expect(
      screen.getByText('Diga o que precisa fazer; o plano do dia sai pronto.'),
    ).toBeInTheDocument()
    expect(document.querySelector('main.auth-page')).toBeInTheDocument()
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

  it('T5: erro de credencial foca o primeiro campo inválido; inputs têm autocomplete corretos', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new authApi.ApiError(401, 'Credenciais inválidas.'))
    const user = userEvent.setup()
    renderLogin()

    const email = screen.getByLabelText(/^email$/i)
    const senha = screen.getByLabelText(/^senha$/i)
    expect(email).toHaveAttribute('autocomplete', 'email')
    expect(senha).toHaveAttribute('autocomplete', 'current-password')

    await user.type(email, 'ana@email.com')
    await user.type(senha, 'Errada@123')
    await user.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() => expect(senha).toHaveFocus())
    expect(senha).toHaveAttribute('aria-invalid', 'true')
    expect(senha).toHaveAccessibleDescription(/credenciais inválidas/i)
    expect(email).not.toHaveAttribute('aria-invalid')
    expect(senha).toBeEnabled()

    await user.type(senha, 'x')
    expect(senha).not.toHaveAttribute('aria-invalid')
  })

  it('validação local mostra erros por campo e foca o primeiro (email)', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: /entrar/i }))

    const email = screen.getByLabelText(/^email$/i)
    expect(email).toHaveFocus()
    expect(email).toHaveAccessibleDescription('Informe um email válido.')
    expect(screen.getByLabelText(/^senha$/i)).toHaveAccessibleDescription('Informe a senha.')
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('mostrar/ocultar senha usa botão com nome acessível', async () => {
    const user = userEvent.setup()
    renderLogin()

    const senha = screen.getByLabelText(/^senha$/i)
    await user.click(screen.getByRole('button', { name: 'Mostrar senha' }))
    expect(senha).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar senha' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
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
