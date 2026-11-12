import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { SessaoAtivaProvider } from '../execucao/SessaoAtivaContext'
import { ToastProvider } from '../feedback/ToastProvider'
import { QuickAddProvider, useQuickAdd } from '../layout/QuickAddContext'
import * as projetoApi from '../projeto/api/projetoApi'
import { XpProgressProvider } from '../xp/XpProgressContext'
import * as tarefaApi from './api/tarefaApi'
import type { TarefaResponse } from './api/tarefaApi'
import { TarefasPage } from './pages/TarefasPage'
import { TarefaDrawer } from './TarefaDrawer'

vi.mock('./api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  criarTarefa: vi.fn(),
  buscarTarefa: vi.fn(),
  atualizarTarefa: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
  iniciarTarefaSessao: vi.fn(),
  concluirTarefaDireto: vi.fn(),
  criarEsperaTarefa: vi.fn(),
  excluirEsperaTarefa: vi.fn(),
  listarEsperasTarefa: vi.fn(),
}))

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

vi.mock('../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../execucao/api/execucaoApi')>()
  return {
    ...actual,
    listarExecucoes: vi.fn().mockResolvedValue({ planoAprovado: null, itens: [] }),
  }
})

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn().mockResolvedValue({
    id: 'u-1',
    nome: 'Teste',
    email: 't@t.com',
    xpTotal: 0,
    nivel: 1,
    xpNoNivel: 0,
    xpParaProximoNivel: 10,
  }),
  atualizarMe: vi.fn(),
}))

