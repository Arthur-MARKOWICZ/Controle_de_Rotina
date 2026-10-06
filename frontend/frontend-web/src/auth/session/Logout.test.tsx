import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { HomePage } from '../../pages/HomePage'
import * as authApi from '../api/authApi'
import { AuthProvider } from '../session/AuthContext'
import { clearAccessToken, getAccessToken, setAccessToken } from '../session/tokenStore'
import { ProtectedRoute } from '../components/ProtectedRoute'

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

describe('Logout', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.mocked(authApi.logout).mockReset()
    vi.mocked(authApi.logout).mockResolvedValue(undefined)
  })

  it('chama logout, limpa access token e volta para login', async () => {
    setAccessToken('jwt-ok')
    const user = userEvent.setup()

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <HomePage />
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<p>Tela de login</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.click(screen.getByRole('button', { name: /sair/i }))

    await waitFor(() => {
      expect(screen.getByText('Tela de login')).toBeInTheDocument()
    })
    expect(authApi.logout).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
  })
})
