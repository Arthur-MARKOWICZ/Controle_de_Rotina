import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { Drawer } from './Drawer'

type HarnessProps = {
  headerStart?: ReactNode
  onClose?: () => void
}

function Harness({ headerStart, onClose }: HarnessProps = {}) {
  const [montado, setMontado] = useState(false)
  const [aberto, setAberto] = useState(false)
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setMontado(true)
          setAberto(true)
        }}
      >
        Abrir drawer
      </button>
      {montado ? (
        <Drawer
          open={aberto}
          title="Editar"
          headerStart={headerStart}
          onClose={() => {
            onClose?.()
            setAberto(false)
          }}
          onExited={() => setMontado(false)}
          footer={<button type="button">Salvar</button>}
        >
          <label htmlFor="campo-nome">Nome</label>
          <input id="campo-nome" />
        </Drawer>
      ) : null}
    </>
  )
}

describe('Drawer', () => {
  it('T1: abre com foco no primeiro campo; Esc fecha e devolve foco ao gatilho', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    const gatilho = screen.getByRole('button', { name: 'Abrir drawer' })
    await user.click(gatilho)

    expect(screen.getByRole('dialog', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByLabelText('Nome')).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Fechar' })).toBeInTheDocument()

    await user.keyboard('{Escape}')

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Editar' })).not.toBeInTheDocument()
    })
    expect(gatilho).toHaveFocus()
  })

  it('botão Fechar pede fechamento', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Abrir drawer' }))
    await user.click(screen.getByRole('button', { name: 'Fechar' }))

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Editar' })).not.toBeInTheDocument()
    })
  })

  it('renderiza headerStart opcional sem quebrar o título e o Fechar', async () => {
    const user = userEvent.setup()
    render(
      <Harness
        headerStart={
          <button type="button" aria-label="Voltar">
            Voltar
          </button>
        }
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Abrir drawer' }))

    expect(screen.getByRole('button', { name: 'Voltar' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Fechar' })).toBeInTheDocument()
  })

  it('Esc, Fechar e backdrop chamam apenas onClose', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const { rerender } = render(<Harness onClose={onClose} />)

    await user.click(screen.getByRole('button', { name: 'Abrir drawer' }))
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)

    rerender(<Harness onClose={onClose} />)
    await user.click(screen.getByRole('button', { name: 'Abrir drawer' }))
    await user.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(onClose).toHaveBeenCalledTimes(2)

    rerender(<Harness onClose={onClose} />)
    await user.click(screen.getByRole('button', { name: 'Abrir drawer' }))
    await user.click(screen.getByRole('dialog', { name: 'Editar' }))
    expect(onClose).toHaveBeenCalledTimes(3)
  })
})
