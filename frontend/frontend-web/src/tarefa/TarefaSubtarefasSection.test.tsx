import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as tarefaApi from './api/tarefaApi'
import type { TarefaResponse } from './api/tarefaApi'
import { TarefaSubtarefasSection } from './TarefaSubtarefasSection'

vi.mock('./api/tarefaApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/tarefaApi')>()
  return {
    ...actual,
    listarTarefas: vi.fn(),
    criarTarefa: vi.fn(),
    buscarTarefa: vi.fn(),
    reordenarTarefas: vi.fn(),
  }
})

const pai: TarefaResponse = {
  id: 't-pai',
  titulo: 'TCC',
  descricao: '',
  prioridade: 'ALTA',
  prazo: null,
  status: 'PENDENTE',
  duracaoMinutos: 120,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
  temFilhos: true,
}

const filho: TarefaResponse = {
  ...pai,
  id: 't-filho',
  titulo: 'Capítulo 1',
  parentId: 't-pai',
  temFilhos: false,
  duracaoMinutos: 30,
}

const onPaiAtualizado = vi.fn()

function renderSecao() {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <TarefaSubtarefasSection parent={pai} onPaiAtualizado={onPaiAtualizado} />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('TarefaSubtarefasSection', () => {
  beforeEach(() => {
    onPaiAtualizado.mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockReset().mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 100,
    })
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tarefaApi.buscarTarefa).mockReset()
    vi.mocked(tarefaApi.reordenarTarefas).mockReset()
  })

  it('mostra estado vazio quando o pai não tem subtarefas', async () => {
    renderSecao()

    expect(await screen.findByText('Nenhuma subtarefa ainda.')).toBeInTheDocument()
  })

  it('lista subtarefas carregadas da API', async () => {
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [filho],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
    })
    renderSecao()

    expect(await screen.findByText('Capítulo 1')).toBeInTheDocument()
    expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
      expect.objectContaining({ parentId: 't-pai' }),
    )
  })

  it('não cria subtarefa com título vazio (estado inalterado)', async () => {
    const user = userEvent.setup()
    renderSecao()
    await screen.findByText('Nenhuma subtarefa ainda.')

    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    expect(onPaiAtualizado).not.toHaveBeenCalled()
  })

  it('limpa o título com Escape sem chamar a API', async () => {
    const user = userEvent.setup()
    renderSecao()
    const campo = await screen.findByPlaceholderText('Adicionar subtarefa…')
    await user.type(campo, 'Rascunho')
    await user.keyboard('{Escape}')

    expect(campo).toHaveValue('')
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('mostra erro de carga e lista vazia quando listar falha', async () => {
    vi.mocked(tarefaApi.listarTarefas).mockRejectedValue(new ApiError(500, 'Falha'))
    renderSecao()

    expect(await screen.findByText('Não foi possível carregar as subtarefas.')).toBeInTheDocument()
    expect(screen.queryByText('Capítulo 1')).not.toBeInTheDocument()
  })

  it('mostra erro da API ao criar e preserva o título digitado', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new ApiError(400, 'Título duplicado'))
    const user = userEvent.setup()
    renderSecao()
    await screen.findByPlaceholderText('Adicionar subtarefa…')

    await user.type(screen.getByPlaceholderText('Adicionar subtarefa…'), 'Capítulo 1')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Título duplicado')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Adicionar subtarefa…')).toHaveValue('Capítulo 1')
    expect(onPaiAtualizado).not.toHaveBeenCalled()
  })

  it('cria subtarefa e notifica o pai', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(filho)
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({ ...pai, temFilhos: true })
    const user = userEvent.setup()
    renderSecao()

    await user.type(await screen.findByPlaceholderText('Adicionar subtarefa…'), 'Capítulo 1')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
        expect.objectContaining({ titulo: 'Capítulo 1', parentId: 't-pai' }),
      )
    })
    expect(onPaiAtualizado).toHaveBeenCalled()
    expect(await screen.findByText('Capítulo 1')).toBeInTheDocument()
  })
})
