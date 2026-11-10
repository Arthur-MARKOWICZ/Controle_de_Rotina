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
  })

  it('renderiza lista de tags', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /tags/i })).toBeInTheDocument()
    })
    expect(screen.getByDisplayValue('Foco')).toBeInTheDocument()
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
})
