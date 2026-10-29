import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ProtectedRoute } from '../components/ProtectedRoute'
import * as authApi from '../api/authApi'
import * as meApi from '../api/meApi'
import { AuthProvider } from './AuthContext'
import { clearAccessToken, getAccessToken, setAccessToken } from '../session/tokenStore'
import { AppShell } from '../../layout/AppShell'

vi.mock('../api/authApi', () => ({
  logout: vi.fn(),
  login: vi.fn(),
  cadastrarUsuario: vi.fn(),
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

vi.mock('../api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
}))

describe('Logout', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.mocked(authApi.logout).mockReset()
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: '1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
  })

  it('chama logout, limpa access token e volta para login', async () => {
    setAccessToken('jwt-ok')
    const user = userEvent.setup()

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<p>Conteúdo protegido</p>} />
            </Route>
            <Route path="/login" element={<p>Tela de login</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.click(screen.getAllByRole('button', { name: /sair/i })[0])

    await waitFor(() => {
      expect(screen.getByText('Tela de login')).toBeInTheDocument()
    })
    expect(authApi.logout).toHaveBeenCalledOnce()
    expect(authApi.logout).toHaveBeenCalledWith('jwt-ok')
    expect(getAccessToken()).toBeNull()
  })
})
