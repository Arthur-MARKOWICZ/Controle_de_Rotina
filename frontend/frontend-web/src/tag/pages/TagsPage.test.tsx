import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as tagApi from '../api/tagApi'
import { TagsPage } from './TagsPage'

const toastSuccess = vi.fn()

vi.mock('../../feedback/useConfirmDialog', () => ({
  useConfirmDialog: () => ({
    confirm: vi.fn().mockResolvedValue(false),
    dialog: null,
  }),
}))

vi.mock('../../feedback/ToastProvider', () => ({
  useToast: () => ({ success: toastSuccess, error: vi.fn() }),
}))

vi.mock('../api/tagApi', () => ({
  TAG_COR_PADRAO: '#6B7280',
  listarTags: vi.fn(),
  criarTag: vi.fn(),
  atualizarTag: vi.fn(),
  excluirTag: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter>
      <TagsPage />
    </MemoryRouter>,
  )
}

describe('TagsPage', () => {
  beforeEach(() => {
    toastSuccess.mockReset()
    vi.mocked(tagApi.listarTags).mockResolvedValue([
      { id: 't1', nome: 'Foco', cor: '#6B7280' },
    ])
    vi.mocked(tagApi.atualizarTag).mockResolvedValue({
      id: 't1',
      nome: 'Foco',
      cor: '#112233',
    })
    vi.mocked(tagApi.criarTag).mockReset()
    vi.mocked(tagApi.excluirTag).mockReset()
  })

  it('renderiza lista de tags', async () => {
    renderPage()
    expect(await screen.findByDisplayValue('Foco')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /tags/i })).toBeInTheDocument()
  })

  it('só salva e notifica ao soltar a cor (blur), não a cada mudança', async () => {
    renderPage()
    const cor = await screen.findByLabelText('Cor de Foco')

    fireEvent.change(cor, { target: { value: '#aabbcc' } })
    fireEvent.change(cor, { target: { value: '#112233' } })
    expect(tagApi.atualizarTag).not.toHaveBeenCalled()
    expect(toastSuccess).not.toHaveBeenCalled()

    fireEvent.blur(cor)
    await waitFor(() => {
      expect(tagApi.atualizarTag).toHaveBeenCalledTimes(1)
    })
    expect(tagApi.atualizarTag).toHaveBeenCalledWith('t1', { nome: 'Foco', cor: '#112233' })
    expect(toastSuccess).toHaveBeenCalledTimes(1)
  })

  it('mostra vazio quando não há tags', async () => {
    vi.mocked(tagApi.listarTags).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText(/nenhuma tag ainda/i)).toBeInTheDocument()
  })

  it('mostra erro e lista vazia quando listar falha', async () => {
    const { ApiError } = await import('../../auth/api/authApi')
    vi.mocked(tagApi.listarTags).mockRejectedValue(new ApiError(500, 'Falha ao listar.'))
    renderPage()
    expect(await screen.findByText('Falha ao listar.')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('Foco')).not.toBeInTheDocument()
  })

  it('não cria tag com nome vazio', async () => {
    vi.mocked(tagApi.criarTag).mockResolvedValue({ id: 't2', nome: 'Nova', cor: '#6B7280' })
    renderPage()
    await screen.findByDisplayValue('Foco')
    expect(screen.getByRole('button', { name: /criar/i })).toBeDisabled()
    expect(tagApi.criarTag).not.toHaveBeenCalled()
  })

  it('cria tag válida e notifica', async () => {
    vi.mocked(tagApi.criarTag).mockResolvedValue({ id: 't2', nome: 'Nova', cor: '#6B7280' })
    renderPage()
    const nome = await screen.findByLabelText(/nova tag/i)
    fireEvent.change(nome, { target: { value: 'Nova' } })
    fireEvent.submit(nome.closest('form')!)
    await waitFor(() => {
      expect(tagApi.criarTag).toHaveBeenCalledWith({ nome: 'Nova', cor: '#6B7280' })
    })
    expect(toastSuccess).toHaveBeenCalledWith('Tag criada')
  })

  it('mostra ApiError 409 ao criar e preserva o nome', async () => {
    const { ApiError } = await import('../../auth/api/authApi')
    vi.mocked(tagApi.criarTag).mockRejectedValue(new ApiError(409, 'Nome duplicado.'))
    renderPage()
    const nome = await screen.findByLabelText(/nova tag/i)
    fireEvent.change(nome, { target: { value: 'Foco' } })
    fireEvent.submit(nome.closest('form')!)
    expect(await screen.findByText('Nome duplicado.')).toBeInTheDocument()
    expect(nome).toHaveValue('Foco')
  })
})
