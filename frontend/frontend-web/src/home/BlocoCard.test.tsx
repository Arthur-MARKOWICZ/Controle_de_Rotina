import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { BlocoCard } from './BlocoCard'

describe('BlocoCard (T2)', () => {
  it('exibe prioridade e status em português, não enum cru', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="ALTA"
          statusExecucao="EM_ANDAMENTO"
        />
      </MemoryRouter>,
    )

    expect(screen.getByText('Estudar Spring')).toBeInTheDocument()
    expect(screen.getByText('Alta')).toBeInTheDocument()
    expect(screen.getByText('Em andamento')).toBeInTheDocument()
    expect(screen.queryByText('ALTA')).not.toBeInTheDocument()
    expect(screen.queryByText('EM_ANDAMENTO')).not.toBeInTheDocument()
  })

  it('em rascunho mostra ação Remover quando onRemover é passado', async () => {
    const onRemover = vi.fn()
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="MEDIA"
          onRemover={onRemover}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText('Média')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /remover estudar spring/i }))
    expect(onRemover).toHaveBeenCalledOnce()
  })

  it('mostra justificativa quando prazo e flag estão presentes', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="ALTA"
          prazo="2026-10-22T15:00:00Z"
          mostrarJustificativa
        />
      </MemoryRouter>,
    )

    expect(screen.getByText(/prioridade alta/i)).toBeInTheDocument()
  })
})
