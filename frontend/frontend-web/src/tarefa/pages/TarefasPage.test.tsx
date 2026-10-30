import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../../feedback/ToastProvider'
import * as projetoApi from '../../projeto/api/projetoApi'
import * as tarefaApi from '../api/tarefaApi'
import { TarefasPage } from './TarefasPage'

vi.mock('../api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
}))

vi.mock('../../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

const refIso = '2026-10-28T15:00:00.000Z'

const paginaVariada = {
  content: [
    {
      id: 't-atrasada',
      titulo: 'Atrasada',
      descricao: '',
      prioridade: 'ALTA' as const,
      prazo: '2026-10-20T12:00:00Z',
      status: 'ATRASADA' as const,
      duracaoMinutos: 60,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    },
    {
      id: 't-hoje',
      titulo: 'Hoje item',
      descricao: '',
      prioridade: 'MEDIA' as const,
      prazo: '2026-10-28T20:00:00Z',
      status: 'PENDENTE' as const,
      duracaoMinutos: 120,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    },
    {
      id: 't-semana',
      titulo: 'Semana item',
      descricao: '',
      prioridade: 'BAIXA' as const,
      prazo: '2026-10-30T12:00:00Z',
      status: 'PENDENTE' as const,
      duracaoMinutos: 60,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    },
    {
      id: 't-depois',
      titulo: 'Depois item',
      descricao: '',
      prioridade: 'MEDIA' as const,
      prazo: '2026-11-10T12:00:00Z',
      status: 'EM_PROGRESSO' as const,
      duracaoMinutos: 180,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    },
  ],
  totalElements: 4,
  totalPages: 1,
  number: 0,
  size: 20,
}

const pagina = {
  content: [paginaVariada.content[1]],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

function renderLista(path = '/tarefas') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/tarefas" element={<TarefasPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('TarefasPage', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.excluirTarefa).mockReset()
    vi.mocked(tarefaApi.restaurarTarefa).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(pagina)
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue({
      content: [
        {
          id: 'proj-tcc',
          nome: 'TCC',
          descricao: null,
          status: 'ATIVO',
          createdAt: '2026-01-01T10:00:00Z',
          updatedAt: '2026-01-01T10:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    })
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(refIso))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('lista tarefas e mostra status em PT', async () => {
    renderLista()

    expect(await screen.findByText('Hoje item')).toBeInTheDocument()
    expect(document.querySelector('.status-pendente')).toHaveTextContent('Pendente')
    expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
        sort: 'prazo',
        direction: 'asc',
      }),
    )
  })

  it('T3: renderiza grupos com contador', async () => {
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaVariada)
    renderLista()

    expect(await screen.findByRole('heading', { name: /atrasadas \(1\)/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /^hoje \(1\)$/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /esta semana \(1\)/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /depois \(1\)/i })).toBeInTheDocument()
  })

  it('T4: lista usa cards, sem tabela', async () => {
    renderLista()
    await screen.findByText('Hoje item')

    expect(document.querySelector('table')).not.toBeInTheDocument()
    expect(document.querySelector('.tarefa-card')).toBeInTheDocument()
  })

  it('T1: search params reaplicam visão e filtro na carga', async () => {
    renderLista('/tarefas?visao=pendentes&prioridade=ALTA&sort=duracao&direction=desc&page=1')

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
        expect.objectContaining({
          status: ['PENDENTE'],
          prioridade: ['ALTA'],
          sort: 'duracao',
          direction: 'desc',
          page: 1,
        }),
      )
    })
    expect(screen.getByRole('button', { name: 'Pendentes' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('T2: filtro projetoId na URL da lista de tarefas', async () => {
    renderLista('/tarefas?projetoId=proj-tcc')

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
        expect.objectContaining({
          projetoId: 'proj-tcc',
        }),
      )
    })
  })

  it('chip de visão aplica filtro imediatamente', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderLista()
    await screen.findByText('Hoje item')

    await user.click(screen.getByRole('button', { name: 'Em progresso' }))

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
        expect.objectContaining({
          status: ['EM_PROGRESSO'],
          page: 0,
        }),
      )
    })
  })

  it('filtros avançados: prioridade e ordenação via painel', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderLista()
    await screen.findByText('Hoje item')

    await user.click(screen.getByRole('button', { name: /^filtros$/i }))
    await user.click(screen.getByLabelText(/^alta$/i))
    await user.selectOptions(screen.getByLabelText(/ordenar por/i), 'duracao')
    await user.selectOptions(screen.getByLabelText(/^direção$/i), 'desc')
    await user.click(screen.getByRole('button', { name: /aplicar filtros/i }))

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
        expect.objectContaining({
          prioridade: ['ALTA'],
          sort: 'duracao',
          direction: 'desc',
          page: 0,
        }),
      )
    })
  })

  it('excluir na lista: cancelar confirmação não chama API', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderLista()
    await screen.findByText('Hoje item')

    await user.click(screen.getByRole('button', { name: /^excluir$/i }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /excluir tarefa/i })).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^cancelar$/i }))

    expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
  })

  it('T2: confirmar exclusão mostra Desfazer e restore é chamado', async () => {
    vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)
    vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue({
      ...pagina.content[0],
      deletada: false,
      deletedAt: null,
    })
    vi.mocked(tarefaApi.listarTarefas)
      .mockResolvedValueOnce(pagina)
      .mockResolvedValueOnce({ ...pagina, content: [], totalElements: 0, totalPages: 0 })
      .mockResolvedValueOnce(pagina)

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderLista()
    await screen.findByText('Hoje item')

    await user.click(screen.getByRole('button', { name: /^excluir$/i }))
    expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /excluir tarefa/i })).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: /^excluir$/i }))

    await waitFor(() => {
      expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-hoje')
    })

    await user.click(screen.getByRole('button', { name: /desfazer/i }))
    await waitFor(() => {
      expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-hoje')
    })
  })

  it('visão Excluídas lista deletadas e permite restaurar', async () => {
    const deletada = {
      ...pagina.content[0],
      deletada: true,
      deletedAt: '2026-02-01T10:00:00Z',
    }
    vi.mocked(tarefaApi.listarTarefas)
      .mockResolvedValueOnce(pagina)
      .mockResolvedValueOnce({
        ...pagina,
        content: [deletada],
      })
      .mockResolvedValueOnce(pagina)
    vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue({
      ...deletada,
      deletada: false,
      deletedAt: null,
    })

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderLista()
    await screen.findByText('Hoje item')

    await user.click(screen.getByRole('button', { name: 'Excluídas' }))

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
        expect.objectContaining({
          somenteDeletadas: true,
        }),
      )
    })

    expect(await screen.findByText('Excluída')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^restaurar$/i }))

    await waitFor(() => {
      expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-hoje')
    })
  })

  it('T5: empty state oferece CTA Criar primeira tarefa', async () => {
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderLista()

    expect(await screen.findByText(/nenhuma tarefa ainda/i)).toBeInTheDocument()
    const empty = screen.getByText(/nenhuma tarefa ainda/i).closest('.tarefas-empty')
    expect(empty).not.toBeNull()
    await user.click(within(empty as HTMLElement).getByRole('button', { name: /criar primeira tarefa/i }))
    expect(await screen.findByRole('heading', { name: /nova tarefa/i })).toBeInTheDocument()
  })
})
