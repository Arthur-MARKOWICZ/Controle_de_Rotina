import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { SessaoDuracaoDialog } from './SessaoDuracaoDialog'

describe('SessaoDuracaoDialog', () => {
  it('filtra presets pelo restante e confirma Restante', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(
      <SessaoDuracaoDialog
        open
        restanteMinutos={40}
        tituloAcao="Iniciar sessão"
        onConfirm={onConfirm}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '15 min' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '30 min' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '45 min' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '60 min' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Restante (40)' }))
    expect(onConfirm).toHaveBeenCalledWith(40)
  })

  it('confirma preset e custom dentro do teto', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(
      <SessaoDuracaoDialog
        open
        restanteMinutos={90}
        tituloAcao="Concluir sessão"
        onConfirm={onConfirm}
        onClose={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: '25 min' }))
    expect(onConfirm).toHaveBeenCalledWith(25)

    onConfirm.mockClear()
    await user.type(screen.getByLabelText(/Personalizado/), '35')
    await user.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(onConfirm).toHaveBeenCalledWith(35)
  })
})
