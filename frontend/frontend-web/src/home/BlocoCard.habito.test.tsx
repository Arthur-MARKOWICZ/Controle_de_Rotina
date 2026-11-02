import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { BlocoCard } from './BlocoCard'

describe('BlocoCard hábito (F2)', () => {
  it('mostra rótulo Hábito, título e duração', () => {
    render(
      <MemoryRouter>
        <ul>
          <BlocoCard
            ordem={1}
            titulo="Correr"
            origem="HABITO"
            habitoId="h-1"
            duracaoMinutos={30}
            horarioPreferencial="07:00:00"
            motivoRecorrencia="Recorrente · Seg, Qua"
          />
        </ul>
      </MemoryRouter>,
    )

    expect(screen.getByText('Hábito')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Correr' })).toHaveAttribute('href', '/habitos/h-1')
    expect(screen.getByText('30min')).toBeInTheDocument()
    expect(screen.getByLabelText('Horário preferencial')).toHaveTextContent('Pref. 07:00')
    expect(screen.getByText('Recorrente · Seg, Qua')).toBeInTheDocument()
  })
})
