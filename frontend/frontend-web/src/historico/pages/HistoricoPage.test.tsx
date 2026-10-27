import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as historicoApi from '../api/historicoApi'
import { HistoricoPage } from './HistoricoPage'

vi.mock('../../auth/session/AuthContext', () => ({
  useAuth: () => ({ logout: vi.fn() }),
}))

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
  xpGanho: 1420,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/historico']}>
      <Routes>
        <Route path="/historico" element={<HistoricoPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('HistoricoPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(2026, 9, 27, 12, 0, 0))
    vi.mocked(historicoApi.consultarHistoricoDia).mockReset()
    vi.mocked(historicoApi.consultarHistoricoSemana).mockReset()
    vi.mocked(historicoApi.consultarHistoricoDia).mockResolvedValue(resumoDia)
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue(resumoSemana)
  })

  it('renderiza Hoje e Esta semana a partir da API', async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Hoje' })).toBeInTheDocument()
    })
    expect(screen.getByRole('heading', { name: 'Esta semana' })).toBeInTheDocument()
    expect(screen.getByText('3h40')).toBeInTheDocument()
    expect(screen.getByText('+280 XP')).toBeInTheDocument()
    expect(screen.getByText('+1420 XP')).toBeInTheDocument()
    expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-27')
    expect(historicoApi.consultarHistoricoSemana).toHaveBeenCalledWith('2026-10-26')
  })

  it('navega dia e semana com query correta', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    await waitFor(() => {
      expect(historicoApi.consultarHistoricoDia).toHaveBeenCalled()
    })

    await user.click(screen.getByRole('button', { name: /dia anterior/i }))
    await waitFor(() => {
      expect(historicoApi.consultarHistoricoDia).toHaveBeenCalledWith('2026-10-26')
    })

    await user.click(screen.getByRole('button', { name: /semana anterior/i }))
    await waitFor(() => {
      expect(historicoApi.consultarHistoricoSemana).toHaveBeenCalledWith('2026-10-19')
    })
  })
})
