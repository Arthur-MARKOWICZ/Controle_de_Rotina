import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes, useLocation, type InitialEntry } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import { adicionarDiasIso } from '../../capacidade/semana'
import { ToastProvider } from '../../feedback/ToastProvider'
import { dataHojeIso } from '../../planner/data'
import * as objetivoApi from '../api/objetivoApi'
import type { ObjetivoItens, ObjetivoResponse } from '../api/objetivoApi'
import { ObjetivoDrawer } from '../ObjetivoDrawer'
import { ObjetivoDetalhePage } from './ObjetivoDetalhePage'

vi.mock('../api/objetivoApi', () => ({
  buscarObjetivo: vi.fn(),
  listarItensObjetivo: vi.fn(),
  concluirObjetivo: vi.fn(),
  arquivarObjetivo: vi.fn(),
  reabrirObjetivo: vi.fn(),
  atualizarObjetivo: vi.fn(),
  criarObjetivo: vi.fn(),
}))

function objetivo(parcial: Partial<ObjetivoResponse> = {}): ObjetivoResponse {
  return {
    id: 'obj-1',
    titulo: 'Passar na certificação',
    descricao: 'Estudar todo dia',
    prazo: null,
    status: 'ATIVO',
    concluidoEm: null,
    arquivadoEm: null,
    createdAt: '2026-11-01T12:00:00Z',
    updatedAt: '2026-11-01T12:00:00Z',
    progresso: null,
    ...parcial,
  }
}

const ITENS: ObjetivoItens = {
  tarefas: [
    {
      id: 't-1',
      titulo: 'Estudar S3',
      status: 'PENDENTE',
      origem: 'DIRETO',
      projetoId: null,
      projetoNome: null,
    },
    {
      id: 't-2',
      titulo: 'Simulado 1',
      status: 'PENDENTE',
      origem: 'VIA_PROJETO',
      projetoId: 'p-1',
      projetoNome: 'Estudos AWS',
    },
  ],
  habitos: [{ id: 'h-1', titulo: 'Flashcards', status: 'ATIVO', origem: 'DIRETO' }],
  projetos: [{ id: 'p-1', nome: 'Estudos AWS', status: 'ATIVO', origem: 'DIRETO' }],
}

const SEM_ITENS: ObjetivoItens = { tarefas: [], habitos: [], projetos: [] }

function LocationProbe() {
  const location = useLocation()
  return (
    <div data-testid="location">{`${location.pathname}${location.search}`}</div>
  )
}

