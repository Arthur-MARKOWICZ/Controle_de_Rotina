import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '../auth/session/AuthContext'
import * as capacidadeResolvidaApi from '../capacidade/api/capacidadeResolvidaApi'
import * as execucaoApi from '../execucao/api/execucaoApi'
import type { ItemExecucaoResponse } from '../execucao/api/execucaoApi'
import * as planoApi from '../planner/api/planoApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import type { TarefaResponse } from '../tarefa/api/tarefaApi'
import { HomePage } from './HomePage'

vi.mock('../planner/api/planoApi', () => ({
  buscarPlano: vi.fn(),
  gerarPlano: vi.fn(),
  aprovarPlano: vi.fn(),
  adicionarBloco: vi.fn(),
  removerBloco: vi.fn(),
}))

vi.mock('../capacidade/api/capacidadeResolvidaApi', () => ({
  consultarCapacidadeResolvida: vi.fn(),
}))

vi.mock('../tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
}))

vi.mock('../execucao/api/execucaoApi', () => ({
  listarExecucoes: vi.fn(),
  iniciarExecucao: vi.fn(),
  concluirExecucao: vi.fn(),
  abortarExecucao: vi.fn(),
}))

vi.mock('../planner/data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../planner/data')>()
  return {
    ...actual,
    dataHojeIso: () => '2026-10-20',
  }
})

