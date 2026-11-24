import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ObjetivoProgresso } from './ObjetivoProgresso'

function progresso(percentual: number, minutosConcluidos: number, minutosTotais: number) {
  return { percentual, minutosConcluidos, minutosTotais }
}

function preenchimento(): HTMLElement {
  return screen.getByRole('progressbar').firstElementChild as HTMLElement
}

describe('ObjetivoProgresso', () => {
  it('anuncia a barra com valores, texto legível e nome com o título', () => {
    render(
      <ObjetivoProgresso titulo="Perder 5 kg" progresso={progresso(40, 120, 300)} tamanho="compacto" />,
    )

    const barra = screen.getByRole('progressbar', { name: 'Progresso de Perder 5 kg' })
    expect(barra).toHaveAttribute('aria-valuenow', '40')
    expect(barra).toHaveAttribute('aria-valuemin', '0')
    expect(barra).toHaveAttribute('aria-valuemax', '100')
    expect(barra).toHaveAttribute('aria-valuetext', '40%, 2h de 5h concluídas')
    expect(preenchimento().style.width).toBe('40%')
  })

  it('mostra a barra vazia em 0%', () => {
    render(<ObjetivoProgresso titulo="X" progresso={progresso(0, 0, 90)} tamanho="compacto" />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0')
    expect(preenchimento().style.width).toBe('0%')
    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuetext',
      '0%, 0min de 1h 30min concluídas',
    )
  })

  it('mostra a barra cheia em 100%', () => {
    render(<ObjetivoProgresso titulo="X" progresso={progresso(100, 300, 300)} tamanho="destaque" />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
    expect(preenchimento().style.width).toBe('100%')
  })

  it('limita a 100 o percentual acima do máximo vindo da API', () => {
    render(<ObjetivoProgresso titulo="X" progresso={progresso(130, 45, 30)} tamanho="compacto" />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
    expect(preenchimento().style.width).toBe('100%')
  })

  it('limita a 0 o percentual negativo vindo da API', () => {
    render(<ObjetivoProgresso titulo="X" progresso={progresso(-5, 0, 60)} tamanho="compacto" />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0')
    expect(preenchimento().style.width).toBe('0%')
  })

  it('sem progresso mostra o texto alternativo e nenhuma barra, nem 0%', () => {
    const { container } = render(
      <ObjetivoProgresso
        titulo="X"
        progresso={null}
        tamanho="compacto"
        semProgresso="Vincule itens para medir."
      />,
    )

    expect(screen.getByText('Vincule itens para medir.')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(container).not.toHaveTextContent('%')
  })

  it('sem progresso e sem texto alternativo não renderiza nada', () => {
    const { container } = render(
      <ObjetivoProgresso titulo="X" progresso={null} tamanho="destaque" />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('total de minutos zero com objeto presente é tratado como sem progresso', () => {
    const { container } = render(
      <ObjetivoProgresso
        titulo="X"
        progresso={progresso(0, 0, 0)}
        tamanho="compacto"
        semProgresso="Sem tempo."
      />,
    )

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByText('Sem tempo.')).toBeInTheDocument()
    expect(container).not.toHaveTextContent('%')
  })

  it('esconde a legenda visual dos leitores de tela para não duplicar o anúncio', () => {
    const { container } = render(
      <ObjetivoProgresso titulo="X" progresso={progresso(40, 120, 300)} tamanho="compacto" />,
    )

    const legenda = container.querySelector('.objetivo-progresso__legenda')
    expect(legenda).toHaveAttribute('aria-hidden', 'true')
    expect(legenda).toHaveTextContent('40% · 2h de 5h concluídas')
  })
})
