import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as decidirApi from './api/decidirApi'
import { InlineAddDecidir } from './InlineAddDecidir'

vi.mock('./api/decidirApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/decidirApi')>()
  return {
    ...actual,
    criarDecidir: vi.fn(),
  }
})

const onCriado = vi.fn()

function renderAdd() {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <InlineAddDecidir onCriado={onCriado} />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('InlineAddDecidir', () => {
  beforeEach(() => {
    onCriado.mockReset()
    vi.mocked(decidirApi.criarDecidir).mockReset()
  })

  it('cria item só com o título', async () => {
    vi.mocked(decidirApi.criarDecidir).mockResolvedValue({
      id: 'd-1',
      titulo: 'Comprar curso',
      descricao: null,
      decidirAte: null,
      projetoId: null,
      tarefaId: null,
      createdAt: '',
      updatedAt: '',
    })
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar item…'), 'Comprar curso')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    await waitFor(() => {
      expect(decidirApi.criarDecidir).toHaveBeenCalledWith({ titulo: 'Comprar curso' })
    })
    expect(onCriado).toHaveBeenCalled()
    expect(screen.getByPlaceholderText('Adicionar item…')).toHaveValue('')
  })

  it('não cria com título vazio', async () => {
    const user = userEvent.setup()
    renderAdd()

    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(decidirApi.criarDecidir).not.toHaveBeenCalled()
    expect(onCriado).not.toHaveBeenCalled()
  })

  it('não cria com espaços', async () => {
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar item…'), '   ')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(decidirApi.criarDecidir).not.toHaveBeenCalled()
  })

  it('limpa com Escape sem chamar a API', async () => {
    const user = userEvent.setup()
    renderAdd()
    const campo = screen.getByPlaceholderText('Adicionar item…')
    await user.type(campo, 'Rascunho')
    await user.keyboard('{Escape}')

    expect(campo).toHaveValue('')
    expect(decidirApi.criarDecidir).not.toHaveBeenCalled()
  })

  it('mostra ApiError e preserva o título', async () => {
    vi.mocked(decidirApi.criarDecidir).mockRejectedValue(new ApiError(400, 'Título inválido'))
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar item…'), 'X')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Título inválido')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Adicionar item…')).toHaveValue('X')
    expect(onCriado).not.toHaveBeenCalled()
  })

  it('mostra fallback para erro de rede', async () => {
    vi.mocked(decidirApi.criarDecidir).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar item…'), 'X')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Não foi possível criar o item. Tente novamente.')).toBeInTheDocument()
    expect(onCriado).not.toHaveBeenCalled()
  })

  it('mostra toast de sucesso após criar', async () => {
    vi.mocked(decidirApi.criarDecidir).mockResolvedValue({
      id: 'd-1',
      titulo: 'Item',
      descricao: null,
      decidirAte: null,
      projetoId: null,
      tarefaId: null,
      createdAt: '',
      updatedAt: '',
    })
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar item…'), 'Item')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Item capturado')).toBeInTheDocument()
  })
})
