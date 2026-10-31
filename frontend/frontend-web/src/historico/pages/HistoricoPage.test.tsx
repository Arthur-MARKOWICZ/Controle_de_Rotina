import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as historicoApi from '../api/historicoApi'
import { HistoricoPage } from './HistoricoPage'

vi.mock('../api/historicoApi', () => ({
  consultarHistoricoDia: vi.fn(),
  consultarHistoricoSemana: vi.fn(),
}))

const resumoDia = {
  periodo: { escopo: 'DIA' as const, inicio: '2026-10-27', fim: '2026-10-27' },
  tarefasFinalizadas: 4,
  blocosConcluidos: 6,
  minutosTrabalhados: 220,
  xpGanho: 280,
}

const resumoSemana = {
  periodo: { escopo: 'SEMANA' as const, inicio: '2026-10-26', fim: '2026-11-01' },
  tarefasFinalizadas: 18,
  blocosConcluidos: 24,
  minutosTrabalhados: 1270,
  xpGanho: 1120,
}

const resumoSemanaAnterior = {
  periodo: { escopo: 'SEMANA' as const, inicio: '2026-10-19', fim: '2026-10-25' },
  tarefasFinalizadas: 10,
  blocosConcluidos: 20,
  minutosTrabalhados: 1000,
  xpGanho: 1000,
}

const resumoVazio = {
  periodo: { escopo: 'DIA' as const, inicio: '2026-10-20', fim: '2026-10-20' },
  tarefasFinalizadas: 0,
  blocosConcluidos: 0,
  minutosTrabalhados: 0,
  xpGanho: 0,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/historico']}>
      <Routes>
        <Route path="/historico" element={<HistoricoPage />} />
        <Route path="/" element={<div>Home</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('HistoricoPage (T4)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(2026, 9, 27, 12, 0, 0))
    vi.mocked(historicoApi.consultarHistoricoDia).mockReset()
    vi.mocked(historicoApi.consultarHistoricoSemana).mockReset()
    vi.mocked(historicoApi.consultarHistoricoDia).mockImplementation(async (data) => {
      if (data === '2026-10-27') {
        return resumoDia
      }
      return { ...resumoVazio, periodo: { escopo: 'DIA', inicio: data ?? '', fim: data ?? '' }, xpGanho: 40 }
    })
    vi.mocked(historicoApi.consultarHistoricoSemana).mockImplementation(async (semanaInicio) => {
      if (semanaInicio === '2026-10-19') {
        return resumoSemanaAnterior
      }
      return resumoSemana
    })
  })

  it('renderiza Esta semana por padrão e Hoje na visão Dia, a partir da API', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Esta semana' })).toBeInTheDocument()
    expect(screen.getByText('+1120 XP')).toBeInTheDocument()
    expect(historicoApi.consultarHistoricoSemana).toHaveBeenCalledWith('2026-10-26')
    expect(historicoApi.consultarHistoricoSemana).toHaveBeenCalledWith('2026-10-19')

    await user.click(screen.getByRole('radio', { name: 'Dia' }))

    expect(await screen.findByRole('heading', { name: 'Hoje' })).toBeInTheDocument()
    expect(screen.getByText('3h40')).toBeInTheDocument()
    expect(screen.getByText('+280 XP')).toBeInTheDocument()
    expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-27')
    expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-26')
  })

  it('mostra comparação vs semana passada com seta e sinal nas métricas da semana', async () => {
    renderPage()

    // XP: 1120 vs 1000 = +12%
    const variacao = await screen.findByText('+12% vs semana passada')
    expect(variacao).toHaveClass('historico-variacao--alta')
    expect(variacao.querySelector('svg')).not.toBeNull()
  })

  it('T3: variação negativa mostra seta para baixo e texto com sinal', async () => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockImplementation(async (semanaInicio) => {
      if (semanaInicio === '2026-10-19') {
        return resumoSemanaAnterior
      }
      return { ...resumoSemana, xpGanho: 800 }
    })
    renderPage()

    const variacao = await screen.findByText('-20% vs semana passada')
    expect(variacao).toHaveClass('historico-variacao--queda')
    expect(variacao).not.toHaveClass('historico-variacao--alta')
    expect(variacao.querySelector('svg')).not.toBeNull()
  })

  it('navega dia e semana com query correta', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    await screen.findByRole('heading', { name: 'Esta semana' })
    await user.click(screen.getByRole('button', { name: /semana anterior/i }))
    await waitFor(() => {
      expect(historicoApi.consultarHistoricoSemana).toHaveBeenCalledWith('2026-10-12')
    })
    expect(await screen.findByRole('heading', { name: 'Semana passada' })).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: 'Dia' }))
    await screen.findByRole('heading', { name: 'Hoje' })
    expect(screen.getByRole('button', { name: /próximo dia/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /dia anterior/i }))
    await waitFor(() => {
      expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-25')
    })
    expect(await screen.findByRole('heading', { name: 'Ontem' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Hoje' }))
    expect(await screen.findByRole('heading', { name: 'Hoje' })).toBeInTheDocument()
  })

  it('exibe gráfico com alternativa textual e empty motivacional', async () => {
    vi.mocked(historicoApi.consultarHistoricoDia).mockResolvedValue(resumoVazio)
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue({
      ...resumoVazio,
      periodo: { escopo: 'SEMANA', inicio: '2026-10-26', fim: '2026-11-01' },
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('table', { name: /xp por dia da semana/i })).toBeInTheDocument()
    })

    const empties = screen.getAllByText(/ainda sem atividade neste período/i)
    expect(empties.length).toBeGreaterThanOrEqual(1)
    const link = within(empties[0].closest('p') as HTMLElement).getByRole('link', {
      name: /ver o plano de hoje/i,
    })
    expect(link).toHaveAttribute('href', '/')
  })
})
