import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from '../api/meApi'
import { ApiError } from '../api/authApi'
import { PerfilPage } from './PerfilPage'

vi.mock('../api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
}))

function renderPerfil() {
  return render(
    <MemoryRouter>
      <PerfilPage />
    </MemoryRouter>,
  )
}

describe('PerfilPage', () => {
  beforeEach(() => {
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(meApi.atualizarMe).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: '1',
      nome: 'Ana Silva',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
  })

  it('carrega nome e email de /me', async () => {
    renderPerfil()

    expect(await screen.findByDisplayValue('Ana Silva')).toBeInTheDocument()
    expect(screen.getByDisplayValue('ana@email.com')).toBeInTheDocument()
  })

  it('T3: renderiza seções Dados e Alterar senha distintas', async () => {
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    expect(screen.getByRole('group', { name: 'Dados' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Alterar senha' })).toBeInTheDocument()
  })

  it('exige senha atual para salvar', async () => {
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.clear(screen.getByLabelText(/^nome$/i))
    await user.type(screen.getByLabelText(/^nome$/i), 'Ana Atualizada')
    await user.click(screen.getByRole('button', { name: /salvar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/senha atual/i)
    expect(meApi.atualizarMe).not.toHaveBeenCalled()
  })

  it('rejeita nova senha fraca', async () => {
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.type(screen.getByLabelText(/^senha atual$/i), 'Senha@123')
    await user.type(screen.getByLabelText(/^nova senha$/i), 'fraca')
    await user.type(screen.getByLabelText(/confirmar nova senha/i), 'fraca')
    await user.click(screen.getByRole('button', { name: /salvar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/senha forte/i)
    expect(meApi.atualizarMe).not.toHaveBeenCalled()
  })

  it('rejeita confirmação de nova senha diferente', async () => {
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.type(screen.getByLabelText(/^senha atual$/i), 'Senha@123')
    await user.type(screen.getByLabelText(/^nova senha$/i), 'NovaSenha@456')
    await user.type(screen.getByLabelText(/confirmar nova senha/i), 'Outra@456')
    await user.click(screen.getByRole('button', { name: /salvar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/senhas não conferem/i)
    expect(meApi.atualizarMe).not.toHaveBeenCalled()
  })

  it('envia atualização com senha atual e campos alterados', async () => {
    vi.mocked(meApi.atualizarMe).mockResolvedValue({
      id: '1',
      nome: 'Ana Atualizada',
      email: 'nova@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.clear(screen.getByLabelText(/^nome$/i))
    await user.type(screen.getByLabelText(/^nome$/i), 'Ana Atualizada')
    await user.clear(screen.getByLabelText(/^email$/i))
    await user.type(screen.getByLabelText(/^email$/i), 'nova@email.com')
    await user.type(screen.getByLabelText(/^senha atual$/i), 'Senha@123')
    await user.type(screen.getByLabelText(/^nova senha$/i), 'NovaSenha@456')
    await user.type(screen.getByLabelText(/confirmar nova senha/i), 'NovaSenha@456')
    await user.click(screen.getByRole('button', { name: /salvar/i }))

    await waitFor(() => {
      expect(meApi.atualizarMe).toHaveBeenCalledWith({
        senhaAtual: 'Senha@123',
        nome: 'Ana Atualizada',
        email: 'nova@email.com',
        novaSenha: 'NovaSenha@456',
      })
    })
    expect(await screen.findByRole('status')).toHaveTextContent(/atualizado/i)
  })

  it('mostra erro quando a senha atual está incorreta', async () => {
    vi.mocked(meApi.atualizarMe).mockRejectedValue(
      new ApiError(401, 'Credenciais inválidas.'),
    )
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.type(screen.getByLabelText(/^senha atual$/i), 'Errada@123')
    await user.click(screen.getByRole('button', { name: /salvar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/senha atual/i)
  })
})
