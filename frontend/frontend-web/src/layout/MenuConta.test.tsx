import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { ThemeProvider } from '../theme/ThemeProvider'
import { MenuConta } from './MenuConta'

const logout = vi.fn()

vi.mock('../auth/session/AuthContext', () => ({
  useAuth: () => ({ logout, isAuthenticated: true, loginWithToken: vi.fn() }),
}))

vi.mock('../xp/XpProgressContext', () => ({
  useXpProgress: () => ({
    progresso: null,
    nomeUsuario: 'Ana Silva',
    aplicarProgresso: vi.fn(),
  }),
}))

function renderMenu() {
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <MenuConta />
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('MenuConta', () => {
  it('mostra iniciais do nome no gatilho', () => {
    renderMenu()

    expect(screen.getByRole('button', { name: /menu da conta: ana silva/i })).toBeInTheDocument()
    expect(screen.getByText('AS')).toBeInTheDocument()
  })

  it('abre o popover com Perfil e Sair', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /menu da conta/i }))

    expect(screen.getByRole('link', { name: /perfil/i })).toHaveAttribute('href', '/perfil')
    expect(screen.getByRole('button', { name: /sair/i })).toBeInTheDocument()
  })

  it('fecha com Escape', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /menu da conta/i }))
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('button', { name: /sair/i })).not.toBeInTheDocument()
  })

  it('fecha ao clicar fora', async () => {
    const user = userEvent.setup()
    render(
      <ThemeProvider>
        <MemoryRouter>
          <button type="button">Fora</button>
          <MenuConta />
        </MemoryRouter>
      </ThemeProvider>,
    )

    await user.click(screen.getByRole('button', { name: /menu da conta/i }))
    await user.click(screen.getByRole('button', { name: 'Fora' }))

    expect(screen.queryByRole('link', { name: /perfil/i })).not.toBeInTheDocument()
  })

  it('chama logout ao sair', async () => {
    logout.mockReset().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /menu da conta/i }))
    await user.click(screen.getByRole('button', { name: /sair/i }))

    expect(logout).toHaveBeenCalledOnce()
  })

  it('alterna aberto/fechado no gatilho', async () => {
    const user = userEvent.setup()
    renderMenu()
    const gatilho = screen.getByRole('button', { name: /menu da conta/i })

    await user.click(gatilho)
    expect(gatilho).toHaveAttribute('aria-expanded', 'true')
    await user.click(gatilho)
    expect(gatilho).toHaveAttribute('aria-expanded', 'false')
  })

  it('não chama logout só por abrir o menu', async () => {
    logout.mockReset()
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: /menu da conta/i }))

    expect(logout).not.toHaveBeenCalled()
  })
})
