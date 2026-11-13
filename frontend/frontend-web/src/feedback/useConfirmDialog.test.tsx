import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { useConfirmDialog } from './useConfirmDialog'

function Probe() {
  const { confirm, dialog } = useConfirmDialog()
  return (
    <>
      {dialog}
      <button
        type="button"
        onClick={() => {
          void confirm({ title: 'Excluir?', message: 'Não dá para desfazer.', confirmLabel: 'Excluir' }).then(
            (ok) => {
              document.body.dataset.resultado = String(ok)
            },
          )
        }}
      >
        Pedir
      </button>
    </>
  )
}

describe('useConfirmDialog', () => {
  it('não renderiza diálogo antes de pedir confirmação', () => {
    render(<Probe />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('mostra título e mensagem ao pedir confirmação', async () => {
    const user = userEvent.setup()
    render(<Probe />)
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Excluir?')).toBeInTheDocument()
    expect(screen.getByText('Não dá para desfazer.')).toBeInTheDocument()
  })

  it('resolve true ao confirmar', async () => {
    const user = userEvent.setup()
    render(<Probe />)
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    await user.click(await screen.findByRole('button', { name: 'Excluir' }))
    await waitFor(() => expect(document.body.dataset.resultado).toBe('true'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('resolve false ao cancelar e fecha o diálogo', async () => {
    const user = userEvent.setup()
    render(<Probe />)
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    await user.click(await screen.findByRole('button', { name: /cancelar|voltar/i }))
    await waitFor(() => expect(document.body.dataset.resultado).toBe('false'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('não deixa o diálogo aberto depois de confirmar', async () => {
    const user = userEvent.setup()
    render(<Probe />)
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    await user.click(await screen.findByRole('button', { name: 'Excluir' }))
    expect(screen.queryByText('Não dá para desfazer.')).not.toBeInTheDocument()
  })

  it('permite um segundo pedido depois de cancelar', async () => {
    const user = userEvent.setup()
    render(<Probe />)
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    await user.click(await screen.findByRole('button', { name: /cancelar|voltar/i }))
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('não confirma ao pressionar Escape', async () => {
    const user = userEvent.setup()
    render(<Probe />)
    await user.click(screen.getByRole('button', { name: 'Pedir' }))
    await user.keyboard('{Escape}')
    await waitFor(() => expect(document.body.dataset.resultado).toBe('false'))
  })
})
