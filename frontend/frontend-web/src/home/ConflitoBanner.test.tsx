import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ConflitoBanner } from './ConflitoBanner'

describe('ConflitoBanner', () => {
  it('T2: renderiza ação que dispara callback', async () => {
    const onAumentar = vi.fn()
    const user = userEvent.setup()

    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        onAumentarCapacidade={onAumentar}
        onManterAssim={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: /aumentar capacidade de hoje/i }))
    expect(onAumentar).toHaveBeenCalledOnce()
  })

  it('não mostra ações quando temConflito é falso', () => {
    render(
      <ConflitoBanner
        mensagem="Tudo certo"
        temConflito={false}
        onAumentarCapacidade={vi.fn()}
        onManterAssim={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button', { name: /aumentar capacidade/i })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', 'Mensagem do plano')
  })

  it('Manter assim dispara callback e não aumenta capacidade', async () => {
    const onAumentar = vi.fn()
    const onManter = vi.fn()
    const user = userEvent.setup()
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        onAumentarCapacidade={onAumentar}
        onManterAssim={onManter}
      />,
    )
    await user.click(screen.getByRole('button', { name: /manter assim/i }))
    expect(onManter).toHaveBeenCalledOnce()
    expect(onAumentar).not.toHaveBeenCalled()
  })

  it('remover menor prioridade fica desabilitado enquanto removendo', async () => {
    const onRemover = vi.fn()
    const user = userEvent.setup()
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        onRemoverMenorPrioridade={onRemover}
        removendo
      />,
    )
    const botao = screen.getByRole('button', { name: /remover tarefa de menor prioridade/i })
    expect(botao).toBeDisabled()
    await user.click(botao)
    expect(onRemover).not.toHaveBeenCalled()
  })

  it('lista itens omitidos e adiciona', async () => {
    const onAdicionar = vi.fn()
    const user = userEvent.setup()
    const item = {
      tipo: 'TAREFA' as const,
      id: 't-9',
      titulo: 'Revisar API',
      duracaoMinutos: 45,
      motivo: 'SEM_CAPACIDADE',
    }
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[item]}
        onAdicionarOmitido={onAdicionar}
      />,
    )
    expect(screen.getByRole('list', { name: /itens não alocados/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^adicionar$/i }))
    expect(onAdicionar).toHaveBeenCalledWith(item)
  })

  it('não lista omitidos sem conflito mesmo com itens', () => {
    render(
      <ConflitoBanner
        mensagem="Ok"
        temConflito={false}
        itensNaoAlocados={[
          { tipo: 'TAREFA', id: 't-9', titulo: 'Revisar API', duracaoMinutos: 45, motivo: 'SEM_CAPACIDADE' },
        ]}
      />,
    )
    expect(screen.queryByRole('list', { name: /itens não alocados/i })).not.toBeInTheDocument()
  })

  it('exibe a mensagem de conflito no alerta', () => {
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        onAumentarCapacidade={vi.fn()}
        onManterAssim={vi.fn()}
      />,
    )
    expect(screen.getByRole('status')).toHaveTextContent(/capacidade insuficiente/i)
  })
})
