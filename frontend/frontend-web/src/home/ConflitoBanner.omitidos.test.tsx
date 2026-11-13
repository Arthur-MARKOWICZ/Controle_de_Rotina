import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ConflitoBanner } from './ConflitoBanner'

describe('ConflitoBanner omitidos (F2)', () => {
  it('lista omitidos e oferece ação Adicionar', async () => {
    const onAdicionar = vi.fn()
    const user = userEvent.setup()

    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          {
            tipo: 'HABITO',
            id: 'h-1',
            titulo: 'Meditar',
            duracaoMinutos: 15,
            motivo: 'CAPACIDADE',
          },
        ]}
        onAdicionarOmitido={onAdicionar}
        onManterAssim={() => undefined}
      />,
    )

    expect(screen.getByLabelText('Itens não alocados')).toBeInTheDocument()
    expect(screen.getByText('Meditar')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Adicionar' }))
    expect(onAdicionar).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'h-1', tipo: 'HABITO' }),
    )
  })

  it('sem onAdicionarOmitido lista o item mas não mostra Adicionar', () => {
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          { tipo: 'HABITO', id: 'h-1', titulo: 'Meditar', duracaoMinutos: 15, motivo: 'CAPACIDADE' },
        ]}
      />,
    )
    expect(screen.getByText('Meditar')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Adicionar' })).not.toBeInTheDocument()
  })

  it('adicionandoId desabilita o botão do item e não dispara de novo', async () => {
    const onAdicionar = vi.fn()
    const user = userEvent.setup()
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          { tipo: 'HABITO', id: 'h-1', titulo: 'Meditar', duracaoMinutos: 15, motivo: 'CAPACIDADE' },
        ]}
        onAdicionarOmitido={onAdicionar}
        adicionandoId="h-1"
      />,
    )
    const botao = screen.getByRole('button', { name: /adicionando/i })
    expect(botao).toBeDisabled()
    await user.click(botao)
    expect(onAdicionar).not.toHaveBeenCalled()
  })

  it('removendo também trava Adicionar', async () => {
    const onAdicionar = vi.fn()
    const user = userEvent.setup()
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          { tipo: 'TAREFA', id: 't-1', titulo: 'API', duracaoMinutos: 45, motivo: 'CAPACIDADE' },
        ]}
        onAdicionarOmitido={onAdicionar}
        removendo
      />,
    )
    const botao = screen.getByRole('button', { name: 'Adicionar' })
    expect(botao).toBeDisabled()
    await user.click(botao)
    expect(onAdicionar).not.toHaveBeenCalled()
  })

  it('lista vazia não renderiza a lista de omitidos', () => {
    render(<ConflitoBanner mensagem="Capacidade insuficiente" temConflito itensNaoAlocados={[]} />)
    expect(screen.queryByLabelText('Itens não alocados')).not.toBeInTheDocument()
  })

  it('marca hábito vs tarefa no omitido', () => {
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          { tipo: 'HABITO', id: 'h-1', titulo: 'Meditar', duracaoMinutos: 15, motivo: 'CAPACIDADE' },
          { tipo: 'TAREFA', id: 't-1', titulo: 'API', duracaoMinutos: 45, motivo: 'CAPACIDADE' },
        ]}
      />,
    )
    expect(screen.getAllByText('Hábito')).toHaveLength(1)
    expect(screen.getByText('Tarefa')).toBeInTheDocument()
  })

  it('naoDeveTravarOOutroOmitidoQuandoAdicionandoIdApontaParaUmItem', async () => {
    const onAdicionar = vi.fn()
    const user = userEvent.setup()
    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          { tipo: 'HABITO', id: 'h-1', titulo: 'Meditar', duracaoMinutos: 15, motivo: 'CAPACIDADE' },
          { tipo: 'TAREFA', id: 't-1', titulo: 'API', duracaoMinutos: 45, motivo: 'CAPACIDADE' },
        ]}
        onAdicionarOmitido={onAdicionar}
        adicionandoId="h-1"
      />,
    )
    const ocupado = screen.getByRole('button', { name: /adicionando/i })
    const livres = screen.getAllByRole('button', { name: /^adicionar$/i })
    expect(ocupado).toBeDisabled()
    expect(livres).toHaveLength(1)
    const livre = livres[0]
    expect(livre).toBeEnabled()
    await user.click(livre)
    expect(onAdicionar).toHaveBeenCalledTimes(1)
    expect(onAdicionar).toHaveBeenCalledWith(expect.objectContaining({ id: 't-1', tipo: 'TAREFA' }))
  })
})
