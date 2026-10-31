import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { BlocoAtivo } from './BlocoAtivo'

function iniciadoHa(minutos: number): string {
  return new Date(Date.now() - minutos * 60_000).toISOString()
}

describe('BlocoAtivo (T3)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('destaca bloco em andamento com tempo restante e progresso', () => {
    render(
      <BlocoAtivo
        titulo="Estudar Spring"
        duracaoMinutos={60}
        iniciadoEm={iniciadoHa(28)}
        ocupado={false}
        onConcluir={() => undefined}
        onAbortar={() => undefined}
      />,
    )

    expect(screen.getByRole('region', { name: /agora/i })).toHaveTextContent(/agora\s*estudar spring/i)
    expect(screen.getByLabelText(/tempo restante/i)).toHaveTextContent(/32min/i)

    const bar = screen.getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '47')
  })

  it('dispara Concluir e Abortar', async () => {
    const onConcluir = vi.fn()
    const onAbortar = vi.fn()
    const user = userEvent.setup()

    render(
      <BlocoAtivo
        titulo="Estudar Spring"
        duracaoMinutos={60}
        iniciadoEm={iniciadoHa(28)}
        ocupado={false}
        onConcluir={onConcluir}
        onAbortar={onAbortar}
      />,
    )

    await user.click(screen.getByRole('button', { name: /concluir estudar spring/i }))
    await user.click(screen.getByRole('button', { name: /abortar estudar spring/i }))

    expect(onConcluir).toHaveBeenCalledOnce()
    expect(onAbortar).toHaveBeenCalledOnce()
  })

  it('ícones das ações são decorativos (T5)', () => {
    render(
      <BlocoAtivo
        titulo="Estudar Spring"
        duracaoMinutos={60}
        iniciadoEm={iniciadoHa(28)}
        ocupado={false}
        onConcluir={() => undefined}
        onAbortar={() => undefined}
      />,
    )

    for (const nome of [/concluir estudar spring/i, /abortar estudar spring/i]) {
      const icone = screen.getByRole('button', { name: nome }).querySelector('svg')
      expect(icone).toHaveAttribute('aria-hidden', 'true')
    }
  })

  it('T11: cronômetro local decrementa e mostra +N min ao estourar', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-20T14:58:00.000Z'))

    render(
      <BlocoAtivo
        titulo="Estudar Spring"
        duracaoMinutos={60}
        iniciadoEm="2026-10-20T14:00:00.000Z"
        ocupado={false}
        onConcluir={() => undefined}
        onAbortar={() => undefined}
      />,
    )

    expect(screen.getByLabelText(/tempo restante/i)).toHaveTextContent(/2min/)

    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    expect(screen.getByLabelText(/tempo restante/i)).toHaveTextContent(/1min/)

    act(() => {
      vi.advanceTimersByTime(3 * 60_000)
    })
    expect(screen.queryByLabelText(/tempo restante/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/tempo excedido/i)).toHaveTextContent('+2 min')
  })
})
