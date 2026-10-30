import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../../feedback/ToastProvider'
import * as projetoApi from '../api/projetoApi'
import { ProjetosPage } from './ProjetosPage'

vi.mock('../api/projetoApi', () => ({
  listarProjetos: vi.fn(),
  arquivarProjeto: vi.fn(),
  reativarProjeto: vi.fn(),
}))

const pagina = {
  content: [
    {
      id: 'p-tcc',
      nome: 'TCC',
      descricao: 'Trabalho de conclusão de curso com escopo bem longo para truncar',
      status: 'ATIVO' as const,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

function renderLista(path = '/projetos') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/projetos" element={<ProjetosPage />} />
          <Route path="/projetos/nova" element={<p>página novo projeto</p>} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('ProjetosPage', () => {
  beforeEach(() => {
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(projetoApi.arquivarProjeto).mockReset()
    vi.mocked(projetoApi.reativarProjeto).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue(pagina)
  })

  it('T1: lista renderiza projetos e arquivar chama API', async () => {
    vi.mocked(projetoApi.arquivarProjeto).mockResolvedValue({
      ...pagina.content[0],
      status: 'ARQUIVADO',
    })
    vi.mocked(projetoApi.listarProjetos)
      .mockResolvedValueOnce(pagina)
      .mockResolvedValueOnce({ ...pagina, content: [], totalElements: 0, totalPages: 0 })

    const user = userEvent.setup()
    renderLista()

    expect(await screen.findByText('TCC')).toBeInTheDocument()
    expect(document.querySelector('.status-ativo')).toHaveTextContent('Ativo')
    expect(projetoApi.listarProjetos).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ATIVO', page: 0, size: 20 }),
    )

    await user.click(screen.getByRole('button', { name: /^arquivar$/i }))
    expect(projetoApi.arquivarProjeto).not.toHaveBeenCalled()

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /arquivar projeto/i })).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^arquivar$/i }))

    await waitFor(() => {
      expect(projetoApi.arquivarProjeto).toHaveBeenCalledWith('p-tcc')
    })
  })

  it('empty state ativo oferece CTA Criar primeiro projeto', async () => {
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })
    renderLista()

    expect(await screen.findByText(/nenhum projeto ainda/i)).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /criar primeiro projeto/i }),
    ).toHaveAttribute('href', '/projetos/nova')
  })

  it('visão Arquivados lista e permite reativar', async () => {
    const arquivado = {
      ...pagina.content[0],
      status: 'ARQUIVADO' as const,
    }
    vi.mocked(projetoApi.listarProjetos)
      .mockResolvedValueOnce(pagina)
      .mockResolvedValueOnce({
        ...pagina,
        content: [arquivado],
      })
    vi.mocked(projetoApi.reativarProjeto).mockResolvedValue({
      ...arquivado,
      status: 'ATIVO',
    })

    const user = userEvent.setup()
    renderLista()
    await screen.findByText('TCC')

    await user.click(screen.getByRole('button', { name: 'Arquivados' }))

    await waitFor(() => {
      expect(projetoApi.listarProjetos).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'ARQUIVADO' }),
      )
    })

    expect(await screen.findByText('Arquivado')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^reativar$/i }))

    await waitFor(() => {
      expect(projetoApi.reativarProjeto).toHaveBeenCalledWith('p-tcc')
    })
  })
})