const tarefaExtra: TarefaResponse = {
  id: 't-2',
  titulo: 'Ler docs',
  descricao: '',
  prioridade: 'MEDIA',
  prazo: '2030-01-01T12:00:00Z',
  status: 'PENDENTE',
  duracaoHoras: 1,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

const planoBase: PlanoResponse = {
  id: 'p-1',
  data: '2026-10-20',
  versao: 1,
  status: 'RASCUNHO',
  temConflito: false,
  mensagem: null,
  geradoEm: '2026-10-20T09:00:00Z',
  createdAt: '2026-10-20T09:00:00Z',
  updatedAt: '2026-10-20T09:00:00Z',
  blocos: [
    {
      id: 'b-1',
      duracaoMinutos: 60,
      ordem: 1,
      tarefa: {
        id: 't-1',
        titulo: 'Estudar Spring',
        prioridade: 'ALTA',
        status: 'PENDENTE',
        duracaoHoras: 1,
      },
    },
  ],
}

const itemExecucaoBase: ItemExecucaoResponse = {
  bloco: { id: 'b-1', duracaoMinutos: 60, ordem: 1 },
  tarefaResumo: {
    id: 't-1',
    titulo: 'Estudar Spring',
    prioridade: 'ALTA',
    status: 'PENDENTE',
    duracaoHoras: 1,
  },
  execucao: {
    id: null,
    blocoPlanoId: null,
    status: 'NAO_INICIADO',
    iniciadoEm: null,
    concluidoEm: null,
    abortadoEm: null,
  },
}

function renderHome(initialPath = '/') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tarefas/:id" element={<p>Detalhe da tarefa</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('HomePage — plano do dia', () => {
  beforeEach(() => {
    vi.mocked(planoApi.buscarPlano).mockReset()
    vi.mocked(planoApi.gerarPlano).mockReset()
    vi.mocked(planoApi.aprovarPlano).mockReset()
    vi.mocked(planoApi.adicionarBloco).mockReset()
    vi.mocked(planoApi.removerBloco).mockReset()
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(execucaoApi.listarExecucoes).mockReset()
    vi.mocked(execucaoApi.iniciarExecucao).mockReset()
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida).mockResolvedValue([
      { data: '2026-10-20', horas: 2, origem: 'SEMANAL' },
    ])
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [tarefaExtra],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 100,
    })
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
      itens: [itemExecucaoBase],
    })
  })

  describe('F1 — leitura', () => {
    it('T13: mostra mensagem de dia sem tempo quando a API indicar', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        mensagem: 'Sem tempo neste dia. Nada planejado.',
        blocos: [],
      })

      renderHome()

      expect(
        await screen.findByText('Sem tempo neste dia. Nada planejado.'),
      ).toBeInTheDocument()
    })

    it('T13: mostra banner de conflito quando temConflito é true', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        temConflito: true,
        mensagem: 'Capacidade insuficiente: ao menos uma tarefa foi alocada parcialmente.',
      })

      renderHome()

      const banner = await screen.findByRole('status', { name: /conflito/i })
      expect(banner).toHaveTextContent(
        'Capacidade insuficiente: ao menos uma tarefa foi alocada parcialmente.',
      )
    })

    it('lista blocos do plano do dia corrente', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      renderHome()

      expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
      expect(screen.getByText(/1h\s*·\s*ALTA/i)).toBeInTheDocument()
      expect(planoApi.buscarPlano).toHaveBeenCalledWith({ data: '2026-10-20' })
    })

    it('mostra capacidade usada vs disponível', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      renderHome()

      expect(await screen.findByText(/capacidade/i)).toBeInTheDocument()
      expect(screen.getByText(/1h\s*\/\s*2h/i)).toBeInTheDocument()
    })

    it('mostra estado RASCUNHO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      renderHome()

      expect(await screen.findByText(/rascunho/i)).toBeInTheDocument()
    })

    it('mostra estado APROVADO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      renderHome()

      expect(await screen.findByText(/aprovado/i)).toBeInTheDocument()
    })

    it('mostra estado vazio quando não há plano no dia', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(null)

      renderHome()

      expect(await screen.findByText(/ainda não há plano/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /^gerar plano$/i })).toBeInTheDocument()
    })

    it('mostra erro quando a consulta falha', async () => {
      vi.mocked(planoApi.buscarPlano).mockRejectedValue(
        new Error('Não foi possível carregar o plano.'),
      )

      renderHome()

      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent(
          /não foi possível carregar o plano/i,
        )
      })
    })
  })

  describe('gerar plano', () => {
    it('gera plano a partir do estado vazio e exibe o resultado', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(null)
      vi.mocked(planoApi.gerarPlano).mockResolvedValue(planoBase)

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /^gerar plano$/i }))

      await waitFor(() => {
        expect(planoApi.gerarPlano).toHaveBeenCalledWith('2026-10-20')
      })
      expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
      expect(screen.getByText(/rascunho/i)).toBeInTheDocument()
    })

    it('permite gerar nova versão em RASCUNHO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.gerarPlano).mockResolvedValue({
        ...planoBase,
        versao: 2,
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /gerar nova versão/i }))

      await waitFor(() => {
        expect(planoApi.gerarPlano).toHaveBeenCalledWith('2026-10-20')
      })
      expect(await screen.findByText(/versão 2/i)).toBeInTheDocument()
    })

    it('não mostra gerar quando o plano está APROVADO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      renderHome()

      expect(await screen.findByText(/aprovado/i)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /gerar/i })).not.toBeInTheDocument()
    })
  })

  describe('F2 — aprovar', () => {
    it('T14: CTA aprovar só em RASCUNHO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      renderHome()

      expect(await screen.findByText(/aprovado/i)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /aprovar plano/i })).not.toBeInTheDocument()
    })

    it('T14: após aprovar a UI reflete APROVADO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.aprovarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /aprovar plano/i }))

      await waitFor(() => {
        expect(planoApi.aprovarPlano).toHaveBeenCalledWith('p-1')
      })
      expect(await screen.findByText(/^aprovado$/i)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /aprovar plano/i })).not.toBeInTheDocument()
    })
  })

  describe('F3 — remover bloco', () => {
    it('remove bloco do rascunho e atualiza a lista', async () => {
      vi.mocked(planoApi.buscarPlano)
        .mockResolvedValueOnce(planoBase)
        .mockResolvedValueOnce({ ...planoBase, blocos: [] })
      vi.mocked(planoApi.removerBloco).mockResolvedValue(undefined)

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /remover estudar spring/i }))

      await waitFor(() => {
        expect(planoApi.removerBloco).toHaveBeenCalledWith('p-1', 'b-1')
      })
      await waitFor(() => {
        expect(screen.queryByText('Estudar Spring')).not.toBeInTheDocument()
      })
      expect(screen.getByText(/nenhum bloco neste plano/i)).toBeInTheDocument()
    })

    it('não mostra remover quando plano está APROVADO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      renderHome()

      expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /remover/i })).not.toBeInTheDocument()
    })
  })

  describe('F4 — adicionar tarefa', () => {
    it('adiciona tarefa elegível e mostra feedback de conflito', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.adicionarBloco).mockResolvedValue({
        ...planoBase,
        temConflito: true,
        mensagem: 'Capacidade estourada após adicionar tarefa.',
        blocos: [
          ...planoBase.blocos,
          {
            id: 'b-2',
            duracaoMinutos: 60,
            ordem: 2,
            tarefa: {
              id: 't-2',
              titulo: 'Ler docs',
              prioridade: 'MEDIA',
              status: 'PENDENTE',
              duracaoHoras: 1,
            },
          },
        ],
      })

      const user = userEvent.setup()
      renderHome()

      const select = await screen.findByLabelText(/^tarefa$/i)
      await user.selectOptions(select, 't-2')
      await user.click(screen.getByRole('button', { name: /adicionar ao plano/i }))

      await waitFor(() => {
        expect(planoApi.adicionarBloco).toHaveBeenCalledWith('p-1', 't-2')
      })
      expect(await screen.findByText('Ler docs')).toBeInTheDocument()
      const banner = await screen.findByRole('status', { name: /conflito/i })
      expect(banner).toHaveTextContent(/capacidade estourada/i)
    })

    it('não mostra picker quando plano está APROVADO', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      renderHome()

      expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: /adicionar tarefa/i })).not.toBeInTheDocument()
    })
  })

  describe('F5 — detalhe da tarefa', () => {
    it('navega para a rota de detalhe ao clicar no card', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      const user = userEvent.setup()
      renderHome()

      const link = await screen.findByRole('link', { name: 'Estudar Spring' })
      expect(link).toHaveAttribute('href', '/tarefas/t-1')

      await user.click(link)

      expect(await screen.findByText('Detalhe da tarefa')).toBeInTheDocument()
    })
  })

  describe('modo execução (plano APROVADO)', () => {
    it('T10: mostra CTA iniciar e não mostra aprovar/remover do rascunho', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      renderHome()

      expect(await screen.findByRole('button', { name: /iniciar estudar spring/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /aprovar plano/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /remover/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: /adicionar tarefa/i })).not.toBeInTheDocument()
      expect(execucaoApi.listarExecucoes).toHaveBeenCalledWith('2026-10-20')
    })

    it('T11: mostra tempo restante quando em andamento', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })
      vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
        planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
        itens: [
          {
            ...itemExecucaoBase,
            execucao: {
              id: 'e-1',
              blocoPlanoId: 'b-1',
              status: 'EM_ANDAMENTO',
              iniciadoEm: new Date(Date.now() - 28 * 60_000).toISOString(),
              concluidoEm: null,
              abortadoEm: null,
            },
          },
        ],
      })

      renderHome()

      expect(await screen.findByLabelText(/tempo restante/i)).toHaveTextContent(/restante:\s*32min/i)
      expect(screen.getByRole('button', { name: /concluir estudar spring/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /abortar estudar spring/i })).toBeInTheDocument()
    })

    it('inicia execução e atualiza a lista', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })
      vi.mocked(execucaoApi.iniciarExecucao).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-10-20T14:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      })
      vi.mocked(execucaoApi.listarExecucoes)
        .mockResolvedValueOnce({
          planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
          itens: [itemExecucaoBase],
        })
        .mockResolvedValueOnce({
          planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
          itens: [
            {
              ...itemExecucaoBase,
              execucao: {
                id: 'e-1',
                blocoPlanoId: 'b-1',
                status: 'EM_ANDAMENTO',
                iniciadoEm: '2026-10-20T14:00:00Z',
                concluidoEm: null,
                abortadoEm: null,
              },
            },
          ],
        })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /iniciar estudar spring/i }))

      await waitFor(() => {
        expect(execucaoApi.iniciarExecucao).toHaveBeenCalledWith('b-1')
      })
      expect(await screen.findByRole('button', { name: /concluir estudar spring/i })).toBeInTheDocument()
    })

    it('conclui e aborta execução', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })
      const emAndamento: ItemExecucaoResponse = {
        ...itemExecucaoBase,
        execucao: {
          id: 'e-1',
          blocoPlanoId: 'b-1',
          status: 'EM_ANDAMENTO',
          iniciadoEm: '2026-10-20T14:00:00Z',
          concluidoEm: null,
          abortadoEm: null,
        },
      }
      vi.mocked(execucaoApi.listarExecucoes)
        .mockResolvedValueOnce({
          planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
          itens: [emAndamento],
        })
        .mockResolvedValueOnce({
          planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
          itens: [
            {
              ...itemExecucaoBase,
              execucao: {
                id: 'e-1',
                blocoPlanoId: 'b-1',
                status: 'CONCLUIDO',
                iniciadoEm: '2026-10-20T14:00:00Z',
                concluidoEm: '2026-10-20T15:00:00Z',
                abortadoEm: null,
              },
            },
          ],
        })
      vi.mocked(execucaoApi.concluirExecucao).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'CONCLUIDO',
        iniciadoEm: '2026-10-20T14:00:00Z',
        concluidoEm: '2026-10-20T15:00:00Z',
        abortadoEm: null,
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /concluir estudar spring/i }))

      await waitFor(() => {
        expect(execucaoApi.concluirExecucao).toHaveBeenCalledWith('e-1')
      })
      expect(await screen.findByText(/concluído/i)).toBeInTheDocument()
    })
  })
})
