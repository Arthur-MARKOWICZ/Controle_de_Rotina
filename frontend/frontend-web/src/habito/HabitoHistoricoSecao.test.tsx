import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as habitoApi from './api/habitoApi'
import type { HabitoHistoricoDia } from './api/habitoApi'
import { HabitoHistoricoSecao } from './HabitoHistoricoSecao'

vi.mock('./api/habitoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/habitoApi')>()
  return {
    ...actual,
    buscarHistoricoHabito: vi.fn(),
  }
})

function dia(parcial: Partial<HabitoHistoricoDia>): HabitoHistoricoDia {
  return {
    data: '2026-11-10',
    minutosDedicados: 30,
    metaMinutos: 30,
    suficiente: true,
    origem: 'INFORMADO',
    ...parcial,
  }
}

describe('HabitoHistoricoSecao', () => {
  beforeEach(() => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockReset()
  })

  it('lista os dias com minutos/meta e sinaliza meta atingida e falta', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockResolvedValue([
      dia({ data: '2026-11-10' }),
      dia({ data: '2026-11-03', minutosDedicados: 25, suficiente: false, origem: 'TIMER' }),
    ])

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    const itens = await screen.findAllByRole('listitem')
    expect(itens).toHaveLength(2)
    expect(within(itens[0]).getByText('10/11/2026', { exact: false })).toBeInTheDocument()
    expect(within(itens[0]).getByText('30 / 30 min')).toBeInTheDocument()
    expect(within(itens[0]).getByText('Meta atingida')).toBeInTheDocument()
    expect(within(itens[1]).getByText('25 / 30 min')).toBeInTheDocument()
    expect(within(itens[1]).getByText('Faltaram 5 min')).toBeInTheDocument()
    expect(habitoApi.buscarHistoricoHabito).toHaveBeenCalledWith('h-1')
  })

  it('concluir direto antigo sem tempo mostra "Tempo não registrado"', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockResolvedValue([
      dia({ minutosDedicados: 0, suficiente: false, origem: 'TIMER' }),
    ])

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    expect(await screen.findByText('Tempo não registrado')).toBeInTheDocument()
    expect(screen.queryByText(/faltaram/i)).not.toBeInTheDocument()
    expect(screen.queryByText('0 / 30 min')).not.toBeInTheDocument()
  })

  it('mostra estado vazio quando não há cumprimentos', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockResolvedValue([])

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    expect(await screen.findByText('Nenhum cumprimento nos últimos 84 dias.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('mostra carregando antes da resposta', () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockReturnValue(new Promise(() => {}))

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    expect(screen.getByRole('status')).toHaveTextContent('Carregando histórico…')
  })

  it('500 mostra erro e "Tentar de novo" recarrega', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito)
      .mockRejectedValueOnce(new ApiError(500, 'Erro interno'))
      .mockResolvedValueOnce([dia({})])
    const user = userEvent.setup()

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    expect(await screen.findByText(/não foi possível carregar o histórico/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByText('Meta atingida')).toBeInTheDocument()
    expect(habitoApi.buscarHistoricoHabito).toHaveBeenCalledTimes(2)
  })

  it('404 mostra a mensagem do servidor', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockRejectedValue(
      new ApiError(404, 'Hábito não encontrado'),
    )

    render(<HabitoHistoricoSecao habitoId="h-x" />)

    expect(await screen.findByText('Hábito não encontrado')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('HTML do proxy no lugar de JSON mostra erro sem quebrar', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockRejectedValue(
      new ApiError(200, 'A API devolveu HTML da SPA em vez de JSON.'),
    )

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    expect(await screen.findByText(/não foi possível carregar o histórico/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
  })

  it('erro não-Error usa a mensagem padrão sem duplicar', async () => {
    vi.mocked(habitoApi.buscarHistoricoHabito).mockRejectedValue('falhou')

    render(<HabitoHistoricoSecao habitoId="h-1" />)

    await waitFor(() => {
      expect(screen.getByText('Não foi possível carregar o histórico.')).toBeInTheDocument()
    })
  })

  it('ignora resposta que chega depois de trocar de hábito', async () => {
    let resolverAntigo: (dias: HabitoHistoricoDia[]) => void = () => {}
    vi.mocked(habitoApi.buscarHistoricoHabito)
      .mockReturnValueOnce(new Promise((resolve) => { resolverAntigo = resolve }))
      .mockResolvedValueOnce([])

    const { rerender } = render(<HabitoHistoricoSecao habitoId="h-1" />)
    rerender(<HabitoHistoricoSecao habitoId="h-2" />)
    expect(await screen.findByText('Nenhum cumprimento nos últimos 84 dias.')).toBeInTheDocument()

    resolverAntigo([dia({})])

    await waitFor(() => {
      expect(screen.queryByText('Meta atingida')).not.toBeInTheDocument()
    })
  })
})
