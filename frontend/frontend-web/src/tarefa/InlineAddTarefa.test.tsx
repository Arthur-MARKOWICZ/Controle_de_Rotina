import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as tagApi from '../tag/api/tagApi'
import * as tarefaApi from './api/tarefaApi'
import type { TarefaResponse } from './api/tarefaApi'
import { fimDoDiaBrtIso } from './dataCivilBrt'
import { InlineAddTarefa, PLACEHOLDER_INLINE_ADD } from './InlineAddTarefa'
import { tarefa as tarefaFake, tag as tagFake } from '../test/revisaoFixtures'

vi.mock('../tag/api/tagApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../tag/api/tagApi')>()),
  listarTags: vi.fn(),
  criarTag: vi.fn(),
}))

/** Quarta-feira 18/11/2026 10:00 BRT. */
const REF = new Date('2026-11-18T13:00:00.000Z')

vi.mock('./api/tarefaApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/tarefaApi')>()
  return {
    ...actual,
    criarTarefa: vi.fn(),
  }
})

const criada: TarefaResponse = tarefaFake({
  id: 't-1',
  titulo: 'Estudar',
  descricao: '',
  prioridade: 'MEDIA',
  prazo: null,
  status: 'PENDENTE',
  duracaoMinutos: 30,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
})

const onCriada = vi.fn()

