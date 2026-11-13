import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as historicoApi from '../historico/api/historicoApi'
import { PassoSemana } from './PassoSemana'

vi.mock('../historico/api/historicoApi', () => ({
  consultarHistoricoSemana: vi.fn(),
}))

const RESUMO = {
  periodo: { escopo: 'SEMANA' as const, inicio: '2026-11-09', fim: '2026-11-15' },
  tarefasFinalizadas: 3,
  blocosConcluidos: 5,
  minutosTrabalhados: 150,
  xpGanho: 80,
}

describe('PassoSemana', () => {
  beforeEach(() => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockReset()
  })

  it('mostra os números da semana corrente', async () => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue(RESUMO)

    render(<PassoSemana />)

    expect(await screen.findByTestId('semana-tarefas')).toHaveTextContent('3')
    expect(screen.getByText('2h30')).toBeInTheDocument()
    expect(screen.getByText('80')).toBeInTheDocument()
  })

  it('pede a semana corrente sem semanaInicio (o backend resolve em BRT)', async () => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue(RESUMO)

    render(<PassoSemana />)

    await screen.findByTestId('semana-tarefas')
    expect(historicoApi.consultarHistoricoSemana).toHaveBeenCalledWith()
  })

  it('mostra estado vazio quando não houve atividade', async () => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue({
      ...RESUMO,
      tarefasFinalizadas: 0,
      blocosConcluidos: 0,
      minutosTrabalhados: 0,
      xpGanho: 0,
    })

    render(<PassoSemana />)

    expect(await screen.findByText(/nenhuma atividade/i)).toHaveAttribute('role', 'status')
    expect(screen.queryByTestId('semana-tarefas')).not.toBeInTheDocument()
  })

  it('mostra erro do servidor e tenta de novo', async () => {
    const user = userEvent.setup()
    vi.mocked(historicoApi.consultarHistoricoSemana)
      .mockRejectedValueOnce(new ApiError(500, 'Erro interno no histórico.'))
      .mockResolvedValueOnce(RESUMO)

    render(<PassoSemana />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Erro interno no histórico.')
    await user.click(screen.getByRole('button', { name: /tentar de novo/i }))
    expect(await screen.findByTestId('semana-tarefas')).toHaveTextContent('3')
  })

  it('usa texto padrão quando a resposta é HTML do proxy', async () => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockRejectedValue(
      new SyntaxError('Unexpected token \'<\''),
    )

    render(<PassoSemana />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar o resumo da semana.',
    )
  })

  it('mostra 401 sem sessão como erro', async () => {
    vi.mocked(historicoApi.consultarHistoricoSemana).mockRejectedValue(
      new ApiError(401, 'Não autenticado.'),
    )

    render(<PassoSemana />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Não autenticado.')
  })

  it('não quebra se desmontar antes da resposta', async () => {
    let resolver: (v: typeof RESUMO) => void = () => {}
    vi.mocked(historicoApi.consultarHistoricoSemana).mockReturnValue(
      new Promise((resolve) => {
        resolver = resolve
      }),
    )

    const { unmount } = render(<PassoSemana />)
    unmount()
    resolver(RESUMO)

    expect(screen.queryByTestId('semana-tarefas')).not.toBeInTheDocument()
  })
})
