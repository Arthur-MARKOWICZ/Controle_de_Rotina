import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import { ToastProvider } from '../../feedback/ToastProvider'
import * as planoApi from '../../planner/api/planoApi'
import * as projetoApi from '../../projeto/api/projetoApi'
import * as reservaApi from '../../projeto/api/reservaProjetoApi'
import * as overrideApi from '../api/capacidadeOverrideApi'
import * as resolvidaApi from '../api/capacidadeResolvidaApi'
import * as capacidadeApi from '../api/capacidadeSemanalApi'
import { CapacidadePage } from './CapacidadePage'

vi.mock('../api/capacidadeSemanalApi', () => ({
  listarCapacidadesSemanais: vi.fn(),
  criarCapacidadeSemanal: vi.fn(),
  atualizarCapacidadeSemanal: vi.fn(),
  excluirCapacidadeSemanal: vi.fn(),
  restaurarCapacidadeSemanal: vi.fn(),
}))

vi.mock('../api/capacidadeOverrideApi', () => ({
  listarCapacidadesOverride: vi.fn(),
  criarCapacidadeOverride: vi.fn(),
  atualizarCapacidadeOverride: vi.fn(),
  excluirCapacidadeOverride: vi.fn(),
  restaurarCapacidadeOverride: vi.fn(),
}))

vi.mock('../api/capacidadeResolvidaApi', () => ({
  consultarCapacidadeResolvida: vi.fn(),
}))

