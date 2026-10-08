import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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
    <MemoryRouter initialEntries={['/capacidade']}>
      <Routes>
        <Route path="/capacidade" element={<CapacidadePage />} />
      </Routes>
    </MemoryRouter>,
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
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('mostra grade Seg–Dom e carrega horas existentes', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: /capacidade/i })).toBeInTheDocument()
    expect(screen.getByText('Segunda')).toBeInTheDocument()
    expect(screen.getByText('Terça')).toBeInTheDocument()
    expect(screen.getByText('Domingo')).toBeInTheDocument()

    const inputSegunda = screen.getByLabelText(/horas de segunda/i)
    expect(inputSegunda).toHaveValue(4)

    const inputTerca = screen.getByLabelText(/horas de terça/i)
    expect(inputTerca).toHaveValue(null)

    expect(capacidadeApi.listarCapacidadesSemanais).toHaveBeenCalled()
  })

  it('mostra horas efetivas da semana via capacidade resolvida', async () => {
    renderPage()

    await screen.findByLabelText(/horas de segunda/i)

    expect(resolvidaApi.consultarCapacidadeResolvida).toHaveBeenCalledWith({
      dataDe: '2026-10-05',
      dataAte: '2026-10-11',
    })

    const celulaSegunda = screen.getByText('Segunda').closest('article')
    expect(celulaSegunda).not.toBeNull()
    expect(within(celulaSegunda as HTMLElement).getByText(/efetivas:\s*4h/i)).toBeInTheDocument()
    expect(within(celulaSegunda as HTMLElement).getByText(/semanal/i)).toBeInTheDocument()

    const celulaQuarta = screen.getByText('Quarta').closest('article')
    expect(celulaQuarta).not.toBeNull()
    expect(within(celulaQuarta as HTMLElement).getByText(/efetivas:\s*1h/i)).toBeInTheDocument()
    expect(within(celulaQuarta as HTMLElement).getByText(/override/i)).toBeInTheDocument()
  })

  it('destaca célula com override na semana', async () => {
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    const celulaQuarta = screen.getByText('Quarta').closest('article')
    expect(celulaQuarta).toHaveClass('capacidade-celula-override')
  })

  it('cria horas em dia vazio', async () => {
    vi.mocked(capacidadeApi.criarCapacidadeSemanal).mockResolvedValue([
      { ...segunda, id: 'c-2', diaSemana: 'TERCA', horas: 3 },
    ])
    vi.mocked(capacidadeApi.listarCapacidadesSemanais)
      .mockResolvedValueOnce([segunda])
      .mockResolvedValueOnce([
        segunda,
        { ...segunda, id: 'c-2', diaSemana: 'TERCA', horas: 3 },
      ])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    const inputTerca = screen.getByLabelText(/horas de terça/i)
    await user.clear(inputTerca)
    await user.type(inputTerca, '3')
    await user.click(screen.getByRole('button', { name: /salvar terça/i }))

    await waitFor(() => {
      expect(capacidadeApi.criarCapacidadeSemanal).toHaveBeenCalledWith({
        diaSemana: 'TERCA',
        horas: 3,
      })
    })
  })

  it('edita horas de dia existente', async () => {
    vi.mocked(capacidadeApi.atualizarCapacidadeSemanal).mockResolvedValue({
      ...segunda,
      horas: 6,
    })
    vi.mocked(capacidadeApi.listarCapacidadesSemanais)
      .mockResolvedValueOnce([segunda])
      .mockResolvedValueOnce([{ ...segunda, horas: 6 }])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    const inputSegunda = await screen.findByLabelText(/horas de segunda/i)

    await user.clear(inputSegunda)
    await user.type(inputSegunda, '6')
    await user.click(screen.getByRole('button', { name: /salvar segunda/i }))

    await waitFor(() => {
      expect(capacidadeApi.atualizarCapacidadeSemanal).toHaveBeenCalledWith('c-1', {
        horas: 6,
      })
    })
  })

  it('exclui capacidade da grade e recarrega', async () => {
    vi.mocked(capacidadeApi.excluirCapacidadeSemanal).mockResolvedValue(undefined)
    vi.mocked(capacidadeApi.listarCapacidadesSemanais)
      .mockResolvedValueOnce([segunda])
      .mockResolvedValueOnce([])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    await user.click(screen.getByRole('button', { name: /excluir segunda/i }))

    await waitFor(() => {
      expect(capacidadeApi.excluirCapacidadeSemanal).toHaveBeenCalledWith('c-1')
    })
    await waitFor(() => {
      expect(capacidadeApi.listarCapacidadesSemanais).toHaveBeenCalledTimes(2)
    })
  })

  it('filtra somente excluídas e restaura', async () => {
    const deletada = { ...segunda, ativo: false }
    vi.mocked(capacidadeApi.listarCapacidadesSemanais)
      .mockResolvedValueOnce([segunda])
      .mockResolvedValueOnce([deletada])
      .mockResolvedValueOnce([segunda])
    vi.mocked(capacidadeApi.restaurarCapacidadeSemanal).mockResolvedValue(segunda)

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    await user.selectOptions(screen.getByLabelText(/^excluídas$/i), 'somente')
    await user.click(screen.getByRole('button', { name: /^aplicar filtro$/i }))

    await waitFor(() => {
      expect(capacidadeApi.listarCapacidadesSemanais).toHaveBeenLastCalledWith(
        expect.objectContaining({ somenteDeletadas: true }),
      )
    })

    expect(await screen.findByText('Excluída')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /restaurar segunda/i }))

    await waitFor(() => {
      expect(capacidadeApi.restaurarCapacidadeSemanal).toHaveBeenCalledWith('c-1')
    })
  })

  it('cria override por data e recarrega lista/resolvida', async () => {
    vi.mocked(overrideApi.criarCapacidadeOverride).mockResolvedValue([
      { ...overrideQuarta, id: 'o-2', data: '2026-10-09', horas: 2 },
    ])
    vi.mocked(overrideApi.listarCapacidadesOverride)
      .mockResolvedValueOnce([overrideQuarta])
      .mockResolvedValueOnce([
        overrideQuarta,
        { ...overrideQuarta, id: 'o-2', data: '2026-10-09', horas: 2 },
      ])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByLabelText(/horas de segunda/i)

    await user.type(screen.getByLabelText(/data do override/i), '2026-10-09')
    await user.type(screen.getByLabelText(/horas do override/i), '2')
    await user.click(screen.getByRole('button', { name: /salvar override/i }))

    await waitFor(() => {
      expect(overrideApi.criarCapacidadeOverride).toHaveBeenCalledWith({
        data: '2026-10-09',
        horas: 2,
      })
    })
    await waitFor(() => {
      expect(resolvidaApi.consultarCapacidadeResolvida).toHaveBeenCalledTimes(2)
    })
  })

  it('lista overrides próximos e exclui', async () => {
    vi.mocked(overrideApi.excluirCapacidadeOverride).mockResolvedValue(undefined)
    vi.mocked(overrideApi.listarCapacidadesOverride)
      .mockResolvedValueOnce([overrideQuarta])
      .mockResolvedValueOnce([])

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    const excluir = await screen.findByRole('button', {
      name: /excluir override 07\/10\/2026/i,
    })
    expect(screen.getByRole('heading', { name: /overrides por data/i })).toBeInTheDocument()

    await user.click(excluir)

    await waitFor(() => {
      expect(overrideApi.excluirCapacidadeOverride).toHaveBeenCalledWith('o-1')
    })
  })

  it('filtra overrides excluídos e restaura', async () => {
    const deletado = { ...overrideQuarta, ativo: false }
    vi.mocked(overrideApi.listarCapacidadesOverride)
      .mockResolvedValueOnce([overrideQuarta])
      .mockResolvedValueOnce([deletado])
      .mockResolvedValueOnce([overrideQuarta])
    vi.mocked(overrideApi.restaurarCapacidadeOverride).mockResolvedValue(overrideQuarta)

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()
    await screen.findByRole('button', { name: /excluir override 07\/10\/2026/i })

    await user.selectOptions(screen.getByLabelText(/overrides excluídos/i), 'somente')
    await user.click(screen.getByRole('button', { name: /aplicar filtro de overrides/i }))

    await waitFor(() => {
      expect(overrideApi.listarCapacidadesOverride).toHaveBeenLastCalledWith(
        expect.objectContaining({ somenteDeletadas: true }),
      )
    })

    await user.click(
      await screen.findByRole('button', { name: /restaurar override 07\/10\/2026/i }),
    )

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
})
