import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import { SessaoAtivaProvider } from '../../execucao/SessaoAtivaContext'
import { ToastProvider } from '../../feedback/ToastProvider'
import { QuickAddProvider } from '../../layout/QuickAddContext'
import * as projetoApi from '../../projeto/api/projetoApi'
import { XpProgressProvider } from '../../xp/XpProgressContext'
import * as tarefaApi from '../api/tarefaApi'
import type { PageTarefa, TarefaResponse } from '../api/tarefaApi'
import { DEBOUNCE_FILTRO_MS } from '../FiltrosBarra'
import { TarefasPage } from './TarefasPage'

vi.mock('../api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  criarTarefa: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
  iniciarTarefaSessao: vi.fn(),
  concluirTarefaDireto: vi.fn(),
  reordenarTarefas: vi.fn(),
}))

vi.mock('../../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

vi.mock('../../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
}))

vi.mock('../../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../execucao/api/execucaoApi')>()
  return {
    ...actual,
    listarExecucoes: vi.fn().mockResolvedValue({ planoAprovado: null, itens: [] }),
  }
})

vi.mock('../../auth/api/meApi', () => ({
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

function tarefaLista(
  parcial: Partial<TarefaResponse> & { id: string; titulo: string },
): TarefaResponse {
  return {
    descricao: '',
    prioridade: 'MEDIA',
    prazo: '2026-10-28T20:00:00Z',
    status: 'PENDENTE',
    duracaoMinutos: 60,
    restanteMinutos: 60,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    deletedAt: null,
    deletada: false,
    ...parcial,
  }
}

function paginaDe(content: TarefaResponse[], size = 20): PageTarefa {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    number: 0,
    size,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function Local() {
  const location = useLocation()
  return <p data-testid="local">{`${location.pathname}${location.search}`}</p>
}

function renderLista(path = '/tarefas') {
  return render(
    <ToastProvider>
      <XpProgressProvider>
        <SessaoAtivaProvider>
          <MemoryRouter initialEntries={[path]}>
            <QuickAddProvider>
              <Routes>
                <Route path="/tarefas" element={<TarefasPage />} />
              </Routes>
              <Local />
            </QuickAddProvider>
          </MemoryRouter>
        </SessaoAtivaProvider>
      </XpProgressProvider>
    </ToastProvider>,
  )
}

function setupUser() {
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
}

describe('TarefasPage', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tarefaApi.excluirTarefa).mockReset()
    vi.mocked(tarefaApi.restaurarTarefa).mockReset()
    vi.mocked(tarefaApi.reordenarTarefas).mockReset()
    vi.mocked(tarefaApi.reordenarTarefas).mockResolvedValue(undefined)
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

  describe('lista', () => {
    it('lista tarefas e mostra status em PT só quando difere do default da visão', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaVariada)
      renderLista()

      expect(await screen.findByText('Hoje item')).toBeInTheDocument()
      expect(document.querySelector('.status-em_progresso')).toHaveTextContent('Em progresso')
      expect(document.querySelector('.status-pendente')).not.toBeInTheDocument()
      expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 0,
          size: 20,
          sort: 'prazo',
          direction: 'asc',
          status: ['PENDENTE', 'EM_PROGRESSO', 'ATRASADA', 'CANCELADA'],
        }),
        expect.any(AbortSignal),
      )
    })

    it('renderiza grupos com contador', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaVariada)
      renderLista()

      expect(await screen.findByRole('heading', { name: /atrasadas \(1\)/i })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: /^hoje \(1\)$/i })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: /esta semana \(1\)/i })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: /depois \(1\)/i })).toBeInTheDocument()
    })

    it('card é um link para o drawer preservando a query string', async () => {
      renderLista('/tarefas?visao=pendentes')

      const link = await screen.findByRole('link', { name: /hoje item/i })
      expect(link).toHaveAttribute('href', '/tarefas/t-hoje?visao=pendentes')
      expect(document.querySelector('table')).not.toBeInTheDocument()
    })

    it('menu de ações do card oferece Editar e Excluir', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /ações de hoje item/i }))

      const menu = screen.getByRole('group', { name: /ações de hoje item/i })
      expect(within(menu).getByRole('link', { name: /editar/i })).toHaveAttribute(
        'href',
        '/tarefas/t-hoje',
      )
      expect(within(menu).getByRole('button', { name: /^excluir$/i })).toBeInTheDocument()
    })

    it('paginação mostra página, total e avança', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
        ...pagina,
        totalElements: 25,
        totalPages: 2,
      })
      const user = setupUser()
      renderLista()

      expect(await screen.findByText(/página 1 de 2 · 25 tarefas/i)).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /próxima/i }))

      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ page: 1 }),
          expect.any(AbortSignal),
        )
      })
      expect(screen.getByRole('region', { name: /lista de tarefas/i })).toHaveFocus()
    })

    it('empty state da primeira vez: CTA Nova tarefa foca o inline', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 20,
      })
      const user = setupUser()
      renderLista()

      expect(await screen.findByText(/nenhuma tarefa ainda/i)).toBeInTheDocument()
      const empty = screen.getByText(/nenhuma tarefa ainda/i).closest('.tarefas-empty')
      expect(empty).not.toBeNull()
      await user.click(within(empty as HTMLElement).getByRole('button', { name: /nova tarefa/i }))
      expect(screen.getByRole('textbox', { name: /adicionar tarefa/i })).toHaveFocus()
      expect(screen.queryByRole('dialog', { name: /nova tarefa/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: /organizar meu dia/i })).not.toBeInTheDocument()
    })

    it('T1: com lista não vazia, existe link Organizar meu dia para /plano', async () => {
      renderLista()

      const link = await screen.findByRole('link', { name: /organizar meu dia/i })
      expect(link).toHaveAttribute('href', '/plano')
    })

    it('T1: Enter no inline com título chama criarTarefa com prioridade e duração padrão e limpa o campo', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
        id: 't-nova',
        titulo: 'Comprar leite',
        descricao: '',
        prioridade: 'MEDIA',
        prazo: null,
        status: 'PENDENTE',
        duracaoMinutos: 30,
        createdAt: '2026-10-28T12:00:00Z',
        updatedAt: '2026-10-28T12:00:00Z',
        deletedAt: null,
        deletada: false,
      })
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      const input = screen.getByRole('textbox', { name: /adicionar tarefa/i })
      await user.type(input, 'Comprar leite{Enter}')

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Comprar leite',
          prioridade: 'MEDIA',
          duracaoMinutos: 30,
        })
      })
      expect(input).toHaveValue('')
      expect(await screen.findByText('Tarefa criada')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
    })

    it('T2: com projetoId na URL o body inclui projetoId', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
        id: 't-proj',
        titulo: 'Item do TCC',
        descricao: '',
        prioridade: 'MEDIA',
        prazo: null,
        status: 'PENDENTE',
        duracaoMinutos: 30,
        projetoId: 'proj-tcc',
        createdAt: '2026-10-28T12:00:00Z',
        updatedAt: '2026-10-28T12:00:00Z',
        deletedAt: null,
        deletada: false,
      })
      const user = setupUser()
      renderLista('/tarefas?projetoId=proj-tcc')
      await screen.findByText('Hoje item')

      await user.type(screen.getByRole('textbox', { name: /adicionar tarefa/i }), 'Item do TCC{Enter}')

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Item do TCC',
          prioridade: 'MEDIA',
          duracaoMinutos: 30,
          projetoId: 'proj-tcc',
        })
      })
    })

    it('inline permite escolher prioridade e duração antes de criar', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
        id: 't-alta',
        titulo: 'Relatório',
        descricao: '',
        prioridade: 'ALTA',
        prazo: null,
        status: 'PENDENTE',
        duracaoMinutos: 90,
        createdAt: '2026-10-28T12:00:00Z',
        updatedAt: '2026-10-28T12:00:00Z',
        deletedAt: null,
        deletada: false,
      })
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      const prioridade = screen.getByRole('combobox', { name: /^prioridade$/i })
      const duracao = screen.getByRole('combobox', { name: /^duração$/i })
      await user.selectOptions(prioridade, 'ALTA')
      await user.selectOptions(duracao, '90')
      await user.type(screen.getByRole('textbox', { name: /adicionar tarefa/i }), 'Relatório{Enter}')

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
          titulo: 'Relatório',
          prioridade: 'ALTA',
          duracaoMinutos: 90,
        })
      })
      await waitFor(() => expect(prioridade).toHaveValue('MEDIA'))
      expect(duracao).toHaveValue('30')
    })

    it('T3: título vazio não chama API e mostra feedback acessível', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^adicionar$/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    })

    it('empty state com filtros oferece Limpar filtros', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 20,
      })
      const user = setupUser()
      renderLista('/tarefas?prioridade=ALTA')

      const empty = (await screen.findByText(/nada com estes filtros/i)).closest('.tarefas-empty')
      await user.click(within(empty as HTMLElement).getByRole('button', { name: /limpar filtros/i }))

      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas$/)
    })

    it('troca de filtro mantém a lista visível e esmaecida até a nova página chegar', async () => {
      const proxima = deferred<PageTarefa>()
      vi.mocked(tarefaApi.listarTarefas)
        .mockResolvedValueOnce(pagina)
        .mockReturnValueOnce(proxima.promise)
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: 'Pendentes' }))

      expect(screen.getByText('Hoje item')).toBeInTheDocument()
      expect(screen.getByRole('region', { name: /lista de tarefas/i })).toHaveAttribute(
        'aria-busy',
        'true',
      )
      expect(document.querySelector('.skeleton')).not.toBeInTheDocument()

      await act(async () => proxima.resolve(pagina))
      expect(screen.getByRole('region', { name: /lista de tarefas/i })).toHaveAttribute(
        'aria-busy',
        'false',
      )
    })
  })

  describe('filtros', () => {
    it('search params reaplicam visão e filtro na carga', async () => {
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
          expect.any(AbortSignal),
        )
      })
      expect(screen.getByRole('button', { name: 'Pendentes' })).toHaveAttribute(
        'aria-pressed',
        'true',
      )
    })

    it('filtro projetoId na URL da lista de tarefas', async () => {
      renderLista('/tarefas?projetoId=proj-tcc')

      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
          expect.objectContaining({ projetoId: 'proj-tcc' }),
          expect.any(AbortSignal),
        )
      })
      expect(
        await screen.findByRole('button', { name: /remover filtro projeto: tcc/i }),
      ).toBeInTheDocument()
    })

    it('filtros de ausência na URL chamam a API e mostram chips', async () => {
      renderLista('/tarefas?semPrazo=true&semProjeto=true&semTag=true')

      await waitFor(() => {
        const filtro = vi.mocked(tarefaApi.listarTarefas).mock.calls.at(-1)?.[0]
        expect(filtro?.semPrazo).toBe(true)
        expect(filtro?.semProjeto).toBe(true)
        expect(filtro?.semTag).toBe(true)
        expect(filtro?.somenteRaizes).toBeUndefined()
      })
      expect(await screen.findByRole('button', { name: /remover filtro sem prazo/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /remover filtro sem projeto/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /remover filtro sem tag/i })).toBeInTheDocument()
    })

    it('escolher Sem projeto, Sem prazo e Sem tag atualiza a URL', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^projeto$/i }))
      await user.selectOptions(screen.getByLabelText(/^projeto$/i), 'Sem projeto')

      await user.click(screen.getByRole('button', { name: /^prazo$/i }))
      await user.click(screen.getByLabelText(/^sem prazo$/i))

      await user.click(screen.getByRole('button', { name: /^tags$/i }))
      await user.click(await screen.findByLabelText(/^sem tag$/i))

      expect(screen.getByTestId('local')).toHaveTextContent('semProjeto=true')
      expect(screen.getByTestId('local')).toHaveTextContent('semPrazo=true')
      expect(screen.getByTestId('local')).toHaveTextContent('semTag=true')
      await waitFor(() => {
        const filtro = vi.mocked(tarefaApi.listarTarefas).mock.calls.at(-1)?.[0]
        expect(filtro?.semProjeto).toBe(true)
        expect(filtro?.semPrazo).toBe(true)
        expect(filtro?.semTag).toBe(true)
      })
    })

    it('chip do projeto no card leva à lista filtrada do projeto', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(
        paginaDe([
          tarefaLista({
            id: 't-hoje',
            titulo: 'Hoje item',
            projetoId: 'proj-tcc',
            projetoNome: 'TCC',
          }),
        ]),
      )
      renderLista()

      expect(await screen.findByRole('link', { name: 'TCC' })).toHaveAttribute(
        'href',
        '/tarefas?projetoId=proj-tcc',
      )
    })

    it('visão aplica filtro imediatamente', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: 'Em progresso' }))

      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ status: ['EM_PROGRESSO'], page: 0 }),
          expect.any(AbortSignal),
        )
      })
    })

    it('T8: escolher prioridade no popover atualiza URL e lista sem botão Aplicar', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      expect(screen.queryByRole('button', { name: /aplicar filtros/i })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /^prioridade$/i }))
      await user.click(screen.getByLabelText(/^alta$/i))

      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas?prioridade=ALTA')
      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ prioridade: ['ALTA'], page: 0 }),
          expect.any(AbortSignal),
        )
      })
    })

    it('ordenação no popover aplica ao escolher', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^ordenar$/i }))
      await user.selectOptions(screen.getByLabelText(/ordenar por/i), 'duracao')
      await user.selectOptions(screen.getByLabelText(/^direção$/i), 'desc')

      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ sort: 'duracao', direction: 'desc', page: 0 }),
          expect.any(AbortSignal),
        )
      })
    })

    it('campo numérico aplica com debounce de 400ms', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^duração$/i }))
      await user.type(screen.getByLabelText(/duração de \(min\)/i), '30')

      const chamadasComDuracao = () =>
        vi
          .mocked(tarefaApi.listarTarefas)
          .mock.calls.filter(([filtro]) => filtro?.duracaoDe === 30).length
      expect(chamadasComDuracao()).toBe(0)

      act(() => {
        vi.advanceTimersByTime(DEBOUNCE_FILTRO_MS)
      })

      await waitFor(() => expect(chamadasComDuracao()).toBe(1))
      expect(
        vi.mocked(tarefaApi.listarTarefas).mock.calls.some(([filtro]) => filtro?.duracaoDe === 3),
      ).toBe(false)
    })

    it('T9: duas mudanças rápidas de filtro aplicam só a resposta da última', async () => {
      const primeira = deferred<PageTarefa>()
      const segunda = deferred<PageTarefa>()
      vi.mocked(tarefaApi.listarTarefas)
        .mockResolvedValueOnce(pagina)
        .mockReturnValueOnce(primeira.promise)
        .mockReturnValueOnce(segunda.promise)
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: 'Pendentes' }))
      await user.click(screen.getByRole('button', { name: 'Em progresso' }))

      const sinalPrimeira = vi.mocked(tarefaApi.listarTarefas).mock.calls[1][1]
      expect(sinalPrimeira?.aborted).toBe(true)

      await act(async () => segunda.resolve({ ...pagina, content: [paginaVariada.content[3]] }))
      await act(async () => primeira.resolve({ ...pagina, content: [paginaVariada.content[2]] }))

      expect(screen.getByText('Depois item')).toBeInTheDocument()
      expect(screen.queryByText('Semana item')).not.toBeInTheDocument()
    })

    it('T10: chip de filtro ativo é removível e Limpar filtros volta ao default', async () => {
      const user = setupUser()
      renderLista('/tarefas?visao=pendentes&prioridade=ALTA&projetoId=proj-tcc&sort=duracao')
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /remover filtro prioridade: alta/i }))

      expect(screen.getByTestId('local')).toHaveTextContent(
        '/tarefas?visao=pendentes&projetoId=proj-tcc&sort=duracao',
      )
      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ prioridade: undefined, projetoId: 'proj-tcc' }),
          expect.any(AbortSignal),
        )
      })

      await user.click(screen.getByRole('button', { name: /^limpar filtros$/i }))

      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas\?visao=pendentes$/)
      expect(screen.queryByRole('group', { name: /filtros ativos/i })).not.toBeInTheDocument()
      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ projetoId: undefined, sort: 'prazo', status: ['PENDENTE'] }),
          expect.any(AbortSignal),
        )
      })
    })
  })

  describe('excluir e restaurar', () => {
    it('excluir pai com filhos: voltar no diálogo de cascata não chama API', async () => {
      const pai = { ...pagina.content[0], temFilhos: true, titulo: 'Pai com filhos' }
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({ ...pagina, content: [pai] })
      const user = setupUser()
      renderLista()
      await screen.findByText('Pai com filhos')

      await user.click(screen.getByRole('button', { name: /^excluir pai com filhos$/i }))
      const dialog = screen.getByRole('dialog', { name: /excluir tarefa e subtarefas/i })
      await user.click(within(dialog).getByRole('button', { name: /^voltar$/i }))

      expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
      expect(screen.getByText('Pai com filhos')).toBeInTheDocument()
    })

    it('excluir: cancelar confirmação não chama API', async () => {
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^excluir hoje item$/i }))
      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByRole('heading', { name: /excluir tarefa/i })).toBeInTheDocument()
      await user.click(within(dialog).getByRole('button', { name: /^cancelar$/i }))

      expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
      expect(screen.getByText('Hoje item')).toBeInTheDocument()
    })

    it('T7: excluir tira o item na hora; erro da API restaura o item e mostra toast', async () => {
      const exclusao = deferred<void>()
      vi.mocked(tarefaApi.excluirTarefa).mockReturnValue(exclusao.promise)
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaVariada)
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^excluir hoje item$/i }))
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^excluir$/i }))

      expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-hoje')
      expect(screen.queryByText('Hoje item')).not.toBeInTheDocument()

      await act(async () => exclusao.reject(new ApiError(500, 'Falha ao excluir.')))

      expect(await screen.findByText('Falha ao excluir.')).toBeInTheDocument()
      const hoje = screen.getByRole('heading', { name: /^hoje \(1\)$/i }).closest('section')
      expect(within(hoje as HTMLElement).getByText('Hoje item')).toBeInTheDocument()
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(1)
    })

    it('excluir com sucesso mostra Desfazer e restaura de forma otimista', async () => {
      vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)
      vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue(pagina.content[0])
      const user = setupUser()
      renderLista()
      await screen.findByText('Hoje item')

      await user.click(screen.getByRole('button', { name: /^excluir hoje item$/i }))
      expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^excluir$/i }))

      await waitFor(() => {
        expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-hoje')
      })
      expect(screen.queryByText('Hoje item')).not.toBeInTheDocument()

      await user.click(await screen.findByRole('button', { name: /desfazer/i }))

      expect(screen.getByText('Hoje item')).toBeInTheDocument()
      await waitFor(() => {
        expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-hoje')
      })
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(1)
    })

    it('visão Excluídas: restaurar é otimista e reverte em erro', async () => {
      const deletada = {
        ...pagina.content[0],
        deletada: true,
        deletedAt: '2026-02-01T10:00:00Z',
      }
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({ ...pagina, content: [deletada] })
      const restauracao = deferred<typeof deletada>()
      vi.mocked(tarefaApi.restaurarTarefa).mockReturnValue(restauracao.promise)
      const user = setupUser()
      renderLista('/tarefas?visao=excluidas')

      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
          expect.objectContaining({ somenteDeletadas: true }),
          expect.any(AbortSignal),
        )
      })
      expect(await screen.findByText('Excluída')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /^restaurar$/i }))

      expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-hoje')
      expect(screen.queryByText('Excluída')).not.toBeInTheDocument()

      await act(async () => restauracao.reject(new Error('rede')))

      expect(await screen.findByText('Não foi possível restaurar a tarefa.')).toBeInTheDocument()
      expect(screen.getByText('Excluída')).toBeInTheDocument()
    })
  })

  describe('árvore e subtarefas', () => {
    const pai = tarefaLista({
      id: 't-pai',
      titulo: 'Estudar Security',
      temFilhos: true,
      duracaoMinutos: 120,
      restanteMinutos: 120,
    })
    const filho1 = tarefaLista({
      id: 't-filho-1',
      titulo: 'Capítulo 1',
      parentId: 't-pai',
      parentTitulo: 'Estudar Security',
      createdAt: '2026-01-01T10:00:00Z',
    })
    const filho2 = tarefaLista({
      id: 't-filho-2',
      titulo: 'Capítulo 2',
      parentId: 't-pai',
      parentTitulo: 'Estudar Security',
      createdAt: '2026-01-01T11:00:00Z',
    })

    function mockArvore() {
      vi.mocked(tarefaApi.listarTarefas).mockImplementation(async (filtro = {}) => {
        if (filtro.parentId === 't-pai') {
          return paginaDe([filho1, filho2], 100)
        }
        return paginaDe([pai])
      })
    }

    it('visão todas sem filtro isolador lista só raízes', async () => {
      mockArvore()
      renderLista()

      expect(await screen.findByText('Estudar Security')).toBeInTheDocument()
      await waitFor(() => {
        expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
          expect.objectContaining({ somenteRaizes: true }),
          expect.any(AbortSignal),
        )
      })
      expect(screen.queryByText('Capítulo 1')).not.toBeInTheDocument()
      expect(screen.queryByText('Capítulo 2')).not.toBeInTheDocument()
    })

    it('pai recolhido esconde filhos; expandir busca parentId e mostra os filhos', async () => {
      mockArvore()
      const user = setupUser()
      renderLista()
      await screen.findByText('Estudar Security')

      expect(screen.queryByText('Capítulo 1')).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /expandir subtarefas de estudar security/i }))

      expect(await screen.findByText('Capítulo 1')).toBeInTheDocument()
      expect(screen.getByText('Capítulo 2')).toBeInTheDocument()
      expect(screen.queryByText(/subtarefa de estudar security/i)).not.toBeInTheDocument()
      expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
        expect.objectContaining({ parentId: 't-pai', size: 100 }),
      )

      const lista = screen.getByRole('region', { name: /lista de tarefas/i })
      expect(within(lista).queryByRole('button', { name: /adicionar subtarefa/i })).not.toBeInTheDocument()
      const nested = screen.getByRole('list', { name: /subtarefas de estudar security/i })
      expect(within(nested).getByText('Capítulo 1')).toBeInTheDocument()
    })

    it('card do pai não oferece Iniciar nem Concluir de sessão', async () => {
      mockArvore()
      renderLista()
      await screen.findByText('Estudar Security')

      expect(screen.queryByRole('button', { name: /iniciar estudar security/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /concluir estudar security/i })).not.toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: /expandir subtarefas de estudar security/i }),
      ).toBeInTheDocument()
    })

    it('filtro que solta o filho mostra card próprio com indicador do pai', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([filho1]))
      renderLista('/tarefas?prioridade=ALTA')

      expect(await screen.findByText('Capítulo 1')).toBeInTheDocument()
      const indicador = screen.getByRole('link', { name: /subtarefa de estudar security/i })
      expect(indicador).toHaveAttribute('href', '/tarefas/t-pai?prioridade=ALTA')
      expect(screen.queryByText('Estudar Security', { selector: '.tarefa-card__titulo' })).not.toBeInTheDocument()
      await waitFor(() => {
        const filtro = vi.mocked(tarefaApi.listarTarefas).mock.calls.at(-1)?.[0]
        expect(filtro?.somenteRaizes).toBeUndefined()
        expect(filtro?.prioridade).toEqual(['ALTA'])
      })
    })

    it('quando pai e filho estão no resultado, o filho fica aninhado e não duplica no mesmo nível', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([pai, filho1]))
      const user = setupUser()
      renderLista('/tarefas?prioridade=ALTA')
      await screen.findByText('Estudar Security')

      expect(screen.queryByText('Capítulo 1')).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: /subtarefa de estudar security/i })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /expandir subtarefas de estudar security/i }))

      const nested = await screen.findByRole('list', { name: /subtarefas de estudar security/i })
      expect(within(nested).getByText('Capítulo 1')).toBeInTheDocument()
      expect(screen.queryByRole('link', { name: /subtarefa de estudar security/i })).not.toBeInTheDocument()
      expect(
        vi.mocked(tarefaApi.listarTarefas).mock.calls.some(([filtro]) => filtro?.parentId === 't-pai'),
      ).toBe(false)
    })

    it('folha aninhada mantém Iniciar e Concluir de sessão', async () => {
      mockArvore()
      const user = setupUser()
      renderLista()
      await screen.findByText('Estudar Security')
      await user.click(screen.getByRole('button', { name: /expandir subtarefas de estudar security/i }))

      expect(await screen.findByRole('button', { name: /iniciar capítulo 1/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir capítulo 1/i })).toBeInTheDocument()
    })

    it('folha solta no filtro mantém Iniciar e Concluir de sessão', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([filho1]))
      renderLista('/tarefas?prioridade=ALTA')

      expect(await screen.findByRole('button', { name: /iniciar capítulo 1/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir capítulo 1/i })).toBeInTheDocument()
    })
  })

  describe('espera na lista', () => {
    const alvo = {
      id: 'e-1',
      alvoTipo: 'TAREFA' as const,
      alvoId: 't-2',
      alvoTitulo: 'Revisar API',
      resolvida: false,
    }

    it('card bloqueado mostra o alvo e esconde Iniciar e Concluir', async () => {
      const bloqueada = tarefaLista({
        id: 't-bloqueada',
        titulo: 'Escrever doc',
        esperas: [alvo],
        bloqueada: true,
      })
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([bloqueada]))

      renderLista('/tarefas')

      const card = (await screen.findByRole('link', { name: /escrever doc/i })).closest('article')!
      expect(within(card).getByText('Espera Revisar API')).toBeInTheDocument()
      expect(
        within(card).queryByRole('button', { name: /iniciar escrever doc/i }),
      ).not.toBeInTheDocument()
      expect(
        within(card).queryByRole('button', { name: /concluir escrever doc/i }),
      ).not.toBeInTheDocument()
      expect(within(card).getByRole('link', { name: /escrever doc/i })).toBeInTheDocument()
    })

    it('várias esperas viram um resumo no card', async () => {
      const bloqueada = tarefaLista({
        id: 't-bloqueada',
        titulo: 'Escrever doc',
        esperas: [alvo, { ...alvo, id: 'e-2', alvoId: 'p-1', alvoTipo: 'PROJETO', alvoTitulo: 'TCC' }],
        bloqueada: true,
      })
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([bloqueada]))

      renderLista('/tarefas')

      const card = (await screen.findByRole('link', { name: /escrever doc/i })).closest('article')!
      expect(within(card).getByText('Espera 2 itens')).toBeInTheDocument()
      expect(within(card).queryByText('Revisar API')).not.toBeInTheDocument()
    })

    it('bloqueio herdado sem espera própria ainda esconde a sessão', async () => {
      const bloqueada = tarefaLista({
        id: 't-filha',
        titulo: 'Escrever doc',
        bloqueada: true,
      })
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([bloqueada]))

      renderLista('/tarefas')

      const card = (await screen.findByRole('link', { name: /escrever doc/i })).closest('article')!
      expect(within(card).getByText('Bloqueada por espera')).toBeInTheDocument()
      expect(
        within(card).queryByRole('button', { name: /iniciar escrever doc/i }),
      ).not.toBeInTheDocument()
    })

    it('tarefa sem espera mantém Iniciar e Concluir', async () => {
      const livre = tarefaLista({ id: 't-livre', titulo: 'Escrever doc' })
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([livre]))

      renderLista('/tarefas')

      expect(
        await screen.findByRole('button', { name: /iniciar escrever doc/i }),
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir escrever doc/i })).toBeInTheDocument()
    })
  })

  it('reordena tarefas do mesmo grupo com seta no handle', async () => {
    const primeira = tarefaLista({
      id: 't-hoje-1',
      titulo: 'Primeira de hoje',
      ordem: 0,
      prazo: '2026-10-28T18:00:00Z',
    })
    const segunda = tarefaLista({
      id: 't-hoje-2',
      titulo: 'Segunda de hoje',
      ordem: 1,
      prazo: '2026-10-28T19:00:00Z',
    })
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(paginaDe([primeira, segunda]))
    const user = setupUser()
    renderLista()

    await screen.findByText('Primeira de hoje')
    const handle = screen.getByRole('button', { name: 'Reordenar Primeira de hoje' })
    handle.focus()
    await user.keyboard('{ArrowDown}')

    await waitFor(() => {
      expect(tarefaApi.reordenarTarefas).toHaveBeenCalledWith(['t-hoje-2', 't-hoje-1'])
    })
  })
})
