import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { ConfirmDialog } from './ConfirmDialog'

function Probe({ onConfirm }: { onConfirm: () => void }) {
  const [open, setOpen] = useState(true)
  return (
    <ConfirmDialog
      open={open}
      title="Abortar execução?"
      message="Esta ação não pode ser desfeita na UI."
      confirmLabel="Confirmar"
      cancelLabel="Cancelar"
      onConfirm={() => {
        onConfirm()
        setOpen(false)
      }}
      onCancel={() => setOpen(false)}
    />
  )
}

describe('ConfirmDialog (T2)', () => {
  it('Confirmar chama a ação', async () => {
    const onConfirm = vi.fn()
    const user = userEvent.setup()

    render(<Probe onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: /^confirmar$/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('Cancelar não chama a ação', async () => {
    const onConfirm = vi.fn()
    const user = userEvent.setup()

    render(<Probe onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: /^cancelar$/i }))
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('Esc não chama a ação', async () => {
    const onConfirm = vi.fn()
    const user = userEvent.setup()

    render(<Probe onConfirm={onConfirm} />)

    await user.keyboard('{Escape}')
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('não renderiza quando fechado', () => {
    render(
      <ConfirmDialog
        open={false}
        title="Abortar execução?"
        message="msg"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('mostra título e mensagem', () => {
    render(
      <ConfirmDialog
        open
        title="Abortar execução?"
        message="Esta ação não pode ser desfeita na UI."
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByRole('dialog', { name: /abortar execução/i })).toBeInTheDocument()
    expect(screen.getByText(/não pode ser desfeita/i)).toBeInTheDocument()
  })

  it('usa rótulos customizados', () => {
    render(
      <ConfirmDialog
        open
        title="Apagar?"
        message="msg"
        confirmLabel="Apagar"
        cancelLabel="Voltar"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByRole('button', { name: /^apagar$/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^voltar$/i })).toBeInTheDocument()
  })

  it('Cancelar chama onCancel', async () => {
    const onCancel = vi.fn()
    const user = userEvent.setup()
    render(
      <ConfirmDialog
        open
        title="Abortar execução?"
        message="msg"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    )
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
