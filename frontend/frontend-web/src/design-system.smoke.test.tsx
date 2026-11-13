import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from './auth/api/meApi'
import { CadastroPage } from './auth/pages/CadastroPage'
import { LoginPage } from './auth/pages/LoginPage'
import { AuthProvider } from './auth/session/AuthContext'
import { clearAccessToken } from './auth/session/tokenStore'
import * as capacidadeOverrideApi from './capacidade/api/capacidadeOverrideApi'
import * as capacidadeResolvidaApi from './capacidade/api/capacidadeResolvidaApi'
import * as capacidadeSemanalApi from './capacidade/api/capacidadeSemanalApi'
import { CapacidadePage } from './capacidade/pages/CapacidadePage'
import * as execucaoApi from './execucao/api/execucaoApi'
import { SessaoAtivaProvider } from './execucao/SessaoAtivaContext'
import { ToastProvider } from './feedback/ToastProvider'
import * as historicoApi from './historico/api/historicoApi'
import { HistoricoPage } from './historico/pages/HistoricoPage'
import { QuickAddProvider } from './layout/QuickAddContext'
import { HomePage } from './pages/HomePage'
import * as planoApi from './planner/api/planoApi'
import * as tarefaApi from './tarefa/api/tarefaApi'
import * as tagApi from './tag/api/tagApi'
import { TagsPage } from './tag/pages/TagsPage'
import { TarefasPage } from './tarefa/pages/TarefasPage'
import { XpProgressProvider } from './xp/XpProgressContext'

vi.mock('./auth/api/authApi', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  cadastrarUsuario: vi.fn(),
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

vi.mock('./planner/api/planoApi', () => ({
  buscarPlano: vi.fn(),
  gerarPlano: vi.fn(),
  aprovarPlano: vi.fn(),
  aceitarProposta: vi.fn(),
  descartarProposta: vi.fn(),
  adicionarBloco: vi.fn(),
  removerBloco: vi.fn(),
  listarDatasDaProposta: vi.fn().mockResolvedValue([]),
}))

vi.mock('./capacidade/api/capacidadeResolvidaApi', () => ({
  consultarCapacidadeResolvida: vi.fn(),
}))

vi.mock('./capacidade/api/capacidadeSemanalApi', () => ({
  listarCapacidadesSemanais: vi.fn(),
  criarCapacidadeSemanal: vi.fn(),
  atualizarCapacidadeSemanal: vi.fn(),
  excluirCapacidadeSemanal: vi.fn(),
  restaurarCapacidadeSemanal: vi.fn(),
}))

vi.mock('./capacidade/api/capacidadeOverrideApi', () => ({
  listarCapacidadesOverride: vi.fn(),
  criarCapacidadeOverride: vi.fn(),
  atualizarCapacidadeOverride: vi.fn(),
  excluirCapacidadeOverride: vi.fn(),
  restaurarCapacidadeOverride: vi.fn(),
}))

vi.mock('./tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  criarTarefa: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
  reordenarTarefas: vi.fn(),
}))

vi.mock('./execucao/api/execucaoApi', () => ({
  listarExecucoes: vi.fn(),
  iniciarExecucao: vi.fn(),
  concluirExecucao: vi.fn(),
  abortarExecucao: vi.fn(),
}))

vi.mock('./projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
  }),
}))

vi.mock('./projeto/api/reservaProjetoApi', () => ({
  listarReservas: vi.fn().mockResolvedValue([]),
  criarReserva: vi.fn(),
  atualizarReserva: vi.fn(),
}))

vi.mock('./historico/api/historicoApi', () => ({
  consultarHistoricoDia: vi.fn(),
  consultarHistoricoSemana: vi.fn(),
  consultarCalibracao: vi.fn(),
}))

vi.mock('./auth/api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
}))

vi.mock('./tag/api/tagApi', () => ({
  TAG_COR_PADRAO: '#6B7280',
  listarTags: vi.fn(),
  criarTag: vi.fn(),
  atualizarTag: vi.fn(),
  excluirTag: vi.fn(),
}))

vi.mock('./planner/data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./planner/data')>()
  return {
    ...actual,
    dataHojeIso: () => '2026-10-20',
  }
})