function renderAdd(projetoId?: string) {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <InlineAddTarefa projetoId={projetoId} onCriada={onCriada} referencia={REF} />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('InlineAddTarefa', () => {
  beforeEach(() => {
    onCriada.mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tagApi.listarTags).mockReset()
    vi.mocked(tagApi.listarTags).mockResolvedValue([])
    vi.mocked(tagApi.criarTag).mockReset()
  })

  it('cria tarefa com prioridade e duração padrão', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(criada)
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
        titulo: 'Estudar',
        prioridade: 'MEDIA',
        duracaoMinutos: 30,
      })
    })
    expect(onCriada).toHaveBeenCalledWith(criada)
    expect(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD)).toHaveValue('')
  })

  it('inclui projetoId do filtro ativo', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({ ...criada, projetoId: 'p-1' })
    const user = userEvent.setup()
    renderAdd('p-1')

    await user.type(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
        expect.objectContaining({ projetoId: 'p-1' }),
      )
    })
  })

  it('não cria com título vazio (estado inalterado)', async () => {
    const user = userEvent.setup()
    renderAdd()

    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    expect(onCriada).not.toHaveBeenCalled()
  })

  it('não cria com título só de espaços', async () => {
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD), '   ')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('limpa o campo com Escape sem chamar a API', async () => {
    const user = userEvent.setup()
    renderAdd()
    const campo = screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD)
    await user.type(campo, 'Rascunho')
    await user.keyboard('{Escape}')

    expect(campo).toHaveValue('')
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('mostra erro da API e preserva o título', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new ApiError(400, 'Título inválido'))
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Título inválido')).toBeInTheDocument()
    expect(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD)).toHaveValue('Estudar')
    expect(onCriada).not.toHaveBeenCalled()
  })

  it('mostra fallback quando o erro não é ApiError', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Não foi possível criar a tarefa. Tente novamente.')).toBeInTheDocument()
    expect(onCriada).not.toHaveBeenCalled()
  })

  describe('linguagem curta (#74)', () => {
    const campo = () => screen.getByPlaceholderText(PLACEHOLDER_INLINE_ADD)
    const selectPrioridade = () => screen.getByRole('combobox', { name: 'Prioridade' })
    const selectDuracao = () => screen.getByRole('combobox', { name: 'Duração' })
    const adicionar = () => screen.getByRole('button', { name: /^adicionar$/i })

    beforeEach(() => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(criada)
    })

    it('deveAtualizarSelectDePrioridadeEEnviarOValorDoToken', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Deploy amanhã !alta')
      expect(selectPrioridade()).toHaveValue('ALTA')
      await user.click(adicionar())
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Deploy',
          prioridade: 'ALTA',
          duracaoMinutos: 30,
          prazo: fimDoDiaBrtIso(2026, 11, 19),
        })
      })
    })

    it('deveVoltarSelectDeDuracaoAoPadraoAoRemoverOChip', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Deploy 2h')
      expect(selectDuracao()).toHaveValue('120')
      await user.click(screen.getByRole('button', { name: 'Remover duração 2h' }))
      expect(selectDuracao()).toHaveValue('30')
      await user.click(adicionar())
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Deploy 2h',
          prioridade: 'MEDIA',
          duracaoMinutos: 30,
        })
      })
    })

    it('deveAdicionarOpcaoExtraParaDuracaoForaDaLista', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Maratona 5h')
      expect(selectDuracao()).toHaveValue('300')
      expect(screen.getByRole('option', { name: '5h' })).toBeInTheDocument()
      await user.click(adicionar())
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({ titulo: 'Maratona', duracaoMinutos: 300 }),
        )
      })
    })

    it('deveRejeitarOTokenQuandoUsuarioMudaOSelectDepois', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Deploy !alta')
      await user.selectOptions(selectPrioridade(), 'BAIXA')
      expect(screen.queryByRole('button', { name: 'Remover prioridade Alta' })).not.toBeInTheDocument()
      await user.click(adicionar())
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({ titulo: 'Deploy !alta', prioridade: 'BAIXA' }),
        )
      })
    })

    it('naoDeveCriarTagPendenteSemConfirmacao', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Treino #academia')
      await user.click(adicionar())
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Treino',
          prioridade: 'MEDIA',
          duracaoMinutos: 30,
        })
      })
      expect(tagApi.criarTag).not.toHaveBeenCalled()
    })

    it('deveRecusarTituloAcimaDe120CaracteresSemContarOsTokens', async () => {
      const user = userEvent.setup()
      renderAdd()
      expect(campo()).toHaveAttribute('maxLength', '200')
      await user.click(campo())
      await user.paste(`${'a'.repeat(121)} 1h`)
      await user.click(adicionar())
      expect(screen.getByRole('alert')).toHaveTextContent('O título deve ter no máximo 120 caracteres.')
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    })

    it('deveBloquearEnvioComDuracaoInvalida', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Ligar 20min')
      await user.click(adicionar())
      expect(screen.getByRole('alert')).toHaveTextContent(
        'A duração deve ser no mínimo 15 minutos e múltiplo de 15.',
      )
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    })

    it('deveManterTextoEChipsQuandoServidorFalha', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new Error('500'))
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Deploy 1h !alta')
      await user.click(adicionar())
      expect(await screen.findByText(/não foi possível criar a tarefa/i)).toBeInTheDocument()
      expect(campo()).toHaveValue('Deploy 1h !alta')
      expect(screen.getByRole('button', { name: 'Remover prioridade Alta' })).toBeInTheDocument()
    })

    it('deveLimparCampoChipsERejeitadosAposCriar', async () => {
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Deploy 2h')
      await user.click(screen.getByRole('button', { name: 'Remover duração 2h' }))
      await user.click(adicionar())
      await waitFor(() => expect(onCriada).toHaveBeenCalled())
      expect(campo()).toHaveValue('')
      expect(screen.queryByRole('list', { name: 'Valores interpretados' })).not.toBeInTheDocument()
      await user.type(campo(), 'Outra 2h')
      expect(screen.getByRole('button', { name: 'Remover duração 2h' })).toBeInTheDocument()
    })

    it('deveCriarTagConfirmadaEVincular', async () => {
      vi.mocked(tagApi.criarTag).mockResolvedValue(tagFake({ id: 'tag-nova', nome: 'academia', cor: '#6B7280' }))
      const user = userEvent.setup()
      renderAdd()
      await user.type(campo(), 'Treino #academia')
      await user.click(screen.getByRole('button', { name: 'Criar tag academia' }))
      await user.click(adicionar())
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({ titulo: 'Treino', tagIds: ['tag-nova'] }),
        )
      })
    })
  })
})
