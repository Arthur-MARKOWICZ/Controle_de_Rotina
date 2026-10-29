import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'

import { AuthProvider } from '../session/AuthContext'
import { clearAccessToken, setAccessToken } from '../session/tokenStore'
import { ProtectedRoute } from './ProtectedRoute'

function renderComRota(initial: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initial]}>
        <Routes>
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <p>Conteúdo protegido</p>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<p>Tela de login</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    clearAccessToken()
  })

  it('redireciona para login quando não há access token', () => {
    renderComRota('/')
    expect(screen.getByText('Tela de login')).toBeInTheDocument()
    expect(screen.queryByText('Conteúdo protegido')).not.toBeInTheDocument()
  })

  it('renderiza o conteúdo quando há access token', () => {
    setAccessToken('jwt-ok')
    renderComRota('/')
    expect(screen.getByText('Conteúdo protegido')).toBeInTheDocument()
  })

  it('f1_aposLimpezaDoAccessTokenRedirecionaParaLogin', () => {
    setAccessToken('jwt-ok')
    const { rerender } = renderComRota('/')
    expect(screen.getByText('Conteúdo protegido')).toBeInTheDocument()

    clearAccessToken()
    rerender(
      <AuthProvider>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <p>Conteúdo protegido</p>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<p>Tela de login</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(screen.getByText('Tela de login')).toBeInTheDocument()
    expect(screen.queryByText('Conteúdo protegido')).not.toBeInTheDocument()
  })
})
