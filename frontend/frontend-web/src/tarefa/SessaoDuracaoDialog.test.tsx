import { fireEvent, render, screen } from '@testing-library/react'
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

  it('não renderiza quando fechado', () => {
    render(
      <SessaoDuracaoDialog
        open={false}
        restanteMinutos={40}
        tituloAcao="Iniciar sessão"
        onConfirm={vi.fn()}
        onClose={vi.fn()}
      />,
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('Cancelar não confirma', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const onClose = vi.fn()
    render(
      <SessaoDuracaoDialog
        open
        restanteMinutos={40}
        tituloAcao="Iniciar sessão"
        onConfirm={onConfirm}
        onClose={onClose}
      />,
    )
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }))
    expect(onConfirm).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('custom acima do teto mostra erro e não confirma', () => {
    const onConfirm = vi.fn()
    render(
      <SessaoDuracaoDialog
        open
        restanteMinutos={20}
        tituloAcao="Iniciar sessão"
        onConfirm={onConfirm}
        onClose={vi.fn()}
      />,
    )
    fireEvent.change(screen.getByLabelText(/Personalizado/), { target: { value: '45' } })
    fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)
    expect(screen.getByText(/entre 1 e 20/i)).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('custom inválido mostra erro e não confirma', async () => {
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
    await user.type(screen.getByLabelText(/Personalizado/), 'abc')
    await user.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(screen.getByText(/número válido/i)).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('ocupado desabilita presets e confirmação', () => {
    render(
      <SessaoDuracaoDialog
        open
        restanteMinutos={40}
        tituloAcao="Iniciar sessão"
        onConfirm={vi.fn()}
        onClose={vi.fn()}
        ocupado
      />,
    )
    expect(screen.getByRole('button', { name: '15 min' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeDisabled()
  })
})