function renderDetalhe(entry: InitialEntry = '/objetivos/obj-1') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[entry]}>
        <LocationProbe />
        <Routes>
          <Route path="/objetivos" element={<p>lista de objetivos</p>} />
          <Route
            path="/objetivos/:id"
            element={
              <>
                <Link to="/objetivos/obj-2">Ir para o outro</Link>
                <ObjetivoDetalhePage />
              </>
            }
          >
            <Route path="editar" element={<ObjetivoDrawer />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
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

function h1(nome: string | RegExp) {
  return screen.findByRole('heading', { level: 1, name: nome })
}

async function confirmar(rotulo: string) {
  const dialog = await screen.findByRole('dialog')
  await userEvent.click(within(dialog).getByRole('button', { name: rotulo }))
}

describe('ObjetivoDetalhePage', () => {
  beforeEach(() => {
    vi.mocked(objetivoApi.buscarObjetivo).mockReset()
    vi.mocked(objetivoApi.listarItensObjetivo).mockReset()
    vi.mocked(objetivoApi.concluirObjetivo).mockReset()
    vi.mocked(objetivoApi.arquivarObjetivo).mockReset()
    vi.mocked(objetivoApi.reabrirObjetivo).mockReset()
    vi.mocked(objetivoApi.atualizarObjetivo).mockReset()
    vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo())
    vi.mocked(objetivoApi.listarItensObjetivo).mockResolvedValue(ITENS)
  })

  describe('cabeçalho e itens', () => {
    it('mostra título, status, prazo, descrição, ações e os itens vinculados', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(
        objetivo({ prazo: adicionarDiasIso(dataHojeIso(), 3) }),
      )
      renderDetalhe()

      await h1('Passar na certificação')
      expect(screen.getByText('Ativo', { selector: '.badge' })).toBeInTheDocument()
      expect(screen.getByText(/faltam 3 dias/)).toBeInTheDocument()
      expect(screen.getByText('Estudar todo dia')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /editar/i })).toHaveAttribute(
        'href',
        '/objetivos/obj-1/editar',
      )
      expect(await screen.findByRole('heading', { name: 'Tarefas (2)' })).toBeInTheDocument()
      expect(screen.getByText('via projeto Estudos AWS')).toBeInTheDocument()
      expect(objetivoApi.buscarObjetivo).toHaveBeenCalledWith('obj-1')
      expect(objetivoApi.listarItensObjetivo).toHaveBeenCalledWith('obj-1')
    })

    it('objetivo ativo vencido mostra "Prazo vencido" em texto', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(
        objetivo({ prazo: adicionarDiasIso(dataHojeIso(), -1) }),
      )
      renderDetalhe()

      await h1('Passar na certificação')
      expect(screen.getByText(/Prazo vencido/)).toBeInTheDocument()
    })

    it('concluído mostra a data de conclusão e não destaca prazo vencido', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(
        objetivo({
          status: 'CONCLUIDO',
          prazo: adicionarDiasIso(dataHojeIso(), -5),
          concluidoEm: '2026-11-12T15:00:00Z',
        }),
      )
      renderDetalhe()

      await h1('Passar na certificação')
      expect(screen.getByText('Concluído')).toBeInTheDocument()
      expect(screen.getByText(/Concluído em 12\/11\/2026|Concluído em 12 nov/)).toBeInTheDocument()
      expect(screen.queryByText(/Prazo vencido/)).not.toBeInTheDocument()
    })

    it('"Voltar a Objetivos" leva à visão e página da lista de origem', async () => {
      renderDetalhe({
        pathname: '/objetivos/obj-1',
        state: { listaSearch: '?status=CONCLUIDO&page=2' },
      })

      await h1('Passar na certificação')
      expect(screen.getByRole('link', { name: /Voltar a Objetivos/ })).toHaveAttribute(
        'href',
        '/objetivos?status=CONCLUIDO&page=2',
      )
    })

    it('sem state de origem, "Voltar a Objetivos" leva a /objetivos', async () => {
      renderDetalhe()

      await h1('Passar na certificação')
      expect(screen.getByRole('link', { name: /Voltar a Objetivos/ })).toHaveAttribute(
        'href',
        '/objetivos',
      )
    })

    it('objetivo sem itens mostra o estado vazio, sem grupos', async () => {
      vi.mocked(objetivoApi.listarItensObjetivo).mockResolvedValue(SEM_ITENS)
      renderDetalhe()

      expect(await screen.findByText('Nenhum item vinculado')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: /^Tarefas/ })).not.toBeInTheDocument()
    })

    it('falha nos itens mantém o cabeçalho e as ações do objetivo', async () => {
      vi.mocked(objetivoApi.listarItensObjetivo).mockRejectedValue(new TypeError('Failed to fetch'))
      renderDetalhe()

      await h1('Passar na certificação')
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Não foi possível carregar os itens vinculados.',
      )
      expect(screen.getByRole('button', { name: 'Concluir' })).toBeEnabled()
      expect(screen.getByRole('link', { name: /editar/i })).toBeInTheDocument()
    })

    it('HTML do proxy nos itens mostra o alerta da seção e nenhum grupo', async () => {
      vi.mocked(objetivoApi.listarItensObjetivo).mockRejectedValue(
        new ApiError(200, 'Resposta inválida da API (não é JSON).'),
      )
      renderDetalhe()

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Não foi possível carregar os itens vinculados.',
      )
      expect(screen.queryByRole('heading', { name: /^Tarefas/ })).not.toBeInTheDocument()
    })

    it('trocar de objetivo descarta a resposta atrasada do anterior', async () => {
      const primeiro = deferred<ObjetivoResponse>()
      vi.mocked(objetivoApi.buscarObjetivo).mockImplementation((id) =>
        id === 'obj-1' ? primeiro.promise : Promise.resolve(objetivo({ id: 'obj-2', titulo: 'Correr 10 km' })),
      )
      const user = userEvent.setup()
      renderDetalhe()

      await user.click(screen.getByRole('link', { name: 'Ir para o outro' }))
      await h1('Correr 10 km')
      primeiro.resolve(objetivo({ id: 'obj-1', titulo: 'Passar na certificação' }))

      await waitFor(() => {
        expect(screen.queryByRole('heading', { level: 1, name: 'Passar na certificação' })).toBeNull()
      })
      expect(screen.getByRole('heading', { level: 1, name: 'Correr 10 km' })).toBeInTheDocument()
    })
  })

  describe('erros ao carregar o objetivo', () => {
    it('404 mostra a mensagem da API, sem ações nem itens', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockRejectedValue(
        new ApiError(404, 'Objetivo inexistente ou sem permissão de acesso.'),
      )
      vi.mocked(objetivoApi.listarItensObjetivo).mockRejectedValue(new ApiError(404, 'x'))
      renderDetalhe('/objetivos/alheio')

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Objetivo inexistente ou sem permissão de acesso.',
      )
      expect(screen.queryByRole('button', { name: 'Concluir' })).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: /editar/i })).not.toBeInTheDocument()
      expect(screen.queryByText('Itens vinculados')).not.toBeInTheDocument()
    })

    it('erro de servidor mostra a mensagem padrão e nenhuma ação', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockRejectedValue(
        new ApiError(500, 'Não foi possível carregar o objetivo.'),
      )
      renderDetalhe()

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Não foi possível carregar o objetivo.',
      )
      expect(screen.queryByRole('button', { name: 'Arquivar' })).not.toBeInTheDocument()
    })

    it('falha de rede cai na mensagem padrão', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockRejectedValue(new TypeError('Failed to fetch'))
      renderDetalhe()

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Não foi possível carregar o objetivo.',
      )
    })
  })

  describe('ações de ciclo de vida', () => {
    it('concluir confirmado atualiza o status, as ações e mostra o toast, sem sair da página', async () => {
      vi.mocked(objetivoApi.concluirObjetivo).mockResolvedValue(
        objetivo({ status: 'CONCLUIDO', concluidoEm: '2026-11-17T15:00:00Z' }),
      )
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')

      await user.click(screen.getByRole('button', { name: 'Concluir' }))
      const dialog = await screen.findByRole('dialog')
      expect(dialog).toHaveTextContent('Concluir objetivo?')
      await user.click(within(dialog).getByRole('button', { name: 'Concluir' }))

      expect(await screen.findByText('Objetivo concluído')).toBeInTheDocument()
      expect(objetivoApi.concluirObjetivo).toHaveBeenCalledOnce()
      expect(objetivoApi.concluirObjetivo).toHaveBeenCalledWith('obj-1')
      expect(screen.getByText('Concluído')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Concluir' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Arquivar' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Reabrir' })).toBeInTheDocument()
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/objetivos\/obj-1$/)
    })

    it('arquivar cancelado não envia requisição e mantém o status', async () => {
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')

      await user.click(screen.getByRole('button', { name: 'Arquivar' }))
      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: /cancelar/i }))

      expect(objetivoApi.arquivarObjetivo).not.toHaveBeenCalled()
      expect(screen.getByText('Ativo', { selector: '.badge' })).toBeInTheDocument()
    })

    it('reabrir um arquivado não pede confirmação e mostra o toast', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo({ status: 'ARQUIVADO' }))
      vi.mocked(objetivoApi.reabrirObjetivo).mockResolvedValue(objetivo())
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')
      expect(screen.queryByRole('button', { name: 'Concluir' })).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Reabrir' }))

      expect(await screen.findByText('Objetivo reaberto')).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Concluir' })).toBeInTheDocument()
    })

    it('clique repetido durante a ação envia uma única requisição', async () => {
      const pendente = deferred<ObjetivoResponse>()
      vi.mocked(objetivoApi.concluirObjetivo).mockReturnValue(pendente.promise)
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')

      await user.click(screen.getByRole('button', { name: 'Concluir' }))
      await confirmar('Concluir')
      const emAndamento = await screen.findByRole('button', { name: 'Concluindo…' })
      expect(emAndamento).toBeDisabled()
      await user.click(emAndamento)
      await user.click(screen.getByRole('button', { name: 'Arquivar' }))

      expect(objetivoApi.concluirObjetivo).toHaveBeenCalledOnce()
      expect(objetivoApi.arquivarObjetivo).not.toHaveBeenCalled()
      pendente.resolve(objetivo({ status: 'CONCLUIDO' }))
      expect(await screen.findByText('Objetivo concluído')).toBeInTheDocument()
    })

    it('409 mostra a mensagem da API, sem toast, e recarrega o objetivo', async () => {
      vi.mocked(objetivoApi.concluirObjetivo).mockRejectedValue(
        new ApiError(409, 'O objetivo informado já está concluído.'),
      )
      vi.mocked(objetivoApi.buscarObjetivo)
        .mockResolvedValueOnce(objetivo())
        .mockResolvedValue(objetivo({ status: 'CONCLUIDO' }))
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')

      await user.click(screen.getByRole('button', { name: 'Concluir' }))
      await confirmar('Concluir')

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'O objetivo informado já está concluído.',
      )
      expect(screen.queryByText('Objetivo concluído')).not.toBeInTheDocument()
      await waitFor(() => expect(screen.getByText('Concluído')).toBeInTheDocument())
      expect(objetivoApi.buscarObjetivo).toHaveBeenCalledTimes(2)
    })

    it('500 ao reabrir mostra a mensagem padrão em alerta', async () => {
      vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo({ status: 'ARQUIVADO' }))
      vi.mocked(objetivoApi.reabrirObjetivo).mockRejectedValue(new TypeError('Failed to fetch'))
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')

      await user.click(screen.getByRole('button', { name: 'Reabrir' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Não foi possível reabrir o objetivo.',
      )
      expect(screen.queryByText('Objetivo reaberto')).not.toBeInTheDocument()
    })
  })

  describe('edição pelo drawer', () => {
    it('salvar no drawer volta ao detalhe com o novo título e relê os itens', async () => {
      vi.mocked(objetivoApi.buscarObjetivo)
        .mockResolvedValueOnce(objetivo())
        .mockResolvedValueOnce(objetivo())
        .mockResolvedValue(objetivo({ titulo: 'Passar na prova' }))
      vi.mocked(objetivoApi.atualizarObjetivo).mockResolvedValue(
        objetivo({ titulo: 'Passar na prova' }),
      )
      const user = userEvent.setup()
      renderDetalhe()
      await h1('Passar na certificação')

      await user.click(screen.getByRole('link', { name: /editar/i }))
      const campo = await screen.findByLabelText('Título')
      await user.clear(campo)
      await user.type(campo, 'Passar na prova')
      await user.click(screen.getByRole('button', { name: 'Salvar' }))

      await h1('Passar na prova')
      await waitFor(() => {
        expect(screen.getByTestId('location')).toHaveTextContent(/^\/objetivos\/obj-1$/)
      })
      expect(objetivoApi.listarItensObjetivo).toHaveBeenCalledTimes(2)
    })
  })
})
