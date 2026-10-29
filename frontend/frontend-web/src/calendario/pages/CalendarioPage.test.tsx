import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CalendarioResponse } from '../api/calendarioApi'
import * as calendarioApi from '../api/calendarioApi'
import { CalendarioPage } from './CalendarioPage'

vi.mock('../api/calendarioApi', () => ({
  consultarCalendario: vi.fn(),
}))

const semanaResposta: CalendarioResponse = {
  periodo: { inicio: '2026-10-26', fim: '2026-11-01' },
  dias: [
    {
      data: '2026-10-26',
      capacidadeMinutos: 240,
      origemCapacidade: 'SEMANAL',
      minutosAlocados: 60,
      sobrecarga: false,
      statusPlano: 'APROVADO',
      plano: {
        id: 'p-1',
        versao: 1,
        temConflito: false,
        blocos: [
          {
            id: 'b-1',
            tarefaId: 't-1',
            titulo: 'Estudar Spring',
            duracaoMinutos: 60,
            ordem: 1,
            prioridade: 'ALTA',
          },
        ],
      },
      tarefasComPrazo: [
        {
          id: 't-prazo-1',
          titulo: 'Entregar relatório',
          prazo: '2026-10-26T23:59:59Z',
          status: 'PENDENTE',
          prioridade: 'ALTA',
          duracaoMinutos: 120,
        },
      ],
    },
    ...['2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01'].map(
      (data) => ({
        data,
        capacidadeMinutos: 0,
        origemCapacidade: 'VAZIO' as const,
        minutosAlocados: 0,
        sobrecarga: false,
        statusPlano: null,
        plano: null,
        tarefasComPrazo: [],
      }),
    ),
  ],
}

const mesResposta: CalendarioResponse = {
  periodo: { inicio: '2026-10-01', fim: '2026-10-31' },
  dias: Array.from({ length: 31 }, (_, i) => {
    const dia = String(i + 1).padStart(2, '0')
    const data = `2026-10-${dia}`
    const comPlano = data === '2026-10-26'
    const sobrecarga = data === '2026-10-27'
    return {
      data,
      capacidadeMinutos: sobrecarga ? 60 : 240,
      origemCapacidade: 'SEMANAL' as const,
      minutosAlocados: sobrecarga ? 120 : comPlano ? 60 : 0,
      sobrecarga,
      statusPlano: comPlano || sobrecarga ? ('APROVADO' as const) : null,
      plano:
        comPlano || sobrecarga
          ? {
              id: `p-${dia}`,
              versao: 1,
              temConflito: false,
              blocos: [
                {
                  id: `b-${dia}`,
                  tarefaId: 't-1',
                  titulo: 'Bloco mês',
                  duracaoMinutos: sobrecarga ? 120 : 60,
                  ordem: 1,
                  prioridade: 'MEDIA' as const,
                },
              ],
            }
          : null,
      tarefasComPrazo:
        data === '2026-10-28'
          ? [
              {
                id: 't-m',
                titulo: 'Prazo no mês',
                prazo: '2026-10-28T12:00:00Z',
                status: 'PENDENTE' as const,
                prioridade: 'BAIXA' as const,
                duracaoMinutos: 30,
              },
            ]
          : [],
    }
  }),
}

function renderPage(path = '/calendario') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/calendario" element={<CalendarioPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('CalendarioPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(2026, 9, 28, 12, 0, 0))
    vi.mocked(calendarioApi.consultarCalendario).mockReset()
    vi.mocked(calendarioApi.consultarCalendario).mockResolvedValue(semanaResposta)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('T7: renderiza semana a partir da API com títulos dos blocos', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Calendário' })).toBeInTheDocument()
    expect(calendarioApi.consultarCalendario).toHaveBeenCalledWith('2026-10-26', '2026-11-01')
    expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
    expect(screen.getByText(/60min|1h/i)).toBeInTheDocument()
    expect(screen.getByText('Aprovado')).toBeInTheDocument()
  })

  it('T8: lente Prazos mostra tarefas a vencer; clique no dia abre painel interno', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderPage()

    await screen.findByText('Estudar Spring')

    await user.click(screen.getByRole('radio', { name: 'Prazos' }))
    expect(await screen.findByText('Entregar relatório')).toBeInTheDocument()
    expect(screen.queryByText('Estudar Spring')).not.toBeInTheDocument()

    const celulaSegunda = screen.getByRole('button', { name: /26|segunda/i })
    await user.click(celulaSegunda)

    const painel = await screen.findByRole('region', { name: /detalhe do dia/i })
    const linkTarefa = within(painel).getByRole('link', { name: 'Entregar relatório' })
    expect(linkTarefa).toHaveAttribute('href', '/tarefas/t-prazo-1')
    expect(within(painel).getByText(/capacidade/i)).toBeInTheDocument()
  })

  it('T9: visão mês mostra indicadores leves e navega o período', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    vi.mocked(calendarioApi.consultarCalendario).mockImplementation(async (de, ate) => {
      if (de === '2026-10-01' && ate === '2026-10-31') {
        return mesResposta
      }
      if (de === '2026-11-01') {
        return {
          periodo: { inicio: '2026-11-01', fim: '2026-11-30' },
          dias: Array.from({ length: 30 }, (_, i) => {
            const dia = String(i + 1).padStart(2, '0')
            return {
              data: `2026-11-${dia}`,
              capacidadeMinutos: 0,
              origemCapacidade: 'VAZIO' as const,
              minutosAlocados: 0,
              sobrecarga: false,
              statusPlano: null,
              plano: null,
              tarefasComPrazo: [],
            }
          }),
        }
      }
      return semanaResposta
    })

    renderPage()
    await screen.findByText('Estudar Spring')

    await user.click(screen.getByRole('radio', { name: 'Mês' }))

    expect(calendarioApi.consultarCalendario).toHaveBeenCalledWith('2026-10-01', '2026-10-31')
    expect((await screen.findAllByLabelText(/tem plano/i)).length).toBeGreaterThan(0)
    expect(screen.getByLabelText(/tem prazo/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^sobrecarga$/i)).toBeInTheDocument()
    expect(screen.queryByText('Bloco mês')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /próximo/i }))
    expect(await screen.findByText(/novembro/i)).toBeInTheDocument()
    expect(calendarioApi.consultarCalendario).toHaveBeenCalledWith('2026-11-01', '2026-11-30')
  })
})