vi.mock('../../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

vi.mock('../../projeto/api/reservaProjetoApi', () => ({
  listarReservas: vi.fn(),
  criarReserva: vi.fn(),
  atualizarReserva: vi.fn(),
}))

vi.mock('../../planner/api/planoApi', () => ({
  buscarPlano: vi.fn(),
}))

const segunda = {
  id: 'c-1',
  diaSemana: 'SEGUNDA' as const,
  horas: 4,
  ativo: true,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

const overrideQuarta = {
  id: 'o-1',
  data: '2026-10-07',
  horas: 1,
  ativo: true,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

const resolvidaSemana = [
  { data: '2026-10-05', horas: 4, origem: 'SEMANAL' as const },
  { data: '2026-10-06', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-07', horas: 1, origem: 'OVERRIDE' as const },
  { data: '2026-10-08', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-09', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-10', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-11', horas: null, origem: 'VAZIO' as const },
]

const resolvidaSemanaSeguinte = [
  { data: '2026-10-12', horas: 4, origem: 'SEMANAL' as const },
  { data: '2026-10-13', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-14', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-15', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-16', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-17', horas: null, origem: 'VAZIO' as const },
  { data: '2026-10-18', horas: null, origem: 'VAZIO' as const },
]

function renderPage() {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/capacidade']}>
        <Routes>
          <Route path="/capacidade" element={<CapacidadePage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function contagensLeituras() {
  return {
    semanais: vi.mocked(capacidadeApi.listarCapacidadesSemanais).mock.calls.length,
    resolvida: vi.mocked(resolvidaApi.consultarCapacidadeResolvida).mock.calls.length,
    overrides: vi.mocked(overrideApi.listarCapacidadesOverride).mock.calls.length,
    reservas: vi.mocked(reservaApi.listarReservas).mock.calls.length,
    projetos: vi.mocked(projetoApi.listarProjetos).mock.calls.length,
  }
}

function horasDoDia(dia: string) {
  return screen.getByRole('spinbutton', { name: new RegExp(`^horas de ${dia}$`, 'i') })
}

async function findHorasDoDia(dia: string) {
  return screen.findByRole('spinbutton', { name: new RegExp(`^horas de ${dia}$`, 'i') })
}

describe('CapacidadePage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(2026, 9, 8, 12, 0, 0))

    vi.mocked(capacidadeApi.listarCapacidadesSemanais).mockReset()
    vi.mocked(capacidadeApi.criarCapacidadeSemanal).mockReset()
    vi.mocked(capacidadeApi.atualizarCapacidadeSemanal).mockReset()
    vi.mocked(capacidadeApi.excluirCapacidadeSemanal).mockReset()
    vi.mocked(capacidadeApi.restaurarCapacidadeSemanal).mockReset()
    vi.mocked(overrideApi.listarCapacidadesOverride).mockReset()
    vi.mocked(overrideApi.criarCapacidadeOverride).mockReset()
    vi.mocked(overrideApi.atualizarCapacidadeOverride).mockReset()
    vi.mocked(overrideApi.excluirCapacidadeOverride).mockReset()
    vi.mocked(overrideApi.restaurarCapacidadeOverride).mockReset()
    vi.mocked(resolvidaApi.consultarCapacidadeResolvida).mockReset()

    vi.mocked(capacidadeApi.listarCapacidadesSemanais).mockResolvedValue([segunda])
    vi.mocked(overrideApi.listarCapacidadesOverride).mockResolvedValue([overrideQuarta])
    vi.mocked(resolvidaApi.consultarCapacidadeResolvida).mockResolvedValue(resolvidaSemana)
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(reservaApi.listarReservas).mockReset()
    vi.mocked(reservaApi.criarReserva).mockReset()
    vi.mocked(reservaApi.atualizarReserva).mockReset()
    vi.mocked(planoApi.buscarPlano).mockReset()
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
    vi.mocked(reservaApi.listarReservas).mockResolvedValue([
      {
        id: 'r-1',
        projetoId: 'proj-tcc',
        diaSemana: 'SEGUNDA',
        minutosReservados: 60,
        createdAt: '2026-01-01T10:00:00Z',
        updatedAt: '2026-01-01T10:00:00Z',
      },
    ])
    vi.mocked(planoApi.buscarPlano).mockResolvedValue(null)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('mostra grade Seg–Dom, barras e total da semana', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: /capacidade/i })).toBeInTheDocument()
    expect(await findHorasDoDia('segunda')).toHaveValue(4)
    expect(screen.getByText('Segunda')).toBeInTheDocument()
    expect(screen.getByText('Terça')).toBeInTheDocument()
    expect(screen.getByText('Domingo')).toBeInTheDocument()

    expect(horasDoDia('terça')).toHaveValue(0)

    expect(screen.getByText(/4h disponíveis/i)).toBeInTheDocument()
    expect(capacidadeApi.listarCapacidadesSemanais).toHaveBeenCalled()
  })

  it('T1: stepper + em segunda soma 1h e mostra barra 1 dia alterado', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    expect(await findHorasDoDia('segunda')).toHaveValue(4)

    await user.click(screen.getByRole('button', { name: /aumentar horas de segunda/i }))
    expect(horasDoDia('segunda')).toHaveValue(5)
    expect(screen.getByText(/1 dia alterado/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /salvar semana/i })).toBeInTheDocument()

    expect(capacidadeApi.atualizarCapacidadeSemanal).not.toHaveBeenCalled()
    expect(capacidadeApi.criarCapacidadeSemanal).not.toHaveBeenCalled()
  })

  it('T2: Descartar volta aos valores salvos e esconde a barra', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    expect(await findHorasDoDia('segunda')).toHaveValue(4)
    await user.click(screen.getByRole('button', { name: /aumentar horas de segunda/i }))
    expect(screen.getByText(/1 dia alterado/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^descartar$/i }))

    expect(horasDoDia('segunda')).toHaveValue(4)
    expect(screen.queryByText(/dia alterado/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /salvar semana/i })).not.toBeInTheDocument()
  })

  it('T3: salvar semana atualiza com resposta e relê só a resolvida', async () => {
    vi.mocked(capacidadeApi.atualizarCapacidadeSemanal).mockResolvedValue({
      ...segunda,
      horas: 5,
    })
    vi.mocked(capacidadeApi.criarCapacidadeSemanal).mockResolvedValue([
      { ...segunda, id: 'c-2', diaSemana: 'TERCA', horas: 1 },
    ])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await findHorasDoDia('segunda')

    await waitFor(() => {
      expect(capacidadeApi.listarCapacidadesSemanais).toHaveBeenCalledTimes(1)
    })
    const antes = contagensLeituras()

    await user.click(screen.getByRole('button', { name: /aumentar horas de segunda/i }))
    await user.click(screen.getByRole('button', { name: /aumentar horas de terça/i }))
    await user.click(screen.getByRole('button', { name: /salvar semana/i }))

    await waitFor(() => {
      expect(capacidadeApi.atualizarCapacidadeSemanal).toHaveBeenCalledWith('c-1', {
        horas: 5,
      })
    })
    await waitFor(() => {
      expect(capacidadeApi.criarCapacidadeSemanal).toHaveBeenCalledWith({
        diaSemana: 'TERCA',
        horas: 1,
      })
    })
    expect(await screen.findByText(/capacidade salva/i)).toBeInTheDocument()

    await waitFor(() => {
      expect(
        vi.mocked(resolvidaApi.consultarCapacidadeResolvida).mock.calls.length,
      ).toBeGreaterThan(antes.resolvida)
    })
    const depois = contagensLeituras()
    expect(depois.semanais).toBe(antes.semanais)
    expect(depois.overrides).toBe(antes.overrides)
    expect(depois.reservas).toBe(antes.reservas)
    expect(depois.projetos).toBe(antes.projetos)
    expect(depois.resolvida).toBe(antes.resolvida + 1)
    expect(screen.queryByText(/dia alterado/i)).not.toBeInTheDocument()
  })

  it('T4: atualizar reserva mostra valor na hora; erro reverte e toast', async () => {
    let resolvePatch!: (value: unknown) => void
    const patchPendente = new Promise((resolve) => {
      resolvePatch = resolve
    })
    vi.mocked(reservaApi.atualizarReserva).mockImplementation(
      () => patchPendente as Promise<reservaApi.ReservaProjetoResponse>,
    )

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    const celulaSegunda = (await screen.findByText('Segunda')).closest('article')
    expect(celulaSegunda).not.toBeNull()
    const input = within(celulaSegunda as HTMLElement).getByLabelText(
      /minutos reservados.*tcc/i,
    )
    fireEvent.change(input, { target: { value: '90' } })
    await user.click(
      within(celulaSegunda as HTMLElement).getByRole('button', {
        name: /salvar reserva.*tcc/i,
      }),
    )

    expect(
      within(celulaSegunda as HTMLElement).getByLabelText(/minutos reservados.*tcc/i),
    ).toHaveValue(90)
    expect(
      within(celulaSegunda as HTMLElement).getByText(/90\s*\/\s*240\s*min/i),
    ).toBeInTheDocument()

    resolvePatch(
      Promise.reject(
        new ApiError(
          409,
          'A soma das reservas do dia excede a capacidade do template semanal ativo.',
        ),
      ),
    )

    await waitFor(() => {
      expect(
        within(celulaSegunda as HTMLElement).getByLabelText(/minutos reservados.*tcc/i),
      ).toHaveValue(60)
    })
    expect(
      screen.getAllByText(/soma das reservas do dia excede a capacidade/i).length,
    ).toBeGreaterThan(0)
  })

  it('T5: remover exceção some na hora; Desfazer chama restaurar', async () => {
    vi.mocked(overrideApi.excluirCapacidadeOverride).mockResolvedValue(undefined)
    vi.mocked(overrideApi.restaurarCapacidadeOverride).mockResolvedValue(overrideQuarta)

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    const celulaQuarta = (await screen.findByText('Quarta')).closest('article') as HTMLElement
    expect(within(celulaQuarta).getByText(/^exceção$/i)).toBeInTheDocument()

    const antesOverrides = vi.mocked(overrideApi.listarCapacidadesOverride).mock.calls.length

    await user.click(screen.getByRole('button', { name: /voltar ao padrão.*quarta/i }))

    await waitFor(() => {
      expect(overrideApi.excluirCapacidadeOverride).toHaveBeenCalledWith('o-1')
    })
    expect(within(celulaQuarta).queryByText(/^exceção$/i)).not.toBeInTheDocument()
    expect(await screen.findByText(/exceção removida/i)).toBeInTheDocument()
    expect(vi.mocked(overrideApi.listarCapacidadesOverride).mock.calls.length).toBe(
      antesOverrides,
    )

    await user.click(screen.getByRole('button', { name: /^desfazer$/i }))

    await waitFor(() => {
      expect(overrideApi.restaurarCapacidadeOverride).toHaveBeenCalledWith('o-1')
    })
    expect(await within(celulaQuarta).findByText(/^exceção$/i)).toBeInTheDocument()
  })

  it('T6: duas trocas rápidas de semana aplicam só a última', async () => {
    let resolveSeguinte!: (value: unknown) => void
    let resolveSeguinte2!: (value: unknown) => void
    const seguinteLenta = new Promise((resolve) => {
      resolveSeguinte = resolve
    })
    const seguinte2 = new Promise((resolve) => {
      resolveSeguinte2 = resolve
    })

    vi.mocked(resolvidaApi.consultarCapacidadeResolvida)
      .mockResolvedValueOnce(resolvidaSemana)
      .mockImplementationOnce(() => seguinteLenta as Promise<typeof resolvidaSemana>)
      .mockImplementationOnce(() => seguinte2 as Promise<typeof resolvidaSemana>)

    vi.mocked(overrideApi.listarCapacidadesOverride)
      .mockResolvedValueOnce([overrideQuarta])
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await findHorasDoDia('segunda')

    await user.click(screen.getByRole('button', { name: /semana seguinte/i }))
    await user.click(screen.getByRole('button', { name: /semana seguinte/i }))

    resolveSeguinte(resolvidaSemanaSeguinte)
    resolveSeguinte2([
      { data: '2026-10-19', horas: 8, origem: 'SEMANAL' as const },
      { data: '2026-10-20', horas: null, origem: 'VAZIO' as const },
      { data: '2026-10-21', horas: null, origem: 'VAZIO' as const },
      { data: '2026-10-22', horas: null, origem: 'VAZIO' as const },
      { data: '2026-10-23', horas: null, origem: 'VAZIO' as const },
      { data: '2026-10-24', horas: null, origem: 'VAZIO' as const },
      { data: '2026-10-25', horas: null, origem: 'VAZIO' as const },
    ])

    await waitFor(() => {
      expect(screen.getByText(/19–25/i)).toBeInTheDocument()
    })
    expect(screen.queryByText(/12–18/i)).not.toBeInTheDocument()
  })

  it('dia com override mostra badge de exceção', async () => {
    renderPage()
    await findHorasDoDia('segunda')

    const celulaQuarta = screen.getByText('Quarta').closest('article')
    expect(celulaQuarta).not.toBeNull()
    expect(within(celulaQuarta as HTMLElement).getByText(/^exceção$/i)).toBeInTheDocument()
    expect(celulaQuarta).toHaveClass('capacidade-celula-override')
  })

  it('aplica preset dias úteis 4h e fim de semana 0h no estado local', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await findHorasDoDia('segunda')

    await user.click(
      screen.getByRole('button', { name: /dias úteis 4h, fim de semana 0h/i }),
    )

    expect(horasDoDia('segunda')).toHaveValue(4)
    expect(horasDoDia('sexta')).toHaveValue(4)
    expect(horasDoDia('sábado')).toHaveValue(0)
    expect(horasDoDia('domingo')).toHaveValue(0)
    expect(screen.getByText(/20h disponíveis/i)).toBeInTheDocument()
    expect(capacidadeApi.criarCapacidadeSemanal).not.toHaveBeenCalled()
  })

  it('copia semana anterior para o rascunho local', async () => {
    vi.mocked(resolvidaApi.consultarCapacidadeResolvida)
      .mockResolvedValueOnce(resolvidaSemana)
      .mockResolvedValueOnce([
        { data: '2026-09-28', horas: 6, origem: 'SEMANAL' as const },
        { data: '2026-09-29', horas: 5, origem: 'SEMANAL' as const },
        { data: '2026-09-30', horas: 4, origem: 'SEMANAL' as const },
        { data: '2026-10-01', horas: 3, origem: 'SEMANAL' as const },
        { data: '2026-10-02', horas: 2, origem: 'SEMANAL' as const },
        { data: '2026-10-03', horas: 0, origem: 'SEMANAL' as const },
        { data: '2026-10-04', horas: 0, origem: 'SEMANAL' as const },
      ])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await findHorasDoDia('segunda')

    await user.click(screen.getByRole('button', { name: /copiar semana anterior/i }))

    await waitFor(() => {
      expect(resolvidaApi.consultarCapacidadeResolvida).toHaveBeenCalledWith({
        dataDe: '2026-09-28',
        dataAte: '2026-10-04',
      })
    })
    expect(horasDoDia('segunda')).toHaveValue(6)
    expect(horasDoDia('terça')).toHaveValue(5)
    expect(horasDoDia('sábado')).toHaveValue(0)
  })

  it('navega para a semana seguinte e consulta resolvida', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await findHorasDoDia('segunda')

    await user.click(screen.getByRole('button', { name: /semana seguinte/i }))

    await waitFor(() => {
      expect(resolvidaApi.consultarCapacidadeResolvida).toHaveBeenLastCalledWith({
        dataDe: '2026-10-12',
        dataAte: '2026-10-18',
      })
    })
  })

  it('grade mostra soma das reservas ≤ horas do template', async () => {
    renderPage()

    const celulaSegunda = (await screen.findByText('Segunda')).closest('article')
    expect(celulaSegunda).not.toBeNull()
    expect(
      within(celulaSegunda as HTMLElement).getByText(/reservas por projeto/i),
    ).toBeInTheDocument()
    expect(
      within(celulaSegunda as HTMLElement).getByText(/60\s*\/\s*240\s*min/i),
    ).toBeInTheDocument()
    expect(within(celulaSegunda as HTMLElement).getByText('TCC')).toBeInTheDocument()
  })

  it('após salvar reserva com proposta pendente mostra CTA Revisar proposta', async () => {
    vi.mocked(reservaApi.atualizarReserva).mockResolvedValue({
      id: 'r-1',
      projetoId: 'proj-tcc',
      diaSemana: 'SEGUNDA',
      minutosReservados: 75,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
    })
    vi.mocked(planoApi.buscarPlano).mockResolvedValue({
      id: 'pl-1',
      data: '2026-10-08',
      versao: 2,
      status: 'RASCUNHO',
      temConflito: false,
      mensagem: null,
      propostaId: 'prop-1',
      propostaPendente: true,
      geradoEm: '2026-10-08T12:00:00Z',
      createdAt: '2026-10-08T12:00:00Z',
      updatedAt: '2026-10-08T12:00:00Z',
      blocos: [],
    })

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    const celulaSegunda = (await screen.findByText('Segunda')).closest('article')
    const input = within(celulaSegunda as HTMLElement).getByLabelText(
      /minutos reservados.*tcc/i,
    )
    fireEvent.change(input, { target: { value: '75' } })
    await user.click(
      within(celulaSegunda as HTMLElement).getByRole('button', {
        name: /salvar reserva.*tcc/i,
      }),
    )

    expect(await screen.findByRole('link', { name: /revisar proposta/i })).toHaveAttribute(
      'href',
      '/',
    )
  })
})
