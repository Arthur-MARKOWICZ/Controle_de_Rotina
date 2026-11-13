import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { PeriodoNav } from './PeriodoNav'

const opcoes = [
  { valor: 'semana' as const, label: 'Semana' },
  { valor: 'mes' as const, label: 'Mês' },
]

describe('PeriodoNav', () => {
  it('mostra o rótulo do período', () => {
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={vi.fn()}
        rotulo="6–12 out"
        onAnterior={vi.fn()}
        onProximo={vi.fn()}
        onHoje={vi.fn()}
      />,
    )

    expect(screen.getByText('6–12 out')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Semana' })).toHaveAttribute('aria-checked', 'true')
  })

  it('troca a visão', async () => {
    const onVisaoChange = vi.fn()
    const user = userEvent.setup()
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={onVisaoChange}
        rotulo="hoje"
        onAnterior={vi.fn()}
        onProximo={vi.fn()}
        onHoje={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('radio', { name: 'Mês' }))

    expect(onVisaoChange).toHaveBeenCalledWith('mes')
  })

  it('navega para o período anterior', async () => {
    const onAnterior = vi.fn()
    const user = userEvent.setup()
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={vi.fn()}
        rotulo="hoje"
        onAnterior={onAnterior}
        onProximo={vi.fn()}
        onHoje={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: /período anterior/i }))

    expect(onAnterior).toHaveBeenCalledOnce()
  })

  it('navega para hoje', async () => {
    const onHoje = vi.fn()
    const user = userEvent.setup()
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={vi.fn()}
        rotulo="hoje"
        onAnterior={vi.fn()}
        onProximo={vi.fn()}
        onHoje={onHoje}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Hoje' }))

    expect(onHoje).toHaveBeenCalledOnce()
  })

  it('desabilita avançar quando podeAvancar é falso', async () => {
    const onProximo = vi.fn()
    const user = userEvent.setup()
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={vi.fn()}
        rotulo="hoje"
        onAnterior={vi.fn()}
        onProximo={onProximo}
        onHoje={vi.fn()}
        podeAvancar={false}
      />,
    )

    const proximo = screen.getByRole('button', { name: /próximo período/i })
    expect(proximo).toBeDisabled()
    await user.click(proximo)
    expect(onProximo).not.toHaveBeenCalled()
  })

  it('avança quando permitido', async () => {
    const onProximo = vi.fn()
    const user = userEvent.setup()
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={vi.fn()}
        rotulo="hoje"
        onAnterior={vi.fn()}
        onProximo={onProximo}
        onHoje={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: /próximo período/i }))

    expect(onProximo).toHaveBeenCalledOnce()
  })

  it('usa rótulos customizados de anterior/próximo', () => {
    render(
      <PeriodoNav
        opcoes={opcoes}
        visao="semana"
        onVisaoChange={vi.fn()}
        rotulo="hoje"
        onAnterior={vi.fn()}
        onProximo={vi.fn()}
        onHoje={vi.fn()}
        rotuloAnterior="Semana passada"
        rotuloProximo="Próxima semana"
      />,
    )

    expect(screen.getByRole('button', { name: 'Semana passada' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Próxima semana' })).toBeInTheDocument()
  })
})
