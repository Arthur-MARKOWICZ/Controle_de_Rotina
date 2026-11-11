import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../../feedback/ToastProvider'
import * as projetoApi from '../../projeto/api/projetoApi'
import * as decidirApi from '../api/decidirApi'
import { DecidirPage } from './DecidirPage'

vi.mock('../../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([
    { id: 'tag-1', nome: 'Foco', cor: '#6B7280' },
  ]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

vi.mock('../../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

vi.mock('../api/decidirApi', () => ({
  listarDecidir: vi.fn(),
  criarDecidir: vi.fn(),
  buscarDecidir: vi.fn(),
  atualizarDecidir: vi.fn(),
  excluirDecidir: vi.fn(),
  promoverDecidir: vi.fn(),
}))

const itemAberto = {
  id: 'd-1',
  titulo: 'Comprar assinatura',
  descricao: null,
  decidirAte: null,
  projetoId: null,
  tarefaId: null,
  tags: [{ id: 'tag-1', nome: 'Foco', cor: '#6B7280' }],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const itemVencido = {
  ...itemAberto,
  id: 'd-vencido',
  titulo: 'Prazo ontem',
  decidirAte: '2020-01-01T12:00:00Z',
  tags: [],
}

const itemPromovido = {
  ...itemAberto,
  id: 'd-promovido',
  titulo: 'Já decidido',
  tarefaId: 'tarefa-1',
  tags: [],
}

function renderPage(path = '/decidir') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/decidir" element={<DecidirPage />} />
          <Route path="/tarefas/:id" element={<p>tarefa</p>} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('DecidirPage', () => {
  beforeEach(() => {
    vi.mocked(decidirApi.listarDecidir).mockReset()
    vi.mocked(decidirApi.criarDecidir).mockReset()
    vi.mocked(decidirApi.excluirDecidir).mockReset()
    vi.mocked(decidirApi.promoverDecidir).mockReset()
    vi.mocked(decidirApi.atualizarDecidir).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 100,
    })
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue({
      content: [itemAberto],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
    })
  })

  it('cria item pelo inline add só com título e mostra na lista', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.criarDecidir).mockResolvedValue({
      ...itemAberto,
      id: 'd-novo',
      titulo: 'Novo item',
      tags: [],
    })
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 50,
    })

    renderPage()
    expect(await screen.findByText(/nada para decidir/i)).toBeInTheDocument()

    const input = screen.getByRole('textbox', { name: /adicionar item/i })
    await user.type(input, 'Novo item{Enter}')

    await waitFor(() => {
      expect(decidirApi.criarDecidir).toHaveBeenCalledWith({ titulo: 'Novo item' })
    })
    expect(input).toHaveValue('')
    expect(await screen.findByText('Novo item')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
  })

  it('empty state foca o inline add', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 50,
    })
    renderPage()
    const empty = await screen.findByText(/nada para decidir/i)
    const bloco = empty.closest('.decidir-empty')
    expect(bloco).not.toBeNull()
    await user.click(within(bloco as HTMLElement).getByRole('button', { name: /novo item/i }))
    expect(screen.getByRole('textbox', { name: /adicionar item/i })).toHaveFocus()
  })

  it('destaca item com decidirAte vencido', async () => {
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue({
      content: [itemVencido],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
    })
    renderPage()
    const card = await screen.findByText('Prazo ontem')
    expect(card.closest('[data-vencido="true"]')).toBeTruthy()
  })

  it('promove remove da fila aberta', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.promoverDecidir).mockResolvedValue({
      ...itemAberto,
      tarefaId: 'tarefa-1',
    })
    vi.mocked(decidirApi.listarDecidir)
      .mockResolvedValueOnce({
        content: [itemAberto],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 50,
      })
      .mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 50,
      })

    renderPage()
    expect(await screen.findByText('Comprar assinatura')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^promover$/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    await waitFor(() => {
      expect(decidirApi.promoverDecidir).toHaveBeenCalledWith('d-1', {})
    })
    expect(await screen.findByText(/nada para decidir/i)).toBeInTheDocument()
  })

  it('descarta com confirmação e some da lista', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.excluirDecidir).mockResolvedValue()
    vi.mocked(decidirApi.listarDecidir)
      .mockResolvedValueOnce({
        content: [itemAberto],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 50,
      })
      .mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 50,
      })

    renderPage()
    expect(await screen.findByText('Comprar assinatura')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /descartar/i }))
    const confirm = await screen.findByRole('dialog')
    await user.click(within(confirm).getByRole('button', { name: /descartar/i }))

    await waitFor(() => {
      expect(decidirApi.excluirDecidir).toHaveBeenCalledWith('d-1')
    })
    expect(await screen.findByText(/nada para decidir/i)).toBeInTheDocument()
  })

  it('visão já decididos é somente leitura', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue({
      content: [itemPromovido],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
    })

    renderPage('/decidir?promovidos=true')
    expect(await screen.findByText('Já decidido')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^promover$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /descartar/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /adicionar item/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /ver tarefa/i })).toHaveAttribute(
      'href',
      '/tarefas/tarefa-1',
    )
    expect(decidirApi.listarDecidir).toHaveBeenCalledWith(
      expect.objectContaining({ promovidos: true }),
    )

    await user.click(screen.getByRole('button', { name: /abertos/i }))
    await waitFor(() => {
      expect(decidirApi.listarDecidir).toHaveBeenCalledWith(
        expect.objectContaining({ promovidos: false }),
      )
    })
  })

  it('filtro de tags na URL chama listagem com tagIds', async () => {
    renderPage('/decidir?tagId=tag-1')
    await waitFor(() => {
      expect(decidirApi.listarDecidir).toHaveBeenCalledWith(
        expect.objectContaining({ tagIds: ['tag-1'] }),
      )
    })
    expect(await screen.findByText('Comprar assinatura')).toBeInTheDocument()
    expect(screen.getByText('Foco')).toBeInTheDocument()
  })
})
