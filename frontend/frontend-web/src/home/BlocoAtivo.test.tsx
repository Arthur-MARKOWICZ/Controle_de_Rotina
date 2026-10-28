import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { BlocoAtivo } from './BlocoAtivo'

describe('BlocoAtivo (T3)', () => {
  it('destaca bloco em andamento com tempo restante e progresso', () => {
    render(
      <BlocoAtivo
        titulo="Estudar Spring"
        duracaoMinutos={60}
        minutosRestantes={32}
        ocupado={false}
        onConcluir={() => undefined}
        onAbortar={() => undefined}
      />,
    )

    expect(screen.getByRole('region', { name: /agora/i })).toHaveTextContent(
      /agora:\s*estudar spring/i,
    )
    expect(screen.getByLabelText(/tempo restante/i)).toHaveTextContent(/restante:\s*32min/i)

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
        minutosRestantes={32}
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
})
