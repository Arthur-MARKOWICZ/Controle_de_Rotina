import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import { ToastProvider } from '../../feedback/ToastProvider'
import { QuickAddProvider } from '../../layout/QuickAddContext'
import * as projetoApi from '../../projeto/api/projetoApi'
import * as tarefaApi from '../api/tarefaApi'
import type { PageTarefa } from '../api/tarefaApi'
import { DEBOUNCE_FILTRO_MS } from '../FiltrosBarra'
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
      <MemoryRouter initialEntries={[path]}>
        <QuickAddProvider>
          <Routes>
            <Route path="/tarefas" element={<TarefasPage />} />
          </Routes>
          <Local />
        </QuickAddProvider>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function setupUser() {
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
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

    it('empty state da primeira vez oferece CTA Nova tarefa', async () => {
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
      expect(await screen.findByRole('heading', { name: /nova tarefa/i })).toBeInTheDocument()
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
})