const projetosAtivos = {
  content: [
    {
      id: 'proj-tcc',
      nome: 'TCC',
      descricao: null,
      status: 'ATIVO' as const,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

const tarefa: TarefaResponse = {
  id: 't-1',
  titulo: 'Estudar',
  descricao: 'Spring',
  prioridade: 'ALTA',
  prazo: '2030-06-01T15:00:00Z',
  status: 'PENDENTE',
  duracaoMinutos: 240,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

function pagina(content: TarefaResponse[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 20 }
}

function prazoFuturoLocal(): string {
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function Local() {
  const location = useLocation()
  return <p data-testid="local">{`${location.pathname}${location.search}`}</p>
}

function GatilhoQuickAdd() {
  const { abrir } = useQuickAdd()
  return (
    <button type="button" onClick={abrir}>
      Abrir quick-add teste
    </button>
  )
}

function renderApp(path: string) {
  return render(
    <ToastProvider>
      <XpProgressProvider>
        <SessaoAtivaProvider>
          <MemoryRouter initialEntries={[path]}>
            <QuickAddProvider>
              <GatilhoQuickAdd />
              <Routes>
                <Route path="/tarefas" element={<TarefasPage />}>
                  <Route path="nova" element={<TarefaDrawer />} />
                  <Route path=":id" element={<TarefaDrawer />} />
                </Route>
              </Routes>
              <Local />
            </QuickAddProvider>
          </MemoryRouter>
        </SessaoAtivaProvider>
      </XpProgressProvider>
    </ToastProvider>,
  )
}

async function abrirNota(user: ReturnType<typeof userEvent.setup>, rotulo: RegExp | string) {
  await user.click(within(drawer()).getByRole('button', { name: rotulo }))
}

async function definirPrazo(user: ReturnType<typeof userEvent.setup>, valor: string) {
  await user.click(screen.getByRole('button', { name: /definir prazo|editar prazo/i }))
  const dialogo = screen.getByRole('dialog', { name: /^prazo$/i })
  const campo = within(dialogo).getByLabelText(/prazo \(opcional\)/i)
  await user.clear(campo)
  await user.type(campo, valor)
  await user.click(within(dialogo).getByRole('button', { name: /^aplicar$/i }))
}

async function noDialogoAvancadas(
  user: ReturnType<typeof userEvent.setup>,
  acao: (dialogo: HTMLElement) => Promise<void> | void,
) {
  await user.click(screen.getByRole('button', { name: /edições avançadas/i }))
  const dialogo = screen.getByRole('dialog', { name: /edições avançadas/i })
  await acao(dialogo)
  await user.click(within(dialogo).getByRole('button', { name: /^pronto$/i }))
}

function textareaDescricao() {
  return screen.getByRole('textbox', { name: /^descrição$/i })
}

function drawer(nome: RegExp = /(editar|nova) tarefa/i) {
  return screen.getByRole('region', { name: nome })
}

async function esperarDrawerFechar() {
  await waitFor(() => {
    expect(screen.queryByRole('region', { name: /(editar|nova) tarefa/i })).not.toBeInTheDocument()
  })
}

describe('TarefaDrawer', () => {
  function mockLista(content: TarefaResponse[], filhos: TarefaResponse[] = []) {
    vi.mocked(tarefaApi.listarTarefas).mockImplementation(async (filtro = {}) => {
      if (filtro.parentId) {
        return pagina(filhos)
      }
      return pagina(content)
    })
  }

  beforeEach(() => {
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tarefaApi.buscarTarefa).mockReset()
    vi.mocked(tarefaApi.atualizarTarefa).mockReset()
    vi.mocked(tarefaApi.excluirTarefa).mockReset()
    vi.mocked(tarefaApi.restaurarTarefa).mockReset()
    vi.mocked(tarefaApi.criarEsperaTarefa).mockReset()
    vi.mocked(tarefaApi.excluirEsperaTarefa).mockReset()
    mockLista([tarefa])
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(tarefa)
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue(projetosAtivos)
  })

  describe('rotas', () => {
    it('T2: deep link /tarefas/:id renderiza a tela de edição sem a lista', async () => {
      renderApp('/tarefas/t-1')

      expect(await screen.findByDisplayValue('Estudar')).toBeInTheDocument()
      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(screen.queryByRole('dialog', { name: /(editar|nova) tarefa/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('region', { name: /lista de tarefas/i })).not.toBeInTheDocument()
      expect(tarefaApi.buscarTarefa).toHaveBeenCalledWith('t-1')
    })

    it('T3: voltar da tela volta a /tarefas com os mesmos filtros', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1?visao=pendentes&prioridade=ALTA')

      await screen.findByDisplayValue('Estudar')
      await user.click(within(drawer()).getByRole('button', { name: 'Voltar' }))

      await esperarDrawerFechar()
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas?visao=pendentes&prioridade=ALTA')
    })

    it('T3: abrir pelo card e fechar mantém a query string da lista', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas?prioridade=ALTA')

      await user.click(await screen.findByRole('link', { name: /estudar/i }))
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/t-1?prioridade=ALTA')
      await screen.findByDisplayValue('Estudar')

      await user.click(within(drawer()).getByRole('button', { name: /^cancelar$/i }))

      await esperarDrawerFechar()
      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas\?prioridade=ALTA$/)
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(2)
    })

    it('T4: voltar com alterações pede confirmação; continuar editando mantém a tela', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.type(screen.getByLabelText(/^título$/i), ' mais')
      await user.click(within(drawer()).getByRole('button', { name: 'Voltar' }))

      const confirmacao = screen.getByRole('dialog', { name: /descartar alterações/i })
      await user.click(within(confirmacao).getByRole('button', { name: /continuar editando/i }))

      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/^título$/i)).toHaveValue('Estudar mais')

      await user.click(within(drawer()).getByRole('button', { name: 'Voltar' }))
      await user.click(
        within(screen.getByRole('dialog', { name: /descartar alterações/i })).getByRole('button', {
          name: /^descartar$/i,
        }),
      )
      await esperarDrawerFechar()
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
    })

    it('fechar sem alterações não pede confirmação', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(within(drawer()).getByRole('button', { name: 'Voltar' }))

      expect(screen.queryByRole('dialog', { name: /descartar/i })).not.toBeInTheDocument()
      await esperarDrawerFechar()
    })
  })

  describe('salvar e criar', () => {
    it('T5: salvar edição atualiza o item na lista antes da leitura silenciosa, sem skeleton', async () => {
      let leiturasDaLista = 0
      vi.mocked(tarefaApi.listarTarefas).mockImplementation(async (filtro = {}) => {
        if (filtro.parentId) {
          return pagina([])
        }
        leiturasDaLista += 1
        if (leiturasDaLista === 1) {
          return pagina([tarefa])
        }
        return new Promise(() => {})
      })
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...tarefa,
        titulo: 'Estudar mais',
        status: 'EM_PROGRESSO',
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      expect(screen.queryByLabelText(/^status$/i)).not.toBeInTheDocument()
      await user.clear(screen.getByLabelText(/^título$/i))
      await user.type(screen.getByLabelText(/^título$/i), 'Estudar mais')
      await noDialogoAvancadas(user, async (dialogo) => {
        await user.selectOptions(within(dialogo).getByLabelText(/^status$/i), 'EM_PROGRESSO')
      })
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ titulo: 'Estudar mais', status: 'EM_PROGRESSO' }),
        )
      })
      await esperarDrawerFechar()
      expect(await screen.findByText('Estudar mais', { selector: '.tarefa-card span' })).toBeInTheDocument()
      expect(screen.getByText('Tarefa salva')).toBeInTheDocument()
      expect(leiturasDaLista).toBe(2)
      expect(document.querySelector('.skeleton')).not.toBeInTheDocument()
      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas$/)
    })

    it('no create não exibe status, cria tarefa válida, fecha e faz leitura silenciosa', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({ ...tarefa, id: 't-9', titulo: 'Novo' })
      const user = userEvent.setup()
      renderApp('/tarefas/nova')

      expect(await screen.findByRole('region', { name: /nova tarefa/i })).toBeInTheDocument()
      expect(drawer(/nova tarefa/i)).toBeInTheDocument()
      expect(screen.queryByLabelText(/^status$/i)).not.toBeInTheDocument()

      await user.type(screen.getByLabelText(/^título$/i), 'Estudar')
      await abrirNota(user, /adicionar descrição/i)
      await user.type(textareaDescricao(), 'Spring Boot')
      await user.click(screen.getByRole('button', { name: /^pronto$/i }))
      await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'MEDIA')
      await definirPrazo(user, prazoFuturoLocal())
      await user.type(screen.getByLabelText(/duração/i), '120')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({
            titulo: 'Estudar',
            descricao: 'Spring Boot',
            prioridade: 'MEDIA',
            duracaoMinutos: 120,
          }),
        )
      })
      await esperarDrawerFechar()
      expect(screen.getByText('Tarefa criada')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(2)
    })

    it('permite criar tarefa sem prazo', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(tarefa)
      const user = userEvent.setup()
      renderApp('/tarefas/nova')

      await user.type(await screen.findByLabelText(/^título$/i), 'Sem prazo')
      await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'MEDIA')
      await user.type(screen.getByLabelText(/duração/i), '60')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({
            titulo: 'Sem prazo',
            prioridade: 'MEDIA',
            prazo: null,
            duracaoMinutos: 60,
          }),
        )
      })
    })

    it('mostra erro de validação quando título está vazio', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/nova')

      await abrirNota(user, /adicionar descrição/i)
      await user.type(await screen.findByRole('textbox', { name: /^descrição$/i }), 'Algo')
      await user.click(screen.getByRole('button', { name: /^pronto$/i }))
      await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'BAIXA')
      await definirPrazo(user, prazoFuturoLocal())
      await user.type(screen.getByLabelText(/duração/i), '60')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
      expect(drawer(/nova tarefa/i)).toBeInTheDocument()
    })

    it('bloqueia ATRASADA com prazo futuro na validação local', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await noDialogoAvancadas(user, async (dialogo) => {
        await user.selectOptions(within(dialogo).getByLabelText(/^status$/i), 'ATRASADA')
      })
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/ATRASADA/i)
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
    })

    it('mostra detalhe de erro da API no save e mantém a tela aberta', async () => {
      vi.mocked(tarefaApi.atualizarTarefa).mockRejectedValue(
        new ApiError(400, 'O prazo não pode estar no passado.'),
      )
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.clear(screen.getByLabelText(/^título$/i))
      await user.type(screen.getByLabelText(/^título$/i), 'Estudar API')
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/prazo não pode estar no passado/i)
      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
    })

    it('T6: quick-add "Mais detalhes" abre /tarefas/nova com o título preenchido', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(pagina([]))
      const user = userEvent.setup()
      renderApp('/tarefas')

      await user.click(await screen.findByRole('button', { name: /abrir quick-add teste/i }))
      const quickAdd = screen.getByRole('dialog', { name: /nova tarefa/i })
      await user.type(within(quickAdd).getByLabelText(/^título$/i), 'Ler artigo')
      await user.click(within(quickAdd).getByRole('button', { name: /mais detalhes/i }))

      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas\/nova$/)
      expect(drawer(/nova tarefa/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/^título$/i)).toHaveValue('Ler artigo')
      expect(screen.getByRole('button', { name: /adicionar descrição/i })).toBeInTheDocument()
    })
  })

  describe('view da descrição', () => {
    it('abre a nota sem mudar a URL e foca o textarea', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/t-1')

      await abrirNota(user, /spring/i)

      expect(drawer(/descrição/i)).toBeInTheDocument()
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/t-1')
      expect(textareaDescricao()).toHaveFocus()
      expect(screen.queryByRole('button', { name: /^salvar$/i })).not.toBeInTheDocument()
    })

    it('Pronto volta ao meta sem persistir e mostra o preview', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await abrirNota(user, /spring/i)
      await user.clear(textareaDescricao())
      await user.type(textareaDescricao(), 'Spring Boot atualizado')
      await user.click(screen.getByRole('button', { name: /^pronto$/i }))

      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /spring boot atualizado/i })).toBeInTheDocument()
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
      expect(screen.getByRole('button', { name: /spring boot atualizado/i })).toHaveFocus()
    })

    it('Esc e Voltar na nota voltam ao meta sem sair da tela', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await abrirNota(user, /spring/i)
      await user.type(textareaDescricao(), ' extra')
      await user.keyboard('{Escape}')

      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /spring extra/i })).toBeInTheDocument()
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/t-1')

      await abrirNota(user, /spring extra/i)
      await user.click(within(drawer(/descrição/i)).getByRole('button', { name: 'Voltar' }))

      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(screen.queryByRole('dialog', { name: /descartar/i })).not.toBeInTheDocument()
    })

    it('salvar no meta envia a descrição editada na nota', async () => {
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...tarefa,
        descricao: 'Nova nota',
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await abrirNota(user, /spring/i)
      await user.clear(textareaDescricao())
      await user.type(textareaDescricao(), 'Nova nota')
      await user.click(screen.getByRole('button', { name: /^pronto$/i }))
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ descricao: 'Nova nota' }),
        )
      })
    })

    it('fechar o meta com só a descrição alterada pede descartar', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await abrirNota(user, /spring/i)
      await user.type(textareaDescricao(), '!')
      await user.click(screen.getByRole('button', { name: /^pronto$/i }))
      await user.click(within(drawer(/editar tarefa/i)).getByRole('button', { name: 'Voltar' }))

      expect(screen.getByRole('dialog', { name: /descartar alterações/i })).toBeInTheDocument()
    })

    it('com tarefa deletada a linha de descrição fica disabled e a nota não abre', async () => {
      const deletada = { ...tarefa, deletada: true, deletedAt: '2026-02-01T10:00:00Z' }
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(deletada)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      expect(
        await within(drawer(/editar tarefa/i)).findByText(/esta tarefa está excluída/i),
      ).toBeInTheDocument()
      const linha = screen.getByRole('button', { name: /spring/i })
      expect(linha).toBeDisabled()
      await user.click(linha)

      expect(screen.queryByRole('dialog', { name: /^descrição$/i })).not.toBeInTheDocument()
    })
  })

  describe('excluir, restaurar e projeto', () => {
    it('excluir no drawer: cancelar confirmação não chama API', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))
      const dialog = screen.getByRole('dialog', { name: /excluir tarefa/i })
      await user.click(within(dialog).getByRole('button', { name: /^cancelar$/i }))

      expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
      expect(screen.getByRole('button', { name: /excluir tarefa/i })).toBeInTheDocument()
    })

    it('confirma exclusão e permanece no drawer com status excluída', async () => {
      vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))
      const dialog = screen.getByRole('dialog', { name: /excluir tarefa/i })
      await user.click(within(dialog).getByRole('button', { name: /^excluir$/i }))

      await waitFor(() => {
        expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-1')
      })
      expect(
        await within(drawer(/editar tarefa/i)).findByText(/esta tarefa está excluída/i),
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /restaurar tarefa/i })).toBeInTheDocument()
    })

    it('mostra restaurar quando a tarefa está deletada e restaura', async () => {
      const deletada = { ...tarefa, deletada: true, deletedAt: '2026-02-01T10:00:00Z' }
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(deletada)
      vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue(tarefa)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      expect(
        await within(drawer(/editar tarefa/i)).findByText(/esta tarefa está excluída/i),
      ).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /excluir tarefa/i })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /restaurar tarefa/i }))

      await waitFor(() => {
        expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-1')
      })
      expect(await screen.findByRole('button', { name: /^salvar$/i })).toBeInTheDocument()
    })

    it('Desvincular envia PATCH com projetoId: null', async () => {
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
        ...tarefa,
        projetoId: 'proj-tcc',
        projetoNome: 'TCC',
      })
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...tarefa,
        projetoId: null,
        projetoNome: null,
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await noDialogoAvancadas(user, async (dialogo) => {
        await user.click(within(dialogo).getByRole('button', { name: /^desvincular$/i }))
      })

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith('t-1', { projetoId: null })
      })
    })
  })

  describe('subtarefas', () => {
    const filho: TarefaResponse = {
      ...tarefa,
      id: 't-filho',
      titulo: 'Capítulo 1',
      duracaoMinutos: 60,
      parentId: 't-1',
      parentTitulo: 'Estudar',
      temFilhos: false,
    }

    it('tela do pai cria subtarefa só com título e parentId, no padrão do add da lista', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(filho)
      vi.mocked(tarefaApi.buscarTarefa)
        .mockResolvedValueOnce(tarefa)
        .mockResolvedValueOnce({ ...tarefa, temFilhos: true, duracaoMinutos: 60 })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      expect(within(drawer()).getByRole('heading', { name: /^subtarefas$/i })).toBeInTheDocument()

      await user.type(screen.getByPlaceholderText(/adicionar subtarefa/i), 'Capítulo 1')
      await user.click(within(drawer()).getByRole('button', { name: /^adicionar$/i }))

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({ titulo: 'Capítulo 1', parentId: 't-1' }),
        )
      })
      const body = vi.mocked(tarefaApi.criarTarefa).mock.calls[0][0]
      expect(body).not.toHaveProperty('duracaoMinutos')
      expect(body).not.toHaveProperty('prioridade')
      expect(await screen.findByRole('link', { name: /capítulo 1/i })).toHaveAttribute(
        'href',
        '/tarefas/t-filho',
      )
    })

    it('lista de tarefas não oferece criar subtarefa', async () => {
      renderApp('/tarefas')

      const lista = await screen.findByRole('region', { name: /lista de tarefas/i })
      expect(within(lista).queryByPlaceholderText(/adicionar subtarefa/i)).not.toBeInTheDocument()
      expect(within(lista).queryByRole('heading', { name: /^subtarefas$/i })).not.toBeInTheDocument()
    })

    it('duração do pai com filhos não é editável', async () => {
      const pai = { ...tarefa, temFilhos: true, duracaoMinutos: 120 }
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(pai)
      mockLista([pai], [filho])
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      const duracao = within(drawer()).getByLabelText(/^duração \(minutos\)$/i)
      expect(duracao).toBeDisabled()
      expect(duracao).toHaveValue(120)
      expect(within(drawer()).getByText(/soma das subtarefas/i)).toBeInTheDocument()
    })

    it('salvar pai com filhos omite duração no PATCH', async () => {
      const pai = { ...tarefa, temFilhos: true, duracaoMinutos: 120 }
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(pai)
      mockLista([pai], [filho])
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue(pai)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.clear(screen.getByLabelText(/^título$/i))
      await user.type(screen.getByLabelText(/^título$/i), 'Estudar Security')
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ titulo: 'Estudar Security' }),
        )
      })
      const body = vi.mocked(tarefaApi.atualizarTarefa).mock.calls[0][1]
      expect(body).not.toHaveProperty('duracaoMinutos')
    })

    it('tela de subtarefa não mostra criação de neto', async () => {
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(filho)
      mockLista([filho])
      renderApp('/tarefas/t-filho')

      await screen.findByDisplayValue('Capítulo 1')
      expect(within(drawer()).queryByRole('heading', { name: /^subtarefas$/i })).not.toBeInTheDocument()
      expect(within(drawer()).queryByPlaceholderText(/adicionar subtarefa/i)).not.toBeInTheDocument()
    })

    it('nova tarefa não mostra seção de subtarefas', async () => {
      renderApp('/tarefas/nova')

      expect(await screen.findByRole('region', { name: /nova tarefa/i })).toBeInTheDocument()
      expect(within(drawer(/nova tarefa/i)).queryByRole('heading', { name: /^subtarefas$/i })).not.toBeInTheDocument()
    })
  })

  describe('cascata no pai', () => {
    const paiComFilhos: TarefaResponse = {
      ...tarefa,
      temFilhos: true,
      duracaoMinutos: 120,
    }

    it('finalizar pai com filhos: voltar não chama API; confirmar dispara PATCH', async () => {
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(paiComFilhos)
      mockLista([paiComFilhos])
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...paiComFilhos,
        status: 'FINALIZADA',
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await noDialogoAvancadas(user, async (dialogo) => {
        await user.selectOptions(within(dialogo).getByLabelText(/^status$/i), 'FINALIZADA')
      })
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      const dialogo = screen.getByRole('dialog', { name: /finalizar tarefa e subtarefas/i })
      await user.click(within(dialogo).getByRole('button', { name: /^voltar$/i }))
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()

      await user.click(screen.getByRole('button', { name: /^salvar$/i }))
      await user.click(
        within(screen.getByRole('dialog', { name: /finalizar tarefa e subtarefas/i })).getByRole(
          'button',
          { name: /^finalizar$/i },
        ),
      )

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ status: 'FINALIZADA' }),
        )
      })
    })

    it('cancelar pai com filhos: voltar não chama API; confirmar dispara PATCH', async () => {
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(paiComFilhos)
      mockLista([paiComFilhos])
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...paiComFilhos,
        status: 'CANCELADA',
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await noDialogoAvancadas(user, async (dialogo) => {
        await user.selectOptions(within(dialogo).getByLabelText(/^status$/i), 'CANCELADA')
      })
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      const dialogo = screen.getByRole('dialog', { name: /cancelar tarefa e subtarefas/i })
      await user.click(within(dialogo).getByRole('button', { name: /^voltar$/i }))
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()

      await user.click(screen.getByRole('button', { name: /^salvar$/i }))
      await user.click(
        within(screen.getByRole('dialog', { name: /cancelar tarefa e subtarefas/i })).getByRole(
          'button',
          { name: /^cancelar tarefas$/i },
        ),
      )

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ status: 'CANCELADA' }),
        )
      })
    })

    it('excluir pai com filhos: voltar não chama API; confirmar dispara DELETE', async () => {
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(paiComFilhos)
      mockLista([paiComFilhos])
      vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))

      const dialogo = screen.getByRole('dialog', { name: /excluir tarefa e subtarefas/i })
      await user.click(within(dialogo).getByRole('button', { name: /^voltar$/i }))
      expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()

      await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))
      await user.click(
        within(screen.getByRole('dialog', { name: /excluir tarefa e subtarefas/i })).getByRole(
          'button',
          { name: /^excluir$/i },
        ),
      )

      await waitFor(() => {
        expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-1')
      })
    })

    it('pai sem filhos não exige o diálogo de subtarefas ao finalizar, cancelar ou excluir', async () => {
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({ ...tarefa, status: 'FINALIZADA' })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await noDialogoAvancadas(user, async (dialogo) => {
        await user.selectOptions(within(dialogo).getByLabelText(/^status$/i), 'FINALIZADA')
      })
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      expect(screen.queryByRole('dialog', { name: /subtarefas/i })).not.toBeInTheDocument()
      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ status: 'FINALIZADA' }),
        )
      })
    })
  })

  describe('espera', () => {
    const alvo: TarefaResponse = { ...tarefa, id: 't-2', titulo: 'Revisar API' }

    function secaoEspera() {
      return screen.getByRole('region', { name: /^espera$/i })
    }

    it('cria uma espera pela ficha e mostra o bloqueio', async () => {
      const user = userEvent.setup()
      mockLista([tarefa, alvo])
      const criada = {
        id: 'e-1',
        alvoTipo: 'TAREFA' as const,
        alvoId: 't-2',
        alvoTitulo: 'Revisar API',
        resolvida: false,
      }
      vi.mocked(tarefaApi.criarEsperaTarefa).mockResolvedValue(criada)
      vi.mocked(tarefaApi.buscarTarefa)
        .mockResolvedValueOnce(tarefa)
        .mockResolvedValue({ ...tarefa, esperas: [criada], bloqueada: true })

      renderApp('/tarefas/t-1')
      await screen.findByDisplayValue('Estudar')

      await user.click(within(secaoEspera()).getByRole('button', { name: /adicionar espera/i }))
      await waitFor(() => {
        expect(within(secaoEspera()).getByRole('combobox')).toBeEnabled()
      })
      await user.selectOptions(within(secaoEspera()).getByRole('combobox'), 'TAREFA:t-2')
      await user.click(within(secaoEspera()).getByRole('button', { name: /confirmar espera/i }))

      await waitFor(() => {
        expect(tarefaApi.criarEsperaTarefa).toHaveBeenCalledWith('t-1', {
          alvoTipo: 'TAREFA',
          alvoId: 't-2',
        })
      })
      expect(await within(secaoEspera()).findByText('Revisar API')).toBeInTheDocument()
      expect(within(secaoEspera()).getByText(/bloqueada por/i)).toBeInTheDocument()
    })

    it('remove a espera e o bloqueio some', async () => {
      const user = userEvent.setup()
      const existente = {
        id: 'e-1',
        alvoTipo: 'PROJETO' as const,
        alvoId: 'proj-tcc',
        alvoTitulo: 'TCC',
        resolvida: false,
      }
      mockLista([tarefa])
      vi.mocked(tarefaApi.buscarTarefa)
        .mockResolvedValueOnce({ ...tarefa, esperas: [existente], bloqueada: true })
        .mockResolvedValue({ ...tarefa, esperas: [], bloqueada: false })
      vi.mocked(tarefaApi.excluirEsperaTarefa).mockResolvedValue(undefined)

      renderApp('/tarefas/t-1')
      await screen.findByDisplayValue('Estudar')
      expect(within(secaoEspera()).getByText('TCC')).toBeInTheDocument()

      await user.click(within(secaoEspera()).getByRole('button', { name: /^remover$/i }))

      await waitFor(() => {
        expect(tarefaApi.excluirEsperaTarefa).toHaveBeenCalledWith('t-1', 'e-1')
      })
      await waitFor(() => {
        expect(within(secaoEspera()).queryByText('TCC')).not.toBeInTheDocument()
      })
      expect(within(secaoEspera()).queryByText(/bloqueada por/i)).not.toBeInTheDocument()
    })

    it('a ficha de nova tarefa não pede espera e a copy não usa "pendente"', async () => {
      renderApp('/tarefas/nova')

      await screen.findByRole('region', { name: /nova tarefa/i })
      expect(screen.queryByRole('region', { name: /^espera$/i })).not.toBeInTheDocument()

      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(tarefa)
      renderApp('/tarefas/t-1')
      await screen.findByDisplayValue('Estudar')
      expect(within(secaoEspera()).queryByText(/pendente/i)).not.toBeInTheDocument()
    })
  })
})
