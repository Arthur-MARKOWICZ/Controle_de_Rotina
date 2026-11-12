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

  it('T5: card do bloco exibe nome do projeto quando presente', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="ALTA"
          projetoNome="TCC"
        />
      </MemoryRouter>,
    )

    expect(screen.getByText('TCC')).toBeInTheDocument()
    expect(document.querySelector('.badge--projeto')).toHaveTextContent('TCC')
  })

  it('T5: sem projetoNome não mostra chip de projeto', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="ALTA"
        />
      </MemoryRouter>,
    )

    expect(document.querySelector('.badge--projeto')).not.toBeInTheDocument()
  })

  it('bloco de tarefa bloqueada indica a espera e não recebe ações de sessão', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="ALTA"
          statusExecucao="NAO_INICIADO"
          espera="Espera Revisar API"
          acoes={null}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText('Espera Revisar API')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /iniciar/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /concluir/i })).not.toBeInTheDocument()
  })

  it('bloco sem espera não mostra indicação de bloqueio', () => {
    render(
      <MemoryRouter>
        <BlocoCard
          ordem={1}
          titulo="Estudar Spring"
          tarefaId="t-1"
          duracaoMinutos={60}
          prioridade="ALTA"
          statusExecucao="NAO_INICIADO"
        />
      </MemoryRouter>,
    )

    expect(screen.queryByText(/espera/i)).not.toBeInTheDocument()
  })
})
