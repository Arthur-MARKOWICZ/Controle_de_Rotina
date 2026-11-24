import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as objetivoApi from '../objetivo/api/objetivoApi'
import type { ObjetivoResponse } from '../objetivo/api/objetivoApi'
import * as projetoApi from './api/projetoApi'
import { ProjetoDrawer } from './ProjetoDrawer'

vi.mock('../objetivo/api/objetivoApi', () => ({
  listarObjetivos: vi.fn(),
}))

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

vi.mock('./api/projetoApi', () => ({
  criarProjeto: vi.fn(),
}))

const objetivoAtivo: ObjetivoResponse = {
  id: 'o1',
  titulo: 'Passar na certificação',
  descricao: null,
  prazo: null,
  status: 'ATIVO',
  concluidoEm: null,
  arquivadoEm: null,
  createdAt: '2026-11-01T12:00:00Z',
  updatedAt: '2026-11-01T12:00:00Z',
  progresso: null,
}

function paginaObjetivos(content: ObjetivoResponse[]) {
  return {
    content,
    page: { size: 100, number: 0, totalElements: content.length, totalPages: 1 },
  }
}

function renderDrawer() {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/projetos/nova']}>
        <Routes>
          <Route path="/projetos/nova" element={<ProjetoDrawer />} />
          <Route path="/projetos/:id" element={<p>página editar projeto</p>} />
          <Route path="/projetos" element={<p>lista</p>} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('ProjetoDrawer', () => {
  beforeEach(() => {
    vi.mocked(projetoApi.criarProjeto).mockReset()
    vi.mocked(objetivoApi.listarObjetivos).mockReset()
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(paginaObjetivos([objetivoAtivo]))
  })

  it('cria projeto válido e navega para o detalhe', async () => {
    vi.mocked(projetoApi.criarProjeto).mockResolvedValue({
      id: 'p-1',
      nome: 'TCC',
      descricao: null,
      status: 'ATIVO',
      createdAt: '',
      updatedAt: '',
    })
    const user = userEvent.setup()
    renderDrawer()

    await user.type(screen.getByLabelText('Nome'), 'TCC')
    await user.click(screen.getByRole('button', { name: /criar projeto/i }))

    await waitFor(() => {
      expect(projetoApi.criarProjeto).toHaveBeenCalledWith(
        expect.objectContaining({ nome: 'TCC' }),
      )
    })
    expect(await screen.findByText('página editar projeto')).toBeInTheDocument()
  })

  it('não chama a API com nome vazio', async () => {
    const user = userEvent.setup()
    renderDrawer()

    await user.click(screen.getByRole('button', { name: /criar projeto/i }))

    expect(await screen.findByText('Informe o nome do projeto.')).toBeInTheDocument()
    expect(projetoApi.criarProjeto).not.toHaveBeenCalled()
  })

  it('não chama a API com nome só de espaços', async () => {
    const user = userEvent.setup()
    renderDrawer()

    await user.type(screen.getByLabelText('Nome'), '   ')
    await user.click(screen.getByRole('button', { name: /criar projeto/i }))

    expect(await screen.findByText('Informe o nome do projeto.')).toBeInTheDocument()
    expect(projetoApi.criarProjeto).not.toHaveBeenCalled()
  })

  it('fecha sem confirmar quando o form está vazio', async () => {
    const user = userEvent.setup()
    renderDrawer()

    await user.click(screen.getByRole('button', { name: /cancelar/i }))

    expect(await screen.findByText('lista')).toBeInTheDocument()
    expect(projetoApi.criarProjeto).not.toHaveBeenCalled()
  })

  it('não descarta alterações se o usuário cancelar o diálogo', async () => {
    const user = userEvent.setup()
    renderDrawer()

    await user.type(screen.getByLabelText('Nome'), 'TCC')
    await user.click(screen.getByRole('button', { name: /cancelar/i }))
    const dialogo = await screen.findByRole('dialog', { name: /descartar projeto/i })
    await user.click(dialogo.querySelector('.btn--secondary') as HTMLButtonElement)

    expect(screen.getByLabelText('Nome')).toHaveValue('TCC')
    expect(screen.queryByText('lista')).not.toBeInTheDocument()
  })

  it('mostra ApiError ao criar e preserva o nome', async () => {
    vi.mocked(projetoApi.criarProjeto).mockRejectedValue(new ApiError(409, 'Nome duplicado'))
    const user = userEvent.setup()
    renderDrawer()

    await user.type(screen.getByLabelText('Nome'), 'TCC')
    await user.click(screen.getByRole('button', { name: /criar projeto/i }))

    expect(await screen.findByText('Nome duplicado')).toBeInTheDocument()
    expect(screen.getByLabelText('Nome')).toHaveValue('TCC')
  })

  it('mostra fallback para erro de rede', async () => {
    vi.mocked(projetoApi.criarProjeto).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderDrawer()

    await user.type(screen.getByLabelText('Nome'), 'TCC')
    await user.click(screen.getByRole('button', { name: /criar projeto/i }))

    expect(
      await screen.findByText('Não foi possível criar o projeto. Tente novamente.'),
    ).toBeInTheDocument()
  })

  describe('objetivos', () => {
    async function escolher(user: ReturnType<typeof userEvent.setup>) {
      await user.click(screen.getByRole('button', { name: /escolher objetivos/i }))
      await user.click(await screen.findByRole('button', { name: 'Passar na certificação' }))
    }

    it('cria projeto vinculado ao objetivo escolhido', async () => {
      vi.mocked(projetoApi.criarProjeto).mockResolvedValue({
        id: 'p-1',
        nome: 'Estudos AWS',
        descricao: null,
        status: 'ATIVO',
        createdAt: '',
        updatedAt: '',
      })
      const user = userEvent.setup()
      renderDrawer()

      await user.type(screen.getByLabelText('Nome'), 'Estudos AWS')
      await escolher(user)
      await user.click(screen.getByRole('button', { name: /criar projeto/i }))

      await waitFor(() => {
        expect(projetoApi.criarProjeto).toHaveBeenCalledWith(
          expect.objectContaining({ nome: 'Estudos AWS', objetivoIds: ['o1'] }),
        )
      })
    })

    it('sem escolher objetivo envia lista vazia', async () => {
      vi.mocked(projetoApi.criarProjeto).mockResolvedValue({
        id: 'p-1',
        nome: 'TCC',
        descricao: null,
        status: 'ATIVO',
        createdAt: '',
        updatedAt: '',
      })
      const user = userEvent.setup()
      renderDrawer()

      await user.type(screen.getByLabelText('Nome'), 'TCC')
      await user.click(screen.getByRole('button', { name: /criar projeto/i }))

      await waitFor(() => {
        expect(projetoApi.criarProjeto).toHaveBeenCalledWith(
          expect.objectContaining({ objetivoIds: [] }),
        )
      })
    })

    it('escolher só um objetivo já conta como alteração e pede descarte ao fechar', async () => {
      const user = userEvent.setup()
      renderDrawer()

      await escolher(user)
      await user.keyboard('{Escape}')
      await user.keyboard('{Escape}')

      expect(await screen.findByRole('dialog', { name: /descartar/i })).toBeInTheDocument()
      expect(projetoApi.criarProjeto).not.toHaveBeenCalled()
    })

    it('409 por objetivo não ativo mostra a mensagem e preserva o nome', async () => {
      vi.mocked(projetoApi.criarProjeto).mockRejectedValue(
        new ApiError(409, 'Só é possível vincular a um objetivo ativo.'),
      )
      const user = userEvent.setup()
      renderDrawer()

      await user.type(screen.getByLabelText('Nome'), 'TCC')
      await escolher(user)
      await user.click(screen.getByRole('button', { name: /criar projeto/i }))

      expect(await screen.findByText('Só é possível vincular a um objetivo ativo.')).toBeInTheDocument()
      expect(screen.getByLabelText('Nome')).toHaveValue('TCC')
    })
  })
})
