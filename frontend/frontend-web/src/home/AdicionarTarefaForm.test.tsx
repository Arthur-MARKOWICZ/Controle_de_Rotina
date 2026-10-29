import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { TarefaResponse } from '../tarefa/api/tarefaApi'
import { AdicionarTarefaForm } from './AdicionarTarefaForm'

const elegiveis: TarefaResponse[] = [
  {
    id: 't-2',
    titulo: 'Ler docs',
    descricao: '',
    prioridade: 'MEDIA',
    prazo: '2030-01-01T12:00:00Z',
    status: 'PENDENTE',
    duracaoMinutos: 60,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    deletedAt: null,
    deletada: false,
  },
  {
    id: 't-3',
    titulo: 'Correr no parque',
    descricao: '',
    prioridade: 'BAIXA',
    prazo: '2026-10-21T12:00:00Z',
    status: 'PENDENTE',
    duracaoMinutos: 120,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    deletedAt: null,
    deletada: false,
  },
]

describe('AdicionarTarefaForm', () => {
  it('T5: filtra lista pesquisável por texto', async () => {
    const user = userEvent.setup()
    const onSelecionar = vi.fn()

    render(
      <AdicionarTarefaForm
        elegiveis={elegiveis}
        tarefaSelecionadaId=""
        carregandoElegiveis={false}
        adicionando={false}
        onSelecionar={onSelecionar}
        onSubmit={(event) => event.preventDefault()}
      />,
    )

    expect(screen.getByRole('option', { name: /ler docs/i })).toBeInTheDocument()
    await user.type(screen.getByLabelText(/buscar tarefa/i), 'correr')
    expect(screen.getByRole('option', { name: /correr no parque/i })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /ler docs/i })).not.toBeInTheDocument()
  })
})
