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

  it('sem checklist não renderiza regras', () => {
    render(<Harness />)
    expect(screen.queryByText(/uma letra minúscula/i)).not.toBeInTheDocument()
  })

  it('mostra erro e marca o campo inválido', () => {
    render(
      <CampoSenha
        id="senha"
        label="Senha"
        value=""
        onChange={() => {}}
        erro="Informe a senha."
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(/informe a senha/i)
    expect(screen.getByLabelText(/^senha$/i)).toHaveAttribute('aria-invalid', 'true')
  })

  it('sem erro não marca inválido', () => {
    render(<Harness />)
    expect(screen.getByLabelText(/^senha$/i)).not.toHaveAttribute('aria-invalid')
  })

  it('toggle começa como mostrar senha', () => {
    render(<Harness />)
    expect(screen.getByRole('button', { name: /mostrar senha/i })).toHaveAttribute('aria-pressed', 'false')
  })

  it('checklist de maiúscula permanece falsa com só minúscula', async () => {
    const user = userEvent.setup()
    render(<Harness checklist />)
    await user.type(screen.getByLabelText(/^senha$/i), 'abc')
    const maiuscula = screen.getByText(/uma letra maiúscula/i).closest('li')
    expect(maiuscula).toHaveAttribute('data-ok', 'false')
  })
})
