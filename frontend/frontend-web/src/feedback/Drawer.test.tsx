import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'

import { Drawer } from './Drawer'

function Harness() {
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
          onClose={() => setAberto(false)}
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
})
