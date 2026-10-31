import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '../auth/session/AuthContext'
import * as meApi from '../auth/api/meApi'
import * as capacidadeOverrideApi from '../capacidade/api/capacidadeOverrideApi'
import * as capacidadeResolvidaApi from '../capacidade/api/capacidadeResolvidaApi'
import * as execucaoApi from '../execucao/api/execucaoApi'
import type { ItemExecucaoResponse } from '../execucao/api/execucaoApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as planoApi from '../planner/api/planoApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import * as reservaProjetoApi from '../projeto/api/reservaProjetoApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import type { TarefaResponse } from '../tarefa/api/tarefaApi'
import { QuickAddProvider } from '../layout/QuickAddContext'
import { XpProgressProvider } from '../xp/XpProgressContext'
import { HomePage } from './HomePage'

vi.mock('../planner/api/planoApi', () => ({
  buscarPlano: vi.fn(),
  gerarPlano: vi.fn(),
  aprovarPlano: vi.fn(),
  aceitarProposta: vi.fn(),
  descartarProposta: vi.fn(),
  adicionarBloco: vi.fn(),
  removerBloco: vi.fn(),
  listarDatasDaProposta: vi.fn().mockResolvedValue([]),
}))

vi.mock('../capacidade/api/capacidadeResolvidaApi', () => ({
  consultarCapacidadeResolvida: vi.fn(),
}))

vi.mock('../capacidade/api/capacidadeOverrideApi', () => ({
  listarCapacidadesOverride: vi.fn(),
  criarCapacidadeOverride: vi.fn(),
  atualizarCapacidadeOverride: vi.fn(),
  excluirCapacidadeOverride: vi.fn(),
  restaurarCapacidadeOverride: vi.fn(),
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

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
  }),
}))

vi.mock('../projeto/api/reservaProjetoApi', () => ({
  listarReservas: vi.fn().mockResolvedValue([]),
  criarReserva: vi.fn(),
  atualizarReserva: vi.fn(),
}))

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
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
  duracaoMinutos: 60,
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
  propostaId: null,
  propostaPendente: false,
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
        duracaoMinutos: 60,
        prazo: '2026-10-22T15:00:00Z',
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
    duracaoMinutos: 60,
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
      <ToastProvider>
        <XpProgressProvider>
          <MemoryRouter initialEntries={[initialPath]}>
            <QuickAddProvider>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/tarefas/:id" element={<p>Detalhe da tarefa</p>} />
                <Route path="/capacidade" element={<p>Página capacidade</p>} />
              </Routes>
            </QuickAddProvider>
          </MemoryRouter>
        </XpProgressProvider>
      </ToastProvider>
    </AuthProvider>,
  )
}

