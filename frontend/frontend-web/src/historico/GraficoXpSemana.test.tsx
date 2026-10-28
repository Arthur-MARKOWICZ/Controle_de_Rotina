import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { GraficoXpSemana } from './GraficoXpSemana'

const pontos = [
  { data: '2026-10-26', xp: 100 },
  { data: '2026-10-27', xp: 280 },
  { data: '2026-10-28', xp: 0 },
  { data: '2026-10-29', xp: 50 },
  { data: '2026-10-30', xp: 120 },
  { data: '2026-10-31', xp: 40 },
  { data: '2026-11-01', xp: 30 },
]

describe('GraficoXpSemana (T3)', () => {
  it('expõe alternativa textual acessível com XP por dia', () => {
    render(<GraficoXpSemana pontos={pontos} />)

    const tabela = screen.getByRole('table', { name: /xp por dia da semana/i })
    expect(within(tabela).getByText('27/10/2026')).toBeInTheDocument()
    expect(within(tabela).getByText('280 XP')).toBeInTheDocument()
    expect(within(tabela).getByText('0 XP')).toBeInTheDocument()
  })

  it('renderiza barras e rótulos dos dias da semana', () => {
    const { container } = render(<GraficoXpSemana pontos={pontos} />)
    expect(container.querySelectorAll('.historico-grafico__barra')).toHaveLength(7)
    expect(container.querySelectorAll('.historico-grafico__label')).toHaveLength(7)
    expect(screen.getByText('Ter')).toBeInTheDocument()
  })
})
