import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '../api/authApi'
import { CadastroPage } from './CadastroPage'

vi.mock('../api/authApi', () => ({
  cadastrarUsuario: vi.fn(),
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

function renderCadastro() {
  return render(
    <MemoryRouter initialEntries={['/cadastro']}>
      <Routes>
        <Route path="/cadastro" element={<CadastroPage />} />
        <Route path="/login" element={<p>Tela de login</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('CadastroPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.cadastrarUsuario).mockReset()
  })

  it('exibe proposta de valor e checklist de senha', async () => {
    const user = userEvent.setup()
    renderCadastro()

    expect(
      screen.getByText(/diga o que precisa fazer; o sistema monta o plano/i),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/requisitos da senha/i)).toBeInTheDocument()

    const minuscula = screen.getByText(/uma letra minúscula/i).closest('li')
    expect(minuscula).toHaveAttribute('data-ok', 'false')
    await user.type(screen.getByLabelText(/^senha$/i), 'a')
    expect(minuscula).toHaveAttribute('data-ok', 'true')
  })

  it('mostra erro quando as senhas não conferem', async () => {
    const user = userEvent.setup()
    renderCadastro()

    await user.type(screen.getByLabelText(/^nome$/i), 'Ana Silva')
    await user.type(screen.getByLabelText(/^email$/i), 'ana@email.com')
    await user.type(screen.getByLabelText(/^senha$/i), 'Senha@123')
    await user.type(screen.getByLabelText(/confirmar senha/i), 'Senha@999')
    await user.click(screen.getByRole('button', { name: /criar conta/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/senhas não conferem/i)
    expect(authApi.cadastrarUsuario).not.toHaveBeenCalled()
  })

  it('mostra erro quando a senha é fraca', async () => {
    const user = userEvent.setup()
    renderCadastro()

    await user.type(screen.getByLabelText(/^nome$/i), 'Ana Silva')
    await user.type(screen.getByLabelText(/^email$/i), 'ana@email.com')
    await user.type(screen.getByLabelText(/^senha$/i), 'fraca')
    await user.type(screen.getByLabelText(/confirmar senha/i), 'fraca')
    await user.click(screen.getByRole('button', { name: /criar conta/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/senha forte/i)
    expect(authApi.cadastrarUsuario).not.toHaveBeenCalled()
  })

  it('redireciona para login após cadastro bem-sucedido', async () => {
    vi.mocked(authApi.cadastrarUsuario).mockResolvedValue({
      id: '1',
      nome: 'Ana Silva',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    const user = userEvent.setup()
    renderCadastro()

    await user.type(screen.getByLabelText(/^nome$/i), 'Ana Silva')
    await user.type(screen.getByLabelText(/^email$/i), 'ana@email.com')
    await user.type(screen.getByLabelText(/^senha$/i), 'Senha@123')
    await user.type(screen.getByLabelText(/confirmar senha/i), 'Senha@123')
    await user.click(screen.getByRole('button', { name: /criar conta/i }))

    await waitFor(() => {
      expect(screen.getByText('Tela de login')).toBeInTheDocument()
    })
    expect(authApi.cadastrarUsuario).toHaveBeenCalledWith({
      nome: 'Ana Silva',
      email: 'ana@email.com',
      senha: 'Senha@123',
    })
  })
})
