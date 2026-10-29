import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from './auth/api/meApi'
import { LoginPage } from './auth/pages/LoginPage'
import { AuthProvider } from './auth/session/AuthContext'
import { clearAccessToken } from './auth/session/tokenStore'
import * as capacidadeOverrideApi from './capacidade/api/capacidadeOverrideApi'
import * as capacidadeResolvidaApi from './capacidade/api/capacidadeResolvidaApi'
import * as capacidadeSemanalApi from './capacidade/api/capacidadeSemanalApi'
import { CapacidadePage } from './capacidade/pages/CapacidadePage'
import { ToastProvider } from './feedback/ToastProvider'
import * as historicoApi from './historico/api/historicoApi'
import { HistoricoPage } from './historico/pages/HistoricoPage'
import { HomePage } from './pages/HomePage'
import * as planoApi from './planner/api/planoApi'
import * as tarefaApi from './tarefa/api/tarefaApi'
import { TarefasPage } from './tarefa/pages/TarefasPage'
import { XpProgressProvider } from './xp/XpProgressContext'

vi.mock('./auth/api/authApi', () => ({
  login: vi.fn(),
  logout: vi.fn(),
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
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
}))

vi.mock('./execucao/api/execucaoApi', () => ({
  listarExecucoes: vi.fn(),
  iniciarExecucao: vi.fn(),
  concluirExecucao: vi.fn(),
  abortarExecucao: vi.fn(),
}))

vi.mock('./historico/api/historicoApi', () => ({
  consultarHistoricoDia: vi.fn(),
  consultarHistoricoSemana: vi.fn(),
}))

vi.mock('./auth/api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
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
    expect(await screen.findByRole('alert')).toBeInTheDocument()
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
            <MemoryRouter>
              <HomePage />
            </MemoryRouter>
          </XpProgressProvider>
        </ToastProvider>
      </AuthProvider>,
    )

    const gerar = await screen.findByRole('button', { name: /^gerar plano$/i })
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
        <MemoryRouter initialEntries={['/tarefas']}>
          <Routes>
            <Route path="/tarefas" element={<TarefasPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>,
    )

    const nova = await screen.findByRole('button', { name: /nova tarefa/i })
    expect(nova).toHaveClass('btn', 'btn--primary')

    await user.click(screen.getByRole('button', { name: /^filtros$/i }))
    const aplicar = screen.getByRole('button', { name: /aplicar filtros/i })
    expect(aplicar).toHaveClass('btn', 'btn--primary')
    await user.click(aplicar)
    await waitFor(() => {
      expect(vi.mocked(tarefaApi.listarTarefas).mock.calls.length).toBeGreaterThan(0)
    })
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
    await user.click(screen.getByRole('button', { name: /aumentar segunda/i }))
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

    const diaAnterior = await screen.findByRole('button', { name: /dia anterior/i })
    expect(diaAnterior).toHaveClass('btn')
    await user.click(diaAnterior)
    await waitFor(() => {
      expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-26')
    })
    vi.useRealTimers()
  })
})
