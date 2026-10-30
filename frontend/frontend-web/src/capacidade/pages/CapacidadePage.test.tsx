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
    expect(screen.getByText('Segunda')).toBeInTheDocument()
    expect(screen.getByText('Terça')).toBeInTheDocument()
    expect(screen.getByText('Domingo')).toBeInTheDocument()

    expect(screen.getByLabelText(/horas de segunda/i)).toHaveTextContent('4h')
    expect(screen.getByLabelText(/horas de terça/i)).toHaveTextContent('0h')

    expect(screen.getByText(/4h disponíveis/i)).toBeInTheDocument()
    expect(capacidadeApi.listarCapacidadesSemanais).toHaveBeenCalled()
  })

  it('T1: stepper altera valor do dia no estado local', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    expect(await screen.findByLabelText(/horas de segunda/i)).toHaveTextContent('4h')

    await user.click(screen.getByRole('button', { name: /aumentar segunda/i }))
    expect(screen.getByLabelText(/horas de segunda/i)).toHaveTextContent('5h')
    expect(screen.getByText(/5h disponíveis/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /diminuir segunda/i }))
    expect(screen.getByLabelText(/horas de segunda/i)).toHaveTextContent('4h')

    expect(capacidadeApi.atualizarCapacidadeSemanal).not.toHaveBeenCalled()
    expect(capacidadeApi.criarCapacidadeSemanal).not.toHaveBeenCalled()
  })

  it('T2: Salvar semana dispara persistência dos dias alterados', async () => {
    vi.mocked(capacidadeApi.atualizarCapacidadeSemanal).mockResolvedValue({
      ...segunda,
      horas: 5,
    })
    vi.mocked(capacidadeApi.criarCapacidadeSemanal).mockResolvedValue([
      { ...segunda, id: 'c-2', diaSemana: 'TERCA', horas: 1 },
    ])
    vi.mocked(capacidadeApi.listarCapacidadesSemanais)
      .mockResolvedValueOnce([segunda])
      .mockResolvedValueOnce([
        { ...segunda, horas: 5 },
        { ...segunda, id: 'c-2', diaSemana: 'TERCA', horas: 1 },
      ])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    await user.click(screen.getByRole('button', { name: /aumentar segunda/i }))
    await user.click(screen.getByRole('button', { name: /aumentar terça/i }))
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
    expect(capacidadeApi.atualizarCapacidadeSemanal).toHaveBeenCalledTimes(1)
    expect(capacidadeApi.criarCapacidadeSemanal).toHaveBeenCalledTimes(1)
  })

  it('T3: dia com override mostra badge de exceção', async () => {
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    const celulaQuarta = screen.getByText('Quarta').closest('article')
    expect(celulaQuarta).not.toBeNull()
    expect(within(celulaQuarta as HTMLElement).getByText(/exceção/i)).toBeInTheDocument()
    expect(celulaQuarta).toHaveClass('capacidade-celula-override')
  })

  it('aplica preset dias úteis 4h e fim de semana 0h no estado local', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    await user.click(
      screen.getByRole('button', { name: /dias úteis 4h, fim de semana 0h/i }),
    )

    expect(screen.getByLabelText(/horas de segunda/i)).toHaveTextContent('4h')
    expect(screen.getByLabelText(/horas de sexta/i)).toHaveTextContent('4h')
    expect(screen.getByLabelText(/horas de sábado/i)).toHaveTextContent('0h')
    expect(screen.getByLabelText(/horas de domingo/i)).toHaveTextContent('0h')
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
    await screen.findByLabelText(/horas de segunda/i)

    await user.click(screen.getByRole('button', { name: /copiar semana anterior/i }))

    await waitFor(() => {
      expect(resolvidaApi.consultarCapacidadeResolvida).toHaveBeenCalledWith({
        dataDe: '2026-09-28',
        dataAte: '2026-10-04',
      })
    })
    expect(screen.getByLabelText(/horas de segunda/i)).toHaveTextContent('6h')
    expect(screen.getByLabelText(/horas de terça/i)).toHaveTextContent('5h')
    expect(screen.getByLabelText(/horas de sábado/i)).toHaveTextContent('0h')
  })

  it('volta ao padrão com toast de desfazer que restaura o override', async () => {
    vi.mocked(overrideApi.excluirCapacidadeOverride).mockResolvedValue(undefined)
    vi.mocked(overrideApi.restaurarCapacidadeOverride).mockResolvedValue(overrideQuarta)
    vi.mocked(overrideApi.listarCapacidadesOverride)
      .mockResolvedValueOnce([overrideQuarta])
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([overrideQuarta])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByText(/exceção/i)

    await user.click(screen.getByRole('button', { name: /voltar ao padrão.*quarta/i }))

    await waitFor(() => {
      expect(overrideApi.excluirCapacidadeOverride).toHaveBeenCalledWith('o-1')
    })
    expect(await screen.findByText(/exceção removida/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^desfazer$/i }))

    await waitFor(() => {
      expect(overrideApi.restaurarCapacidadeOverride).toHaveBeenCalledWith('o-1')
    })
  })

  it('navega para a semana seguinte e consulta resolvida', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    await user.click(screen.getByRole('button', { name: /semana seguinte/i }))

    await waitFor(() => {
      expect(resolvidaApi.consultarCapacidadeResolvida).toHaveBeenLastCalledWith({
        dataDe: '2026-10-12',
        dataAte: '2026-10-18',
      })
    })
  })

  it('T4: grade mostra soma das reservas ≤ horas do template', async () => {
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

  it('T4: simula 409 ao salvar reserva e mostra erro', async () => {
    vi.mocked(reservaApi.atualizarReserva).mockRejectedValue(
      new ApiError(
        409,
        'A soma das reservas do dia excede a capacidade do template semanal ativo.',
      ),
    )

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    const celulaSegunda = (await screen.findByText('Segunda')).closest('article')
    expect(celulaSegunda).not.toBeNull()
    const input = within(celulaSegunda as HTMLElement).getByLabelText(
      /minutos reservados.*tcc/i,
    )
    fireEvent.change(input, { target: { value: '300' } })
    await user.click(
      within(celulaSegunda as HTMLElement).getByRole('button', {
        name: /salvar reserva.*tcc/i,
      }),
    )

    await waitFor(() => {
      expect(reservaApi.atualizarReserva).toHaveBeenCalledWith('proj-tcc', 'r-1', {
        minutosReservados: 300,
      })
    })
    expect(
      screen.getAllByText(/soma das reservas do dia excede a capacidade/i).length,
    ).toBeGreaterThan(0)
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
