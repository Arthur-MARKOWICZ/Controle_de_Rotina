import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'

import { CampoSenha } from './CampoSenha'

function Harness({ checklist = false }: { checklist?: boolean }) {
  const [senha, setSenha] = useState('')
  return (
    <CampoSenha
      id="senha"
      label="Senha"
      value={senha}
      onChange={setSenha}
      mostrarChecklist={checklist}
    />
  )
}

describe('CampoSenha', () => {
  it('T1: checklist marca regra ao digitar caractere que a satisfaz', async () => {
    const user = userEvent.setup()
    render(<Harness checklist />)

    const minuscula = screen.getByText(/uma letra minúscula/i).closest('li')
    expect(minuscula).toHaveAttribute('data-ok', 'false')

    await user.type(screen.getByLabelText(/^senha$/i), 'a')
    expect(minuscula).toHaveAttribute('data-ok', 'true')
  })

  it('T2: toggle mostrar/ocultar senha altera type', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    const input = screen.getByLabelText(/^senha$/i)
    expect(input).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: /mostrar senha/i }))
    expect(input).toHaveAttribute('type', 'text')

    await user.click(screen.getByRole('button', { name: /ocultar senha/i }))
    expect(input).toHaveAttribute('type', 'password')
  })
})
