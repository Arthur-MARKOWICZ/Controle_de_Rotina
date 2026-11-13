import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { EmptyStatePlano } from './EmptyStatePlano'

describe('EmptyStatePlano', () => {
  it('T2: empty oferece caminho de volta a /tarefas', () => {
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando={false} onGerar={vi.fn()} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: /^gerar plano de hoje$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /voltar às tarefas/i })).toHaveAttribute(
      'href',
      '/tarefas',
    )
  })

  it('dispara gerar plano', async () => {
    const onGerar = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando={false} onGerar={onGerar} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: /^gerar plano de hoje$/i }))
    expect(onGerar).toHaveBeenCalledOnce()
  })

  it('não dispara gerar enquanto gerando', async () => {
    const onGerar = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando onGerar={onGerar} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )
    const botao = screen.getByRole('button', { name: /gerando/i })
    expect(botao).toBeDisabled()
    await user.click(botao)
    expect(onGerar).not.toHaveBeenCalled()
  })

  it('dispara ajustar capacidade', async () => {
    const onAjustar = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando={false} onGerar={vi.fn()} onAjustarCapacidade={onAjustar} />
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: /ajustar capacidade de hoje/i }))
    expect(onAjustar).toHaveBeenCalledOnce()
  })

  it('mostra o status de vazio', () => {
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando={false} onGerar={vi.fn()} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('status')).toHaveTextContent(/ainda não há plano/i)
  })

  it('não chama gerar só por montar', () => {
    const onGerar = vi.fn()
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando={false} onGerar={onGerar} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )
    expect(onGerar).not.toHaveBeenCalled()
  })

  it('link de tarefas permanece visível enquanto gera', () => {
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando onGerar={vi.fn()} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /voltar às tarefas/i })).toHaveAttribute('href', '/tarefas')
  })
})
