import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ThemeProvider } from '../../theme/ThemeProvider'
import { ApiError } from '../api/authApi'
import * as meApi from '../api/meApi'
import { AuthProvider } from '../session/AuthContext'
import {
  clearAccessToken,
  getAccessToken,
  limparAvisoContaExcluida,
  setAccessToken,
  temAvisoContaExcluida,
} from '../session/tokenStore'
import { PerfilPage } from './PerfilPage'

vi.mock('../api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
  exportarDados: vi.fn(),
  excluirConta: vi.fn(),
}))

function renderPerfil() {
  return render(
    <ThemeProvider>
      <AuthProvider>
        <MemoryRouter>
          <PerfilPage />
        </MemoryRouter>
      </AuthProvider>
    </ThemeProvider>,
  )
}

async function abrirDialogExclusao(user: ReturnType<typeof userEvent.setup>) {
  renderPerfil()
  await screen.findByDisplayValue('Ana Silva')
  await user.click(screen.getByRole('button', { name: /^excluir conta$/i }))
  return screen.getByRole('dialog', { name: 'Excluir conta' })
}

describe('PerfilPage › Seus dados', () => {
  const createObjectURL = vi.fn(() => 'blob:export')
  const revokeObjectURL = vi.fn()

  beforeEach(() => {
    clearAccessToken()
    limparAvisoContaExcluida()
    setAccessToken('jwt-ok')
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(meApi.exportarDados).mockReset()
    vi.mocked(meApi.excluirConta).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: '1',
      nome: 'Ana Silva',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    createObjectURL.mockClear()
    revokeObjectURL.mockClear()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exporta e baixa o arquivo com o nome vindo da API', async () => {
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.mocked(meApi.exportarDados).mockResolvedValue({
      blob: new Blob(['{}'], { type: 'application/json' }),
      nomeArquivo: 'controle-rotina-export-2026-11-24.json',
    })
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.click(screen.getByRole('button', { name: /exportar meus dados/i }))

    await waitFor(() => expect(clique).toHaveBeenCalledOnce())
    const link = clique.mock.instances[0] as unknown as HTMLAnchorElement
    expect(link.download).toBe('controle-rotina-export-2026-11-24.json')
    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:export')
    clique.mockRestore()
  })

  it('clique duplo no export faz uma única requisição', async () => {
    let resolver: (v: meApi.ArquivoExportado) => void = () => {}
    vi.mocked(meApi.exportarDados).mockReturnValue(new Promise((r) => (resolver = r)))
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')
    const botao = screen.getByRole('button', { name: /exportar meus dados/i })

    await user.click(botao)
    await user.dblClick(botao)

    expect(meApi.exportarDados).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: /exportando/i })).toBeDisabled()
    resolver({ blob: new Blob(['{}']), nomeArquivo: 'x.json' })
  })

  it('falha no export mostra erro, nada é baixado e o botão volta', async () => {
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.mocked(meApi.exportarDados).mockRejectedValue(new ApiError(500, 'Erro interno.'))
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.click(screen.getByRole('button', { name: /exportar meus dados/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Erro interno.')
    expect(clique).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /exportar meus dados/i })).toBeEnabled()
    clique.mockRestore()
  })

  it('falha de rede no export mostra mensagem genérica', async () => {
    vi.mocked(meApi.exportarDados).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderPerfil()
    await screen.findByDisplayValue('Ana Silva')

    await user.click(screen.getByRole('button', { name: /exportar meus dados/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível exportar seus dados. Tente novamente.',
    )
  })

  it('botão final fica bloqueado até digitar EXCLUIR exatamente', async () => {
    const user = userEvent.setup()
    const dialog = await abrirDialogExclusao(user)
    const botao = within(dialog).getByRole('button', { name: /excluir conta definitivamente/i })

    await user.type(within(dialog).getByLabelText(/^senha atual$/i), 'Senha@123')
    await user.type(within(dialog).getByLabelText(/para confirmar/i), 'excluir')
    expect(botao).toBeDisabled()

    await user.clear(within(dialog).getByLabelText(/para confirmar/i))
    await user.type(within(dialog).getByLabelText(/para confirmar/i), 'EXCLUIR')
    expect(botao).toBeEnabled()
    expect(meApi.excluirConta).not.toHaveBeenCalled()
  })

  it('sem senha o botão final fica bloqueado mesmo com EXCLUIR', async () => {
    const user = userEvent.setup()
    const dialog = await abrirDialogExclusao(user)

    await user.type(within(dialog).getByLabelText(/para confirmar/i), 'EXCLUIR')

    expect(within(dialog).getByRole('button', { name: /excluir conta definitivamente/i })).toBeDisabled()
  })

  it('cancelar fecha o diálogo sem chamar a API', async () => {
    const user = userEvent.setup()
    const dialog = await abrirDialogExclusao(user)

    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog', { name: 'Excluir conta' })).not.toBeInTheDocument()
    expect(meApi.excluirConta).not.toHaveBeenCalled()
    expect(getAccessToken()).toBe('jwt-ok')
  })

  it('exclusão bem-sucedida limpa a sessão e marca o aviso de conta excluída', async () => {
    vi.mocked(meApi.excluirConta).mockResolvedValue(undefined)
    const user = userEvent.setup()
    const dialog = await abrirDialogExclusao(user)

    await user.type(within(dialog).getByLabelText(/^senha atual$/i), 'Senha@123')
    await user.type(within(dialog).getByLabelText(/para confirmar/i), 'EXCLUIR')
    await user.click(within(dialog).getByRole('button', { name: /excluir conta definitivamente/i }))

    await waitFor(() => expect(getAccessToken()).toBeNull())
    expect(meApi.excluirConta).toHaveBeenCalledWith('Senha@123')
    expect(temAvisoContaExcluida()).toBe(true)
  })

  it('senha incorreta mantém o diálogo aberto com erro no campo e não desloga', async () => {
    vi.mocked(meApi.excluirConta).mockRejectedValue(new ApiError(401, 'Credenciais inválidas.'))
    const user = userEvent.setup()
    const dialog = await abrirDialogExclusao(user)

    await user.type(within(dialog).getByLabelText(/^senha atual$/i), 'errada')
    await user.type(within(dialog).getByLabelText(/para confirmar/i), 'EXCLUIR')
    await user.click(within(dialog).getByRole('button', { name: /excluir conta definitivamente/i }))

    expect(await within(dialog).findByText('Senha atual incorreta.')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Excluir conta' })).toBeInTheDocument()
    expect(getAccessToken()).toBe('jwt-ok')
    expect(temAvisoContaExcluida()).toBe(false)
  })

  it('erro de servidor mantém a sessão e permite tentar de novo', async () => {
    vi.mocked(meApi.excluirConta)
      .mockRejectedValueOnce(new ApiError(500, 'Não foi possível concluir a operação.', 'Erro de integridade'))
      .mockResolvedValueOnce(undefined)
    const user = userEvent.setup()
    const dialog = await abrirDialogExclusao(user)
    await user.type(within(dialog).getByLabelText(/^senha atual$/i), 'Senha@123')
    await user.type(within(dialog).getByLabelText(/para confirmar/i), 'EXCLUIR')
    const botao = within(dialog).getByRole('button', { name: /excluir conta definitivamente/i })

    await user.click(botao)

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Não foi possível excluir a conta. Tente novamente.',
    )
    expect(getAccessToken()).toBe('jwt-ok')
    expect(botao).toBeEnabled()

    await user.click(botao)
    await waitFor(() => expect(getAccessToken()).toBeNull())
  })
})
