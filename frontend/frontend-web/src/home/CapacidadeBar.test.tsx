import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { CapacidadeBar } from './CapacidadeBar'
import { formatarDataPorExtenso } from './formatacao'

describe('CapacidadeBar (T1)', () => {
  it('renderiza barra e texto acessível com usados/disponíveis', () => {
    render(<CapacidadeBar usadosMinutos={60} disponiveisMinutos={120} sobrecarga={false} />)

    const bar = screen.getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '50')
    expect(bar).toHaveAttribute('aria-valuemin', '0')
    expect(bar).toHaveAttribute('aria-valuemax', '100')
    expect(bar).toHaveAttribute('aria-label', expect.stringMatching(/1h\s*\/\s*2h/i))

    expect(screen.getByText(/1h\s*\/\s*2h/i)).toBeInTheDocument()
    expect(bar).not.toHaveClass('capacidade-bar--sobrecarga')
  })

  it('marca sobrecarga com token danger e texto acessível', () => {
    render(<CapacidadeBar usadosMinutos={180} disponiveisMinutos={120} sobrecarga />)

    const bar = screen.getByRole('progressbar')
    expect(bar).toHaveClass('capacidade-bar--sobrecarga')
    expect(bar).toHaveAttribute(
      'aria-label',
      expect.stringMatching(/sobrecarga|capacidade insuficiente|3h\s*\/\s*2h/i),
    )
  })
})

describe('saudação / data por extenso', () => {
  it('formata data em pt-BR com dia da semana e mês', () => {
    // 2026-10-27 = terça-feira em America/Sao_Paulo
    const texto = formatarDataPorExtenso(new Date('2026-10-27T15:00:00Z'))
    expect(texto).toMatch(/^Terça/i)
    expect(texto).toMatch(/27/)
    expect(texto).toMatch(/outubro/i)
  })
})
