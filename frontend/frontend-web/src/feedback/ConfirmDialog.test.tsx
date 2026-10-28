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
})
