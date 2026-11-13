import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as projetoApi from '../projeto/api/projetoApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import type { EsperaResumo } from './api/espera'
import { EsperaSection } from './EsperaSection'

vi.mock('../tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
}))

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

const esperaAberta: EsperaResumo = {
  id: 'e-1',
  alvoTipo: 'TAREFA',
  alvoId: 't-2',
  alvoTitulo: 'Revisar API',
  resolvida: false,
}

const onCriar = vi.fn()
const onRemover = vi.fn()

function renderEspera(esperas: EsperaResumo[] = [], somenteLeitura = false) {
  return render(
    <EsperaSection
      origemTipo="TAREFA"
      origemId="t-1"
      esperas={esperas}
      somenteLeitura={somenteLeitura}
      onCriar={onCriar}
      onRemover={onRemover}
    />,
  )
}

describe('EsperaSection', () => {
  beforeEach(() => {
    onCriar.mockReset()
    onRemover.mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockReset().mockResolvedValue({
      content: [
        {
          id: 't-2',
          titulo: 'Revisar API',
          descricao: '',
          prioridade: 'MEDIA',
          prazo: null,
          status: 'PENDENTE',
          duracaoMinutos: 30,
          createdAt: '',
          updatedAt: '',
          deletedAt: null,
          deletada: false,
        },
        {
          id: 't-1',
          titulo: 'Origem',
          descricao: '',
          prioridade: 'MEDIA',
          prazo: null,
          status: 'PENDENTE',
          duracaoMinutos: 30,
          createdAt: '',
          updatedAt: '',
          deletedAt: null,
          deletada: false,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 100,
    })
    vi.mocked(projetoApi.listarProjetos).mockReset().mockResolvedValue({
      content: [
        {
          id: 'p-1',
          nome: 'TCC',
          descricao: null,
          status: 'ATIVO',
          createdAt: '',
          updatedAt: '',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
    })
  })

  it('mostra estado vazio e permite adicionar espera', async () => {
    const user = userEvent.setup()
    renderEspera()

    expect(screen.getByText('Nenhuma espera ainda.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /adicionar espera/i }))
    expect(await screen.findByRole('combobox', { name: /adicionar espera/i })).toBeInTheDocument()
  })

  it('cria espera com o alvo escolhido', async () => {
    onCriar.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderEspera()

    await user.click(screen.getByRole('button', { name: /adicionar espera/i }))
    await user.selectOptions(await screen.findByRole('combobox', { name: /adicionar espera/i }), 'PROJETO:p-1')
    await user.click(screen.getByRole('button', { name: /confirmar espera/i }))

    await waitFor(() => {
      expect(onCriar).toHaveBeenCalledWith({ alvoTipo: 'PROJETO', alvoId: 'p-1' })
    })
  })

  it('não oferece a própria origem como alvo', async () => {
    const user = userEvent.setup()
    renderEspera()

    await user.click(screen.getByRole('button', { name: /adicionar espera/i }))
    const select = await screen.findByRole('combobox', { name: /adicionar espera/i })
    expect(select).not.toHaveTextContent('Origem')
    expect(select).toHaveTextContent('Revisar API')
  })

  it('não mostra o formulário em somente leitura (estado inalterado)', () => {
    renderEspera([esperaAberta], true)

    expect(screen.queryByRole('button', { name: /adicionar espera/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /remover/i })).not.toBeInTheDocument()
    expect(onCriar).not.toHaveBeenCalled()
  })

  it('não cria espera sem alvo escolhido', async () => {
    const user = userEvent.setup()
    renderEspera()

    await user.click(screen.getByRole('button', { name: /adicionar espera/i }))
    await user.click(await screen.findByRole('button', { name: /confirmar espera/i }))

    expect(await screen.findByText('Escolha uma tarefa ou um projeto.')).toBeInTheDocument()
    expect(onCriar).not.toHaveBeenCalled()
  })

  it('mostra erro da API ao criar e mantém o formulário aberto', async () => {
    onCriar.mockRejectedValue(new ApiError(409, 'Espera duplicada'))
    const user = userEvent.setup()
    renderEspera()

    await user.click(screen.getByRole('button', { name: /adicionar espera/i }))
    await user.selectOptions(await screen.findByRole('combobox', { name: /adicionar espera/i }), 'PROJETO:p-1')
    await user.click(screen.getByRole('button', { name: /confirmar espera/i }))

    expect(await screen.findByText('Espera duplicada')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /adicionar espera/i })).toBeInTheDocument()
  })

  it('mostra erro da API ao remover e preserva a espera na lista', async () => {
    onRemover.mockRejectedValue(new ApiError(500, 'Falha ao remover'))
    const user = userEvent.setup()
    renderEspera([esperaAberta])

    await user.click(screen.getByRole('button', { name: /remover/i }))

    expect(await screen.findByText('Falha ao remover')).toBeInTheDocument()
    expect(screen.getByText('Revisar API')).toBeInTheDocument()
  })
})
