import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Popover } from './Popover'

describe('Popover', () => {
  it('abre o painel ao clicar no gatilho', async () => {
    const user = userEvent.setup()
    render(
      <Popover label="Filtros">
        <p>Conteúdo</p>
      </Popover>,
    )
    expect(screen.queryByText('Conteúdo')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Filtros' }))
    expect(screen.getByText('Conteúdo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Filtros' })).toHaveAttribute('aria-expanded', 'true')
  })

  it('fecha com Escape e devolve o foco ao gatilho', async () => {
    const user = userEvent.setup()
    render(
      <Popover label="Filtros">
        <p>Conteúdo</p>
      </Popover>,
    )
    const gatilho = screen.getByRole('button', { name: 'Filtros' })
    await user.click(gatilho)
    await user.keyboard('{Escape}')
    expect(screen.queryByText('Conteúdo')).not.toBeInTheDocument()
    expect(gatilho).toHaveFocus()
  })

  it('fecha ao clicar fora sem disparar ação interna', async () => {
    const onAcao = vi.fn()
    const user = userEvent.setup()
    render(
      <div>
        <Popover label="Filtros">
          <button type="button" onClick={onAcao}>
            Aplicar
          </button>
        </Popover>
        <button type="button">Fora</button>
      </div>,
    )
    await user.click(screen.getByRole('button', { name: 'Filtros' }))
    await user.click(screen.getByRole('button', { name: 'Fora' }))
    expect(screen.queryByRole('button', { name: 'Aplicar' })).not.toBeInTheDocument()
    expect(onAcao).not.toHaveBeenCalled()
  })

  it('fecha ao escolher um item quando fecharAoEscolher', async () => {
    const user = userEvent.setup()
    render(
      <Popover label="Menu" fecharAoEscolher>
        <button type="button">Item</button>
      </Popover>,
    )
    await user.click(screen.getByRole('button', { name: 'Menu' }))
    await user.click(screen.getByRole('button', { name: 'Item' }))
    expect(screen.queryByRole('button', { name: 'Item' })).not.toBeInTheDocument()
  })

  it('não fecha ao clicar no painel quando fecharAoEscolher é falso', async () => {
    const user = userEvent.setup()
    render(
      <Popover label="Menu">
        <p>Painel</p>
      </Popover>,
    )
    await user.click(screen.getByRole('button', { name: 'Menu' }))
    await user.click(screen.getByText('Painel'))
    expect(screen.getByText('Painel')).toBeInTheDocument()
  })

  it('usa aria-label no gatilho quando o label visual não basta', () => {
    render(
      <Popover label={<span aria-hidden>•</span>} ariaLabel="Mais opções">
        <p>x</p>
      </Popover>,
    )
    expect(screen.getByRole('button', { name: 'Mais opções' })).toBeInTheDocument()
  })

  it('alterna aberto/fechado no segundo clique do gatilho', async () => {
    const user = userEvent.setup()
    render(
      <Popover label="Filtros">
        <p>Conteúdo</p>
      </Popover>,
    )
    const gatilho = screen.getByRole('button', { name: 'Filtros' })
    await user.click(gatilho)
    await user.click(gatilho)
    expect(screen.queryByText('Conteúdo')).not.toBeInTheDocument()
    expect(gatilho).toHaveAttribute('aria-expanded', 'false')
  })
})
