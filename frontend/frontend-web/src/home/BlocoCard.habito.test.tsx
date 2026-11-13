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

  it('sem habitoId não vira link', () => {
    render(
      <MemoryRouter>
        <BlocoCard ordem={1} titulo="Correr" origem="HABITO" duracaoMinutos={30} />
      </MemoryRouter>,
    )
    expect(screen.queryByRole('link', { name: 'Correr' })).not.toBeInTheDocument()
    expect(screen.getByText('Correr')).toBeInTheDocument()
  })

  it('sem horário preferencial não mostra o rótulo', () => {
    render(
      <MemoryRouter>
        <BlocoCard ordem={1} titulo="Correr" origem="HABITO" habitoId="h-1" duracaoMinutos={30} />
      </MemoryRouter>,
    )
    expect(screen.queryByLabelText('Horário preferencial')).not.toBeInTheDocument()
  })

  it('não mostra prioridade de tarefa no bloco de hábito', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Correr"
          origem="HABITO"
          habitoId="h-1"
          duracaoMinutos={30}
          prioridade="ALTA"
        />
      </MemoryRouter>,
    )
    expect(screen.queryByText('Alta')).not.toBeInTheDocument()
  })

  it('não aponta para /tarefas', () => {
    render(
      <MemoryRouter>
        <BlocoCard ordem={1} titulo="Correr" origem="HABITO" habitoId="h-1" duracaoMinutos={30} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: 'Correr' })).toHaveAttribute('href', '/habitos/h-1')
    expect(screen.queryByRole('link', { name: 'Correr' })).not.toHaveAttribute('href', '/tarefas/h-1')
  })

  it('justificativa de prazo não aparece em hábito', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Correr"
          origem="HABITO"
          habitoId="h-1"
          duracaoMinutos={30}
          prazo="2030-01-01T12:00:00Z"
          prioridade="ALTA"
          mostrarJustificativa
        />
      </MemoryRouter>,
    )
    expect(screen.queryByText(/prazo/i)).not.toBeInTheDocument()
  })

  it('omite motivo de recorrência quando ausente', () => {
    render(
      <MemoryRouter>
        <BlocoCard ordem={1} titulo="Correr" origem="HABITO" habitoId="h-1" duracaoMinutos={30} />
      </MemoryRouter>,
    )
    expect(screen.queryByText(/recorrente/i)).not.toBeInTheDocument()
  })
})
