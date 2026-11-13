import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ProtectedRoute } from '../auth/components/ProtectedRoute'
import { AuthProvider } from '../auth/session/AuthContext'
import { clearAccessToken, getAccessToken, setAccessToken } from '../auth/session/tokenStore'
import { itemDecidir, paginaDecidir } from '../test/revisaoFixtures'
import { RevisaoPage } from './RevisaoPage'

/** Sem mocks de API: exercita o refresh real do apiClient no meio da revisão. */
function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const item = itemDecidir({ id: 'd-1', titulo: 'Assinar academia' })
const RESUMO = {
  periodo: { escopo: 'SEMANA', inicio: '2026-11-09', fim: '2026-11-15' },
  tarefasFinalizadas: 0,
  blocosConcluidos: 0,
  minutosTrabalhados: 0,
  xpGanho: 0,
}

describe('RevisaoPage — sessão expirada', () => {
  let promover = 0
  let refreshOk = true
  let decidirAbertos = [item]
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    promover = 0
    refreshOk = true
    decidirAbertos = [item]
    setAccessToken('token-velho')
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      const auth = new Headers(init?.headers).get('Authorization')
      if (url === '/auth/refresh') {
        return refreshOk
          ? json(200, { accessToken: 'token-novo' })
          : json(401, { title: 'Não autorizado' })
      }
      if (url.startsWith('/historico/semana')) return json(200, RESUMO)
      if (url.startsWith('/tarefas')) {
        return json(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 })
      }
      if (url.endsWith('/promover')) {
        if (auth === 'Bearer token-velho') return json(401, { title: 'Não autorizado' })
        promover += 1
        decidirAbertos = []
        return json(200, { ...item, tarefaId: 't-1' })
      }
      if (url.startsWith('/decidir')) return json(200, paginaDecidir(decidirAbertos))
      return json(404, { title: 'Não encontrado' })
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearAccessToken()
  })

  it('renova a sessão e promove o item uma única vez', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <RevisaoPage hojeIso="2026-11-13" />
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: /^pular$/i }))
    await user.click(screen.getByRole('button', { name: /^pular$/i }))
    await user.click(await screen.findByRole('button', { name: /promover/i }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /^promover$/i }))

    await waitFor(() => expect(screen.getByText(/fila decidir vazia/i)).toBeInTheDocument())
    expect(promover).toBe(1)
    expect(getAccessToken()).toBe('token-novo')
    expect(fetchMock.mock.calls.filter(([u]) => u === '/auth/refresh')).toHaveLength(1)
  })
  it('leva ao login sem promover quando o refresh também falha', async () => {
    const user = userEvent.setup()
    refreshOk = false
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/revisao']}>
          <Routes>
            <Route
              path="/revisao"
              element={
                <ProtectedRoute>
                  <RevisaoPage hojeIso="2026-11-13" />
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<p>Tela de login</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.click(screen.getByRole('button', { name: /^pular$/i }))
    await user.click(screen.getByRole('button', { name: /^pular$/i }))
    await user.click(await screen.findByRole('button', { name: /promover/i }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /^promover$/i }))

    expect(await screen.findByText('Tela de login')).toBeInTheDocument()
    expect(promover).toBe(0)
    expect(getAccessToken()).toBeNull()
    expect(fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/promover'))).toHaveLength(1)
  })
})