describe('design system smoke (T1)', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.clearAllMocks()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: null,
      itens: [],
    })
    vi.mocked(tagApi.listarTags).mockResolvedValue([])
  })

  it('Login monta e CTA Entrar permanece clicável', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    const entrar = screen.getByRole('button', { name: /entrar/i })
    expect(entrar).toHaveClass('btn', 'btn--primary')
    await user.click(entrar)
    expect((await screen.findAllByRole('alert')).length).toBeGreaterThan(0)
  })

  it('Home monta e CTA Gerar plano permanece clicável', async () => {
    vi.mocked(planoApi.buscarPlano).mockResolvedValue(null)
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida).mockResolvedValue([])
    vi.mocked(planoApi.gerarPlano).mockResolvedValue({
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
      blocos: [],
    })
    const user = userEvent.setup()

    render(
      <AuthProvider>
        <ToastProvider>
          <XpProgressProvider>
            <SessaoAtivaProvider>
              <MemoryRouter>
                <QuickAddProvider>
                  <HomePage />
                </QuickAddProvider>
              </MemoryRouter>
            </SessaoAtivaProvider>
          </XpProgressProvider>
        </ToastProvider>
      </AuthProvider>,
    )

    const gerar = await screen.findByRole('button', { name: /^gerar plano de hoje$/i })
    expect(gerar).toHaveClass('btn', 'btn--primary')
    await user.click(gerar)
    await waitFor(() => {
      expect(planoApi.gerarPlano).toHaveBeenCalled()
    })
  })

  it('Tarefas monta e CTAs principais permanecem clicáveis', async () => {
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })
    const user = userEvent.setup()

    render(
      <ToastProvider>
        <XpProgressProvider>
          <SessaoAtivaProvider>
            <MemoryRouter initialEntries={['/tarefas']}>
              <QuickAddProvider>
                <Routes>
                  <Route path="/tarefas" element={<TarefasPage />} />
                </Routes>
              </QuickAddProvider>
            </MemoryRouter>
          </SessaoAtivaProvider>
        </XpProgressProvider>
      </ToastProvider>,
    )

    const prioridade = await screen.findByRole('button', { name: /^prioridade$/i })
    expect(prioridade).toHaveClass('btn', 'btn--secondary')
    await user.click(prioridade)
    await user.click(screen.getByLabelText(/^alta$/i))
    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
        expect.objectContaining({ prioridade: ['ALTA'] }),
        expect.any(AbortSignal),
      )
    })
    const empty = (await screen.findByText(/nada com estes filtros/i)).closest('.tarefas-empty')
    expect(
      within(empty as HTMLElement).getByRole('button', { name: /^limpar filtros$/i }),
    ).toHaveClass('btn', 'btn--secondary')
  })

  it('Capacidade monta e CTA Salvar semana permanece clicável', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(2026, 9, 8, 12, 0, 0))
    vi.mocked(capacidadeSemanalApi.listarCapacidadesSemanais).mockResolvedValue([])
    vi.mocked(capacidadeSemanalApi.criarCapacidadeSemanal).mockResolvedValue([])
    vi.mocked(capacidadeOverrideApi.listarCapacidadesOverride).mockResolvedValue([])
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida).mockResolvedValue([
      { data: '2026-10-05', horas: null, origem: 'VAZIO' },
      { data: '2026-10-06', horas: null, origem: 'VAZIO' },
      { data: '2026-10-07', horas: null, origem: 'VAZIO' },
      { data: '2026-10-08', horas: null, origem: 'VAZIO' },
      { data: '2026-10-09', horas: null, origem: 'VAZIO' },
      { data: '2026-10-10', horas: null, origem: 'VAZIO' },
      { data: '2026-10-11', horas: null, origem: 'VAZIO' },
    ])
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    render(
      <ToastProvider>
        <MemoryRouter initialEntries={['/capacidade']}>
          <Routes>
            <Route path="/capacidade" element={<CapacidadePage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>,
    )

    await screen.findByRole('heading', { name: /capacidade/i })
    await user.click(screen.getByRole('button', { name: /aumentar horas de segunda/i }))
    const salvar = screen.getByRole('button', { name: /salvar semana/i })
    expect(salvar).toHaveClass('btn', 'btn--primary')
    await user.click(salvar)
    await waitFor(() => {
      expect(capacidadeSemanalApi.criarCapacidadeSemanal).toHaveBeenCalled()
    })
    vi.useRealTimers()
  })

  it('Histórico monta e navegação de período permanece clicável', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(2026, 9, 27, 12, 0, 0))
    const resumo = {
      periodo: { escopo: 'DIA' as const, inicio: '2026-10-27', fim: '2026-10-27' },
      tarefasFinalizadas: 0,
      blocosConcluidos: 0,
      minutosTrabalhados: 0,
      xpGanho: 0,
    }
    vi.mocked(historicoApi.consultarHistoricoDia).mockResolvedValue(resumo)
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue({
      janela: { inicio: '2026-07-30', fim: '2026-10-27' },
      minimoAmostras: 5,
      geral: { amostras: 0, suficiente: false, razao: null, desvioMedioMinutos: null },
      porTag: [],
      porProjeto: [],
    })
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue({
      ...resumo,
      periodo: { escopo: 'SEMANA', inicio: '2026-10-26', fim: '2026-11-01' },
    })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/historico']}>
          <Routes>
            <Route path="/historico" element={<HistoricoPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.click(await screen.findByRole('radio', { name: 'Dia' }))
    const diaAnterior = await screen.findByRole('button', { name: /dia anterior/i })
    expect(diaAnterior).toHaveClass('btn')
    await user.click(diaAnterior)
    await waitFor(() => {
      expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-25')
    })
    vi.useRealTimers()
  })

  it('Cadastro monta e CTA Criar conta permanece clicável', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <CadastroPage />
      </MemoryRouter>,
    )

    const criar = screen.getByRole('button', { name: /criar conta/i })
    expect(criar).toHaveClass('btn', 'btn--primary')
    await user.click(criar)
    expect((await screen.findAllByRole('alert')).length).toBeGreaterThan(0)
  })

  it('Tags monta e CTA Criar permanece desabilitado com nome vazio', async () => {
    vi.mocked(tagApi.listarTags).mockResolvedValue([])

    render(
      <ToastProvider>
        <MemoryRouter initialEntries={['/tags']}>
          <Routes>
            <Route path="/tags" element={<TagsPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>,
    )

    await screen.findByRole('heading', { name: /tags/i })
    const criar = screen.getByRole('button', { name: /^criar$/i })
    expect(criar).toHaveClass('btn', 'btn--primary')
    expect(criar).toBeDisabled()
    expect(tagApi.criarTag).not.toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent(/nenhuma tag ainda/i)
  })
})
