import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as tagApi from '../tag/api/tagApi'
import * as tarefaApi from './api/tarefaApi'
import { fimDoDiaBrtIso } from './dataCivilBrt'
import { QuickAddTarefaDialog } from './QuickAddTarefaDialog'
import type { TarefaDrawerState } from './TarefaDrawer'
import { tarefa as tarefaFake, tag as tagFake } from '../test/revisaoFixtures'

vi.mock('./api/tarefaApi', () => ({
  criarTarefa: vi.fn(),
}))

vi.mock('../tag/api/tagApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../tag/api/tagApi')>()),
  listarTags: vi.fn(),
  criarTag: vi.fn(),
}))

/** Quarta-feira 18/11/2026 10:00 BRT. */
const REF = new Date('2026-11-18T13:00:00.000Z')
const FACULDADE = tagFake({ id: 'tag-fac', nome: 'Faculdade', cor: '#123456' })

let ultimoState: unknown = null

function Local() {
  const location = useLocation()
  ultimoState = location.state
  return (
    <p data-testid="local">
      {location.pathname}
      {location.state && typeof location.state === 'object' && 'titulo' in location.state
        ? `:${(location.state as { titulo?: string }).titulo ?? ''}`
        : ''}
    </p>
  )
}

function renderDialog(open = true) {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <Routes>
          <Route
            path="/"
            element={
              <>
                <QuickAddTarefaDialog open={open} onClose={vi.fn()} referencia={REF} />
                <Local />
              </>
            }
          />
          <Route path="/tarefas/nova" element={<Local />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

const TAREFA_CRIADA = tarefaFake({
  id: 't-1',
  titulo: 'x',
  descricao: '',
  prioridade: 'MEDIA' as const,
  prazo: null,
  status: 'PENDENTE' as const,
  duracaoMinutos: 30,
  createdAt: '2026-11-18T12:00:00Z',
  updatedAt: '2026-11-18T12:00:00Z',
  deletedAt: null,
  deletada: false,
})

describe('QuickAddTarefaDialog', () => {
  beforeEach(() => {
    ultimoState = null
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(TAREFA_CRIADA)
    vi.mocked(tagApi.listarTags).mockReset()
    vi.mocked(tagApi.listarTags).mockResolvedValue([FACULDADE])
    vi.mocked(tagApi.criarTag).mockReset()
  })

  it('T4: submit só com título chama API sem prioridade/duração; Mais detalhes leva ao drawer com título', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(tarefaFake({
      id: 't-99',
      titulo: 'Ler docs',
      descricao: '',
      prioridade: 'MEDIA',
      prazo: null,
      status: 'PENDENTE',
      duracaoMinutos: 30,
      createdAt: '2026-10-28T12:00:00Z',
      updatedAt: '2026-10-28T12:00:00Z',
      deletedAt: null,
      deletada: false,
    }))
    const user = userEvent.setup()
    renderDialog()

    expect(screen.queryByLabelText(/duração/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/prioridade/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/prazo/i)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({ titulo: 'Ler docs' })
    })
    expect(await screen.findByText('Tarefa criada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
  })

  it('T4: Mais detalhes navega para /tarefas/nova com o título', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByLabelText(/^título$/i), 'Ler artigo')
    await user.click(screen.getByRole('button', { name: /mais detalhes/i }))

    expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/nova:Ler artigo')
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('título vazio exibe erro inline e não chama API', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('título só com espaços não chama API', async () => {
    const user = userEvent.setup()
    renderDialog()
    await user.type(screen.getByLabelText(/^título$/i), '   ')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('mostra ApiError e preserva o título', async () => {
    const { ApiError } = await import('../auth/api/authApi')
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new ApiError(409, 'Título duplicado.'))
    const user = userEvent.setup()
    renderDialog()
    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
    expect(await screen.findByText('Título duplicado.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^título$/i)).toHaveValue('Ler docs')
  })

  it('erro de rede usa fallback e não navega', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new Error('offline'))
    const user = userEvent.setup()
    renderDialog()
    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
    expect(await screen.findByText(/não foi possível criar a tarefa/i)).toBeInTheDocument()
    expect(screen.getByTestId('local')).toHaveTextContent('/')
  })

  it('não renderiza o formulário quando fechado', () => {
    renderDialog(false)
    expect(screen.queryByLabelText(/^título$/i)).not.toBeInTheDocument()
  })

  describe('linguagem curta (#74)', () => {
    async function digitar(texto: string) {
      const user = userEvent.setup()
      renderDialog()
      await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
      await user.type(screen.getByLabelText(/^título$/i), texto)
      return user
    }

    it('deveEnviarTodosOsCamposDaFraseCompleta', async () => {
      const user = await digitar('Estudar Spring 2h sex !alta #faculdade')
      expect(await screen.findByText('#Faculdade')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Estudar Spring',
          duracaoMinutos: 120,
          prazo: fimDoDiaBrtIso(2026, 11, 20),
          prioridade: 'ALTA',
          tagIds: ['tag-fac'],
        })
      })
      expect(tagApi.criarTag).not.toHaveBeenCalled()
    })

    it('naoDeveReinterpretarTokenRemovidoAoContinuarDigitando', async () => {
      const user = await digitar('Ler cap 2h')
      await user.click(screen.getByRole('button', { name: 'Remover duração 2h' }))
      await user.type(screen.getByLabelText(/^título$/i), ' amanhã')
      expect(screen.queryByRole('button', { name: 'Remover duração 2h' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Remover prazo qui 19/11' })).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Ler cap 2h',
          prazo: fimDoDiaBrtIso(2026, 11, 19),
        })
      })
    })

    it('naoDeveCriarTagPendenteSemConfirmacao', async () => {
      const user = await digitar('Treino #academia')
      expect(screen.getByText('criar #academia?')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      await waitFor(() => expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({ titulo: 'Treino' }))
      expect(tagApi.criarTag).not.toHaveBeenCalled()
    })

    it('deveCriarTagConfirmadaAntesDaTarefa', async () => {
      vi.mocked(tagApi.criarTag).mockResolvedValue(tagFake({ id: 'tag-nova', nome: 'academia', cor: '#6B7280' }))
      const user = await digitar('Treino #academia')
      await user.click(screen.getByRole('button', { name: 'Criar tag academia' }))
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({ titulo: 'Treino', tagIds: ['tag-nova'] })
      })
      expect(tagApi.criarTag).toHaveBeenCalledWith({ nome: 'academia', cor: tagApi.TAG_COR_PADRAO })
      expect(vi.mocked(tagApi.criarTag).mock.invocationCallOrder[0]).toBeLessThan(
        vi.mocked(tarefaApi.criarTarefa).mock.invocationCallOrder[0],
      )
    })

    it('deveDeixarTagsPendentesQuandoCatalogoFalha', async () => {
      vi.mocked(tagApi.listarTags).mockRejectedValue(new Error('offline'))
      const user = await digitar('Prova 1h #faculdade')
      expect(screen.getByText('criar #faculdade?')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({ titulo: 'Prova', duracaoMinutos: 60 })
      })
    })

    it('deveBloquearEnvioQuandoDuracaoInvalida', async () => {
      const user = await digitar('Ligar 20min')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      expect(screen.getByRole('alert')).toHaveTextContent(
        'A duração deve ser no mínimo 15 minutos e múltiplo de 15.',
      )
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    })

    it('deveRecusarTituloAcimaDe120CaracteresSemContarOsTokens', async () => {
      const user = userEvent.setup()
      renderDialog()
      const campo = screen.getByLabelText(/^título$/i)
      expect(campo).toHaveAttribute('maxLength', '200')
      await user.click(campo)
      await user.paste(`${'a'.repeat(110)} 2h sex !alta`)
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      await waitFor(() => expect(tarefaApi.criarTarefa).toHaveBeenCalled())
      expect(vi.mocked(tarefaApi.criarTarefa).mock.calls[0][0].titulo).toHaveLength(110)

      vi.mocked(tarefaApi.criarTarefa).mockClear()
      await user.clear(campo)
      await user.paste(`${'a'.repeat(121)} 2h`)
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      expect(screen.getByText('O título deve ter no máximo 120 caracteres.')).toBeInTheDocument()
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    })

    it('deveExigirTituloQuandoTextoTemSoTokens', async () => {
      const user = await digitar('2h amanhã')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      expect(screen.getByText('Informe um título.')).toBeInTheDocument()
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    })

    it('naoDeveCriarTarefaQuandoCriarTagFalha', async () => {
      vi.mocked(tagApi.criarTag).mockRejectedValue(new ApiError(409, 'Já existe uma tag com esse nome.'))
      const user = await digitar('Treino #academia')
      await user.click(screen.getByRole('button', { name: 'Criar tag academia' }))
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      expect(await screen.findByText('Já existe uma tag com esse nome.')).toBeInTheDocument()
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
      expect(screen.getByLabelText(/^título$/i)).toHaveValue('Treino #academia')
    })

    it('deveManterTextoEChipsQuandoServidorRecusa', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new ApiError(400, 'Dados inválidos.'))
      const user = await digitar('Deploy 1h !alta')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
      expect(await screen.findByText('Dados inválidos.')).toBeInTheDocument()
      expect(screen.getByLabelText(/^título$/i)).toHaveValue('Deploy 1h !alta')
      expect(screen.getByRole('button', { name: 'Remover prioridade Alta' })).toBeInTheDocument()
    })

    it('deveVoltarAInterpretarTokenRejeitadoAoReabrirODialogo', async () => {
      const arvore = (open: boolean) => (
        <MemoryRouter>
          <ToastProvider>
            <QuickAddTarefaDialog open={open} onClose={vi.fn()} referencia={REF} />
          </ToastProvider>
        </MemoryRouter>
      )
      const user = userEvent.setup()
      const { rerender } = render(arvore(true))
      const campo = () => screen.getByLabelText(/^título$/i)
      await user.type(campo(), 'Ler 2h')
      await user.click(screen.getByRole('button', { name: 'Remover duração 2h' }))
      await user.type(campo(), ' x')
      expect(screen.queryByRole('button', { name: 'Remover duração 2h' })).not.toBeInTheDocument()

      rerender(arvore(false))
      rerender(arvore(true))
      await user.type(campo(), 'Ler 2h')
      expect(screen.getByRole('button', { name: 'Remover duração 2h' })).toBeInTheDocument()
    })

    it('deveLevarValoresInterpretadosParaMaisDetalhesSemTagPendente', async () => {
      const user = await digitar('Estudar 2h amanhã !alta #faculdade #nova')
      await screen.findByText('#Faculdade')
      await user.click(screen.getByRole('button', { name: /mais detalhes/i }))
      expect(ultimoState).toEqual({
        titulo: 'Estudar',
        duracaoMinutos: 120,
        prazo: fimDoDiaBrtIso(2026, 11, 19),
        prioridade: 'ALTA',
        tags: [FACULDADE],
      } satisfies TarefaDrawerState)
      expect(tagApi.criarTag).not.toHaveBeenCalled()
    })
  })
})
