import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { PrazoDialog } from './PrazoDialog'

describe('PrazoDialog', () => {
  it('Aplicar envia o datetime preenchido', async () => {
    const user = userEvent.setup()
    const onApply = vi.fn()
    render(<PrazoDialog open value="" onApply={onApply} onClose={vi.fn()} />)

    await user.type(screen.getByLabelText(/prazo \(opcional\)/i), '2030-06-01T12:00')
    await user.click(screen.getByRole('button', { name: /^aplicar$/i }))

    expect(onApply).toHaveBeenCalledWith('2030-06-01T12:00')
  })

  it('atalho preenche o campo e Aplicar envia o valor do atalho', async () => {
    const user = userEvent.setup()
    const onApply = vi.fn()
    render(<PrazoDialog open value="" onApply={onApply} onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /^amanhã$/i }))
    await user.click(screen.getByRole('button', { name: /^aplicar$/i }))

    expect(onApply).toHaveBeenCalledTimes(1)
    expect(onApply.mock.calls[0][0]).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
  })

  it('Cancelar não aplica o rascunho', async () => {
    const user = userEvent.setup()
    const onApply = vi.fn()
    const onClose = vi.fn()
    render(<PrazoDialog open value="" onApply={onApply} onClose={onClose} />)

    await user.click(screen.getByRole('button', { name: /^hoje$/i }))
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }))

    expect(onApply).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('não renderiza quando fechado', () => {
    render(<PrazoDialog open={false} value="" onApply={vi.fn()} onClose={vi.fn()} />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('Escape fecha sem aplicar', async () => {
    const onApply = vi.fn()
    const onClose = vi.fn()
    render(<PrazoDialog open value="" onApply={onApply} onClose={onClose} />)
    screen.getByRole('dialog').dispatchEvent(new Event('cancel', { bubbles: true }))
    expect(onApply).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('Aplicar com campo vazio envia string vazia', async () => {
    const user = userEvent.setup()
    const onApply = vi.fn()
    render(<PrazoDialog open value="" onApply={onApply} onClose={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: /^aplicar$/i }))
    expect(onApply).toHaveBeenCalledWith('')
  })

  it('disabled impede aplicar o atalho no submit visível', async () => {
    render(<PrazoDialog open value="" disabled onApply={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByRole('button', { name: /^aplicar$/i })).toBeDisabled()
  })
})