describe('HomePage — plano do dia', () => {
  beforeEach(() => {
    vi.mocked(planoApi.buscarPlano).mockReset()
    vi.mocked(planoApi.gerarPlano).mockReset()
    vi.mocked(planoApi.aprovarPlano).mockReset()
    vi.mocked(planoApi.aceitarProposta).mockReset()
    vi.mocked(planoApi.descartarProposta).mockReset()
    vi.mocked(planoApi.adicionarBloco).mockReset()
    vi.mocked(planoApi.removerBloco).mockReset()
    vi.mocked(planoApi.listarDatasDaProposta).mockReset().mockResolvedValue([])
    vi.mocked(reservaProjetoApi.listarReservas).mockReset().mockResolvedValue([])
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida).mockReset()
    vi.mocked(capacidadeOverrideApi.listarCapacidadesOverride).mockReset()
    vi.mocked(capacidadeOverrideApi.criarCapacidadeOverride).mockReset()
    vi.mocked(capacidadeOverrideApi.atualizarCapacidadeOverride).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(execucaoApi.listarExecucoes).mockReset()
    vi.mocked(execucaoApi.iniciarExecucao).mockReset()
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 5,
      nivel: 1,
      xpNoNivel: 5,
      xpParaProximoNivel: 10,
    })
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida).mockResolvedValue([
      { data: '2026-10-20', horas: 2, origem: 'SEMANAL' },
    ])
    vi.mocked(capacidadeOverrideApi.listarCapacidadesOverride).mockResolvedValue([])
    vi.mocked(capacidadeOverrideApi.criarCapacidadeOverride).mockResolvedValue([
      {
        id: 'o-hoje',
        data: '2026-10-20',
        horas: 1,
        ativo: true,
        createdAt: '2026-10-20T10:00:00Z',
        updatedAt: '2026-10-20T10:00:00Z',
      },
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

    it('T5: revisão mostra mensagem de conflito de prazo da API', async () => {
      const mensagemPrazo =
        'Não cabe até o prazo: "Relatorio" — 240 min fora (prazo 23/10/2026).'
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        temConflito: true,
        mensagem: mensagemPrazo,
      })

      renderHome()

      const banner = await screen.findByRole('status', { name: /conflito/i })
      expect(banner).toHaveTextContent(mensagemPrazo)
      expect(banner).toHaveTextContent('Relatorio')
      expect(banner).toHaveTextContent('240')
    })

    it('T2: banner de conflito oferece ações acionáveis', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        temConflito: true,
        mensagem: 'Capacidade insuficiente: ao menos uma tarefa foi alocada parcialmente.',
      })

      const user = userEvent.setup()
      renderHome()

      expect(
        await screen.findByRole('button', { name: /remover tarefa de menor prioridade/i }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: /aumentar capacidade de hoje/i }),
      ).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /aumentar capacidade de hoje/i }))
      expect(
        await screen.findByRole('group', { name: /ajustar capacidade de hoje/i }),
      ).toBeInTheDocument()
    })

    it('T4: atalho na Home ajusta override de hoje e oferece gerar nova versão', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.gerarPlano).mockResolvedValue({
        ...planoBase,
        versao: 2,
      })
      vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida)
        .mockResolvedValueOnce([{ data: '2026-10-20', horas: 2, origem: 'SEMANAL' }])
        .mockResolvedValueOnce([{ data: '2026-10-20', horas: 1, origem: 'OVERRIDE' }])

      const user = userEvent.setup()
      renderHome()

      await user.click(
        await screen.findByRole('button', { name: /^ajustar capacidade de hoje$/i }),
      )
      expect(screen.getByLabelText('Horas de hoje')).toHaveTextContent('2h')

      await user.click(screen.getByRole('button', { name: /diminuir horas de hoje/i }))
      expect(screen.getByLabelText('Horas de hoje')).toHaveTextContent('1h')

      await user.click(screen.getByRole('button', { name: /salvar capacidade de hoje/i }))

      await waitFor(() => {
        expect(capacidadeOverrideApi.criarCapacidadeOverride).toHaveBeenCalledWith({
          data: '2026-10-20',
          horas: 1,
        })
      })

      expect(
        await screen.findByText(/capacidade de hoje atualizada/i),
      ).toBeInTheDocument()
      await user.click(
        screen.getByRole('button', { name: /^gerar nova versão do plano$/i }),
      )

      await waitFor(() => {
        expect(planoApi.gerarPlano).toHaveBeenCalledWith('2026-10-20')
      })
    })

    it('lista blocos do plano do dia corrente', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      renderHome()

      expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
      expect(screen.getByText('Alta')).toBeInTheDocument()
      expect(planoApi.buscarPlano).toHaveBeenCalledWith({ data: '2026-10-20' })
    })

    it('T1: bloco de rascunho mostra justificativa com prazo e prioridade em PT', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      renderHome()

      expect(
        await screen.findByText(/prazo em 2 dias · prioridade alta/i),
      ).toBeInTheDocument()
    })

    it('mostra capacidade usada vs disponível', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)

      renderHome()

      expect(await screen.findByText(/1h de 2h/i)).toBeInTheDocument()
      expect(screen.getByText('1 bloco')).toBeInTheDocument()
      expect(screen.getByRole('progressbar', { name: /capacidade: 1h \/ 2h/i })).toBeInTheDocument()
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
      expect(
        screen.getByText(/gere o plano → revise → aprove → execute/i),
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /^gerar plano de hoje$/i })).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: /^ajustar capacidade de hoje$/i }),
      ).toBeInTheDocument()
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

      await user.click(await screen.findByRole('button', { name: /^gerar plano de hoje$/i }))

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

    it('T3: após gerar, UI marca diferença vs snapshot anterior', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.gerarPlano).mockResolvedValue({
        ...planoBase,
        id: 'p-2',
        versao: 2,
        blocos: [
          {
            id: 'b-novo',
            duracaoMinutos: 60,
            ordem: 1,
            tarefa: {
              id: 't-2',
              titulo: 'Ler docs',
              prioridade: 'MEDIA',
              status: 'PENDENTE',
              duracaoMinutos: 60,
              prazo: '2030-01-01T12:00:00Z',
            },
          },
        ],
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /gerar nova versão/i }))

      expect(await screen.findByLabelText(/bloco novo nesta versão/i)).toBeInTheDocument()
      expect(screen.getByText(/removidos na nova versão: estudar spring/i)).toBeInTheDocument()
      expect(screen.getByText('Ler docs')).toBeInTheDocument()
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

  describe('F1 — proposta pendente', () => {
    it('T4: CTAs aceitar e descartar visíveis com proposta pendente', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        propostaId: 'prop-1',
        propostaPendente: true,
        versao: 2,
      })

      renderHome()

      expect(await screen.findByRole('button', { name: /aceitar proposta/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /^descartar$/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^aprovar/i })).not.toBeInTheDocument()
    })

    it('T10: aceitar e descartar proposta ainda pedem confirmação', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        propostaId: 'prop-1',
        propostaPendente: true,
        versao: 2,
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /aceitar proposta/i }))
      expect(screen.getByRole('heading', { name: /aceitar proposta\?/i })).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /^cancelar$/i }))
      expect(planoApi.aceitarProposta).not.toHaveBeenCalled()

      await user.click(screen.getByRole('button', { name: /^descartar$/i }))
      expect(screen.getByRole('heading', { name: /descartar proposta\?/i })).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /^cancelar$/i }))
      expect(planoApi.descartarProposta).not.toHaveBeenCalled()
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
      expect(screen.queryByRole('button', { name: /^aprovar/i })).not.toBeInTheDocument()
    })

    it('T10: aprovar chama a API direto, sem dialog, com resumo no botão', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.aprovarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /^aprovar 1 bloco · 1h$/i }))

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      await waitFor(() => {
        expect(planoApi.aprovarPlano).toHaveBeenCalledWith('p-1')
      })
      expect(await screen.findByText(/^aprovado$/i)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^aprovar/i })).not.toBeInTheDocument()
      expect(execucaoApi.listarExecucoes).toHaveBeenCalledWith('2026-10-20')
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

    it('T9: bloco some na hora, Desfazer re-adiciona a tarefa', async () => {
      let liberarDelete: () => void = () => undefined
      vi.mocked(planoApi.buscarPlano)
        .mockResolvedValueOnce(planoBase)
        .mockResolvedValue({ ...planoBase, blocos: [] })
      vi.mocked(planoApi.removerBloco).mockImplementation(
        () =>
          new Promise<void>((resolve) => {
            liberarDelete = resolve
          }),
      )
      vi.mocked(planoApi.adicionarBloco).mockResolvedValue(planoBase)

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /remover estudar spring/i }))
      expect(screen.queryByText('Estudar Spring')).not.toBeInTheDocument()
      expect(planoApi.removerBloco).toHaveBeenCalledWith('p-1', 'b-1')

      liberarDelete()
      await user.click(await screen.findByRole('button', { name: /^desfazer$/i }))

      await waitFor(() => {
        expect(planoApi.adicionarBloco).toHaveBeenCalledWith('p-1', 't-1')
      })
      expect(await screen.findByRole('link', { name: 'Estudar Spring' })).toBeInTheDocument()
    })

    it('T9: erro no DELETE restaura o bloco e oferece tentar de novo', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.removerBloco).mockRejectedValue(new Error('Falha ao remover'))

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /remover estudar spring/i }))

      expect(await screen.findByText('Falha ao remover')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Estudar Spring' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /tentar de novo/i })).toBeInTheDocument()
      expect(planoApi.buscarPlano).toHaveBeenCalledTimes(1)
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
              duracaoMinutos: 60,
              prazo: '2030-01-01T12:00:00Z',
            },
          },
        ],
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('option', { name: /ler docs/i }))
      await user.click(screen.getByRole('button', { name: /adicionar ao plano/i }))

      await waitFor(() => {
        expect(planoApi.adicionarBloco).toHaveBeenCalledWith('p-1', 't-2')
      })
      expect(await screen.findByText('Ler docs')).toBeInTheDocument()
      const banner = await screen.findByRole('status', { name: /conflito/i })
      expect(banner).toHaveTextContent(/capacidade estourada/i)
    })

    it('T5: adicionar tarefa filtra por texto na lista pesquisável', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
        content: [
          tarefaExtra,
          {
            ...tarefaExtra,
            id: 't-3',
            titulo: 'Correr no parque',
            prazo: '2026-10-21T12:00:00Z',
          },
        ],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 100,
      })

      const user = userEvent.setup()
      renderHome()

      expect(await screen.findByRole('option', { name: /ler docs/i })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: /correr no parque/i })).toBeInTheDocument()

      const chamadasAntes = vi.mocked(tarefaApi.listarTarefas).mock.calls.length
      await user.type(screen.getByRole('combobox', { name: /buscar tarefa/i }), 'correr')
      expect(screen.getByRole('option', { name: /correr no parque/i })).toBeInTheDocument()
      expect(screen.queryByRole('option', { name: /ler docs/i })).not.toBeInTheDocument()
      // T12: filtro local, sem requisição por tecla
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(chamadasAntes)
    })

    it('mostra linha “adicionando…” enquanto a API não responde', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoBase)
      vi.mocked(planoApi.adicionarBloco).mockImplementation(() => new Promise(() => undefined))

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('option', { name: /ler docs/i }))
      await user.click(screen.getByRole('button', { name: /adicionar ao plano/i }))

      const lista = screen.getAllByRole('list').find((el) => el.classList.contains('plano-blocos'))
      expect(lista).toHaveTextContent(/ler docs\s*adicionando…/i)
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

      expect(await screen.findByLabelText(/tempo restante/i)).toHaveTextContent(/32min/i)
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

    it('T5: iniciar mostra “Em andamento” antes do POST resolver, sem recarregar execuções', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })
      let resolverPost: (value: Awaited<ReturnType<typeof execucaoApi.iniciarExecucao>>) => void =
        () => undefined
      vi.mocked(execucaoApi.iniciarExecucao).mockImplementation(
        () =>
          new Promise((resolve) => {
            resolverPost = resolve
          }),
      )

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /iniciar estudar spring/i }))

      expect(screen.getByText('Em andamento')).toBeInTheDocument()
      expect(screen.getByRole('region', { name: /agora/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir estudar spring/i })).toBeDisabled()

      resolverPost({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: new Date().toISOString(),
        concluidoEm: null,
        abortadoEm: null,
      })

      expect(
        await screen.findByRole('button', { name: /concluir estudar spring/i }),
      ).toBeEnabled()
      expect(execucaoApi.listarExecucoes).toHaveBeenCalledTimes(1)
    })

    it('T6: erro ao iniciar volta a “Não iniciado” e mostra toast com tentar de novo', async () => {
      vi.mocked(planoApi.buscarPlano).mockResolvedValue({
        ...planoBase,
        status: 'APROVADO',
      })
      vi.mocked(execucaoApi.iniciarExecucao).mockRejectedValue(new Error('Falha ao iniciar'))

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /iniciar estudar spring/i }))

      expect(await screen.findByText('Falha ao iniciar')).toBeInTheDocument()
      expect(screen.getByText('Não iniciado')).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: /agora/i })).not.toBeInTheDocument()

      vi.mocked(execucaoApi.iniciarExecucao).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: new Date().toISOString(),
        concluidoEm: null,
        abortadoEm: null,
      })
      await user.click(screen.getByRole('button', { name: /tentar de novo/i }))

      await waitFor(() => {
        expect(execucaoApi.iniciarExecucao).toHaveBeenCalledTimes(2)
      })
      expect(await screen.findByRole('button', { name: /concluir estudar spring/i })).toBeEnabled()
    })

    it('conclui execução', async () => {
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
        xp: {
          ganhoNesteRequest: 120,
          eventos: [{ tipo: 'BLOCO_CONCLUIDO', quantidade: 120 }],
          progresso: {
            xpTotal: 125,
            nivel: 5,
            xpNoNivel: 15,
            xpParaProximoNivel: 50,
          },
        },
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /concluir estudar spring/i }))

      await waitFor(() => {
        expect(execucaoApi.concluirExecucao).toHaveBeenCalledWith('e-1')
      })
      expect(await screen.findByText('Concluído')).toBeInTheDocument()
      expect(await screen.findByText('+120 XP')).toBeInTheDocument()
      expect(await screen.findByText('Nível 5!')).toBeInTheDocument()
      expect(await screen.findByText('Plano de hoje concluído')).toBeInTheDocument()
      expect(await screen.findByText('+120 XP hoje')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: /ver histórico/i })).toHaveAttribute(
        'href',
        '/historico',
      )
      // T7: XP vem da resposta; sem leitura extra de execuções
      expect(execucaoApi.listarExecucoes).toHaveBeenCalledTimes(1)
    })

    it('T5/T7: toast +XP e level-up após concluir com ganho', async () => {
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
        xp: {
          ganhoNesteRequest: 99,
          eventos: [
            { tipo: 'BLOCO_CONCLUIDO', quantidade: 90 },
            { tipo: 'TAREFA_FINALIZADA', quantidade: 9 },
          ],
          progresso: {
            xpTotal: 104,
            nivel: 5,
            xpNoNivel: 4,
            xpParaProximoNivel: 50,
          },
        },
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /concluir estudar spring/i }))

      expect(await screen.findByText('+99 XP (inclui bônus)')).toBeInTheDocument()
      expect(await screen.findByText('Nível 5!')).toBeInTheDocument()
    })

    it('T3: abortar execução exige confirmação', async () => {
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
                status: 'ABORTADO',
                iniciadoEm: '2026-10-20T14:00:00Z',
                concluidoEm: null,
                abortadoEm: '2026-10-20T14:30:00Z',
              },
            },
          ],
        })
      vi.mocked(execucaoApi.abortarExecucao).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'ABORTADO',
        iniciadoEm: '2026-10-20T14:00:00Z',
        concluidoEm: null,
        abortadoEm: '2026-10-20T14:30:00Z',
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /abortar estudar spring/i }))
      expect(execucaoApi.abortarExecucao).not.toHaveBeenCalled()
      expect(screen.getByRole('heading', { name: /abortar execução/i })).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /^abortar$/i }))

      await waitFor(() => {
        expect(execucaoApi.abortarExecucao).toHaveBeenCalledWith('e-1')
      })
    })

    it('T3: cancelar confirmação não aborta', async () => {
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
              iniciadoEm: '2026-10-20T14:00:00Z',
              concluidoEm: null,
              abortadoEm: null,
            },
          },
        ],
      })

      const user = userEvent.setup()
      renderHome()

      await user.click(await screen.findByRole('button', { name: /abortar estudar spring/i }))
      await user.click(screen.getByRole('button', { name: /^cancelar$/i }))

      expect(execucaoApi.abortarExecucao).not.toHaveBeenCalled()
    })
  })

  describe('carga do dia', () => {
    function planoDoDia(data: string, titulo: string): PlanoResponse {
      return {
        ...planoBase,
        id: `p-${data}`,
        data,
        propostaId: 'prop-1',
        blocos: [
          {
            ...planoBase.blocos[0],
            id: `b-${data}`,
            tarefa: { ...planoBase.blocos[0].tarefa, id: `t-${data}`, titulo },
          },
        ],
      }
    }

    it('T3: plano e capacidade são pedidos juntos; reservas não bloqueiam', async () => {
      let liberar: (value: PlanoResponse | null) => void = () => undefined
      vi.mocked(planoApi.buscarPlano).mockImplementation(
        () =>
          new Promise((resolve) => {
            liberar = resolve
          }),
      )
      vi.mocked(reservaProjetoApi.listarReservas).mockRejectedValue(new Error('falhou'))

      renderHome()

      expect(planoApi.buscarPlano).toHaveBeenCalledWith({ data: '2026-10-20' })
      expect(capacidadeResolvidaApi.consultarCapacidadeResolvida).toHaveBeenCalledWith({
        dataDe: '2026-10-20',
        dataAte: '2026-10-20',
      })
      expect(reservaProjetoApi.listarReservas).toHaveBeenCalled()

      liberar(planoBase)
      expect(await screen.findByRole('link', { name: 'Estudar Spring' })).toBeInTheDocument()
    })

    it('T4: resposta antiga de troca de dia é descartada', async () => {
      const pendentes = new Map<string, (plano: PlanoResponse) => void>()
      vi.mocked(planoApi.listarDatasDaProposta).mockResolvedValue([
        '2026-10-20',
        '2026-10-21',
        '2026-10-22',
      ])
      vi.mocked(planoApi.buscarPlano).mockImplementation(({ data }) =>
        data === '2026-10-20'
          ? Promise.resolve(planoDoDia(data, 'Tarefa de hoje'))
          : new Promise((resolve) => pendentes.set(data, resolve)),
      )

      const user = userEvent.setup()
      renderHome()

      expect(await screen.findByRole('link', { name: 'Tarefa de hoje' })).toBeInTheDocument()

      await user.click(await screen.findByRole('button', { name: 'qua 21/10' }))
      await user.click(screen.getByRole('button', { name: 'qui 22/10' }))

      // Conteúdo atual segue visível (sem skeleton) enquanto o novo dia não chega.
      expect(screen.getByRole('link', { name: 'Tarefa de hoje' })).toBeInTheDocument()
      expect(document.querySelector('.skeleton--plano-blocos')).not.toBeInTheDocument()

      pendentes.get('2026-10-22')?.(planoDoDia('2026-10-22', 'Tarefa de quinta'))
      expect(await screen.findByRole('link', { name: 'Tarefa de quinta' })).toBeInTheDocument()

      pendentes.get('2026-10-21')?.(planoDoDia('2026-10-21', 'Tarefa de quarta'))
      await waitFor(() => {
        expect(screen.queryByRole('link', { name: 'Tarefa de quarta' })).not.toBeInTheDocument()
      })
      expect(screen.getByRole('link', { name: 'Tarefa de quinta' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'qui 22/10' })).toHaveAttribute(
        'aria-current',
        'date',
      )
    })
  })

  describe('estados de feedback (T4)', () => {
    it('mostra skeleton enquanto carrega o plano', async () => {
      let liberar: (value: PlanoResponse | null) => void = () => undefined
      vi.mocked(planoApi.buscarPlano).mockImplementation(
        () =>
          new Promise((resolve) => {
            liberar = resolve
          }),
      )

      renderHome()

      expect(screen.getByRole('status', { busy: true })).toBeInTheDocument()
      expect(document.querySelector('.skeleton--plano-blocos')).toBeInTheDocument()

      liberar(null)
      expect(await screen.findByText(/ainda não há plano/i)).toBeInTheDocument()
    })
  })
})
