import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from '../auth/api/meApi'
import { AppShell } from './AppShell'
import { DocumentTitle } from './DocumentTitle'

vi.mock('../auth/session/AuthContext', () => ({
  useAuth: () => ({ logout: vi.fn() }),
}))

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
}))

vi.mock('../tarefa/api/tarefaApi', () => ({
  criarTarefa: vi.fn(),
}))

function renderShell(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <DocumentTitle />
      <Routes>
        <Route element={<AppShell />}>
          <Route
            path="/"
            element={
              <div>
                <p>página hoje</p>
                <label>
                  Título
                  <input aria-label="Campo título" />
                </label>
              </div>
            }
          />
          <Route path="/tarefas" element={<p>página tarefas</p>} />
          <Route path="/capacidade" element={<p>página capacidade</p>} />
          <Route path="/historico" element={<p>página histórico</p>} />
          <Route path="/perfil" element={<p>página perfil</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppShell', () => {
  beforeEach(() => {
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 12,
      nivel: 2,
      xpNoNivel: 2,
      xpParaProximoNivel: 20,
    })
    document.title = ''
  })

  it('renderiza links Hoje, Tarefas, Capacidade e Histórico', () => {
    renderShell('/')

    expect(screen.getAllByRole('link', { name: 'Hoje' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Tarefas' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Capacidade' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Histórico' }).length).toBeGreaterThan(0)
    expect(screen.getByText('página hoje')).toBeInTheDocument()
  })

  it('marca a rota ativa com aria-current="page"', () => {
    renderShell('/tarefas')

    const tarefas = screen.getAllByRole('link', { name: 'Tarefas' })
    expect(tarefas.every((link) => link.getAttribute('aria-current') === 'page')).toBe(true)

    const hoje = screen.getAllByRole('link', { name: 'Hoje' })
    expect(hoje.every((link) => link.getAttribute('aria-current') !== 'page')).toBe(true)
  })

  it('expõe Perfil e Sair', () => {
    renderShell('/')

    expect(screen.getAllByRole('link', { name: 'Perfil' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: /sair/i }).length).toBeGreaterThan(0)
  })

  it('T6: shell reflete nível e barra a partir de /me', async () => {
    renderShell('/')

    expect(await screen.findByLabelText('Nível 2')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: /2 de 20 XP/i })).toBeInTheDocument()
    expect(screen.getByText('2 / 20 XP')).toBeInTheDocument()
    await waitFor(() => {
      expect(meApi.buscarMe).toHaveBeenCalled()
    })
  })

  it('T1: com foco em input, atalho N não abre quick add', async () => {
    const user = userEvent.setup()
    renderShell('/')

    await user.click(screen.getByLabelText('Campo título'))
    await user.keyboard('n')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('T2: sequência G H navega para Hoje (fora de campo)', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    expect(screen.getByText('página tarefas')).toBeInTheDocument()

    await user.keyboard('gh')

    await waitFor(() => {
      expect(screen.getByText('página hoje')).toBeInTheDocument()
    })
  })

  it('T3: ? abre dialog de ajuda listando atalhos', async () => {
    const user = userEvent.setup()
    renderShell('/')

    await user.keyboard('?')

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent(/atalhos de teclado/i)
    expect(dialog).toHaveTextContent(/nova tarefa/i)
    expect(dialog).toHaveTextContent(/G H/i)
    expect(dialog).toHaveTextContent(/Ir para Hoje/i)
  })

  it('T4: define document.title conforme a rota', async () => {
    renderShell('/historico')

    await waitFor(() => {
      expect(document.title).toBe('Histórico · Controle de Rotina')
    })
  })
})
