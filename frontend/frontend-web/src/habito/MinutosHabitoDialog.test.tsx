import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { MinutosHabitoDialog } from './MinutosHabitoDialog'

function renderDialog(props: Partial<Parameters<typeof MinutosHabitoDialog>[0]> = {}) {
  const onConfirm = vi.fn()
  const onClose = vi.fn()
  render(
    <MinutosHabitoDialog
      open
      tituloHabito="Correr"
      duracaoMinutos={30}
      onConfirm={onConfirm}
      onClose={onClose}
      {...props}
    />,
  )
  return { onConfirm, onClose }
}

describe('MinutosHabitoDialog', () => {
  it('confirma a duração do hábito sem alterar o campo', async () => {
    const user = userEvent.setup()
    const { onConfirm } = renderDialog()

    expect(screen.getByLabelText('Minutos dedicados')).toHaveValue(30)
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(onConfirm).toHaveBeenCalledWith(30)
  })

  it('confirma outro valor informado', async () => {
    const user = userEvent.setup()
    const { onConfirm } = renderDialog()

    await user.clear(screen.getByLabelText('Minutos dedicados'))
    await user.type(screen.getByLabelText('Minutos dedicados'), '20')
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(onConfirm).toHaveBeenCalledWith(20)
  })

  it('Enter no campo confirma', async () => {
    const user = userEvent.setup()
    const { onConfirm } = renderDialog({ duracaoMinutos: 45 })

    await user.type(screen.getByLabelText('Minutos dedicados'), '{Enter}')

    expect(onConfirm).toHaveBeenCalledWith(45)
  })

  it('cancelar fecha sem confirmar', async () => {
    const user = userEvent.setup()
    const { onConfirm, onClose } = renderDialog()

    await user.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(onClose).toHaveBeenCalled()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it.each([['0'], ['1441'], ['']])('mostra erro e não confirma com "%s"', async (digitado) => {
    const user = userEvent.setup()
    const { onConfirm } = renderDialog()

    await user.clear(screen.getByLabelText('Minutos dedicados'))
    if (digitado) {
      await user.type(screen.getByLabelText('Minutos dedicados'), digitado)
    }
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Informe entre 1 e 1440 minutos.')
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('não confirma número fracionado', async () => {
    const user = userEvent.setup()
    const { onConfirm } = renderDialog()

    await user.clear(screen.getByLabelText('Minutos dedicados'))
    await user.type(screen.getByLabelText('Minutos dedicados'), '12.5')
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('desabilita campo e botões quando ocupado', () => {
    renderDialog({ ocupado: true })

    expect(screen.getByLabelText('Minutos dedicados')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Concluir' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
  })

  it('não renderiza quando fechado', () => {
    renderDialog({ open: false })

    expect(screen.queryByLabelText('Minutos dedicados')).not.toBeInTheDocument()
  })
})
