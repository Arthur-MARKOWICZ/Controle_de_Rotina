import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { ThemeProvider } from '../theme/ThemeProvider'
import { MenuMais } from './MenuMais'

const logout = vi.fn()

vi.mock('../auth/session/AuthContext', () => ({
  useAuth: () => ({ logout, isAuthenticated: true, loginWithToken: vi.fn() }),
}))

function renderMenu() {
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <MenuMais />
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('MenuMais', () => {
  it('abre o sheet com destinos extras', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /mais/i }))

    expect(screen.getByRole('link', { name: /capacidade/i })).toHaveAttribute('href', '/capacidade')
    expect(screen.getByRole('link', { name: /histórico/i })).toHaveAttribute('href', '/historico')
    expect(screen.getByRole('link', { name: /revisão da semana/i })).toHaveAttribute('href', '/revisao')
  })

  it('fecha ao clicar no backdrop', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /mais/i }))
    await user.click(screen.getByRole('dialog', { name: /mais opções/i }))

    expect(screen.queryByRole('dialog', { name: /mais opções/i })).not.toBeInTheDocument()
  })

  it('fecha ao escolher um destino', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /mais/i }))
    await user.click(screen.getByRole('link', { name: /tags/i }))

    expect(screen.queryByRole('dialog', { name: /mais opções/i })).not.toBeInTheDocument()
  })

  it('chama logout', async () => {
    logout.mockReset().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /mais/i }))
    await user.click(screen.getByRole('button', { name: /sair/i }))

    expect(logout).toHaveBeenCalledOnce()
  })

  it('não chama logout só por abrir', async () => {
    logout.mockReset()
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /mais/i }))

    expect(logout).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /mais/i })).toHaveAttribute('aria-expanded', 'true')
  })

  it('expõe os cinco destinos do sheet', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /mais/i }))

    expect(screen.getByRole('link', { name: /capacidade/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /calendário/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /histórico/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /tags/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /perfil/i })).toBeInTheDocument()
  })

  it('marca o gatilho como fechado depois do backdrop', async () => {
    const user = userEvent.setup()
    renderMenu()
    const gatilho = screen.getByRole('button', { name: /mais/i })

    await user.click(gatilho)
    await user.click(screen.getByRole('dialog', { name: /mais opções/i }))

    expect(gatilho).toHaveAttribute('aria-expanded', 'false')
  })
})
