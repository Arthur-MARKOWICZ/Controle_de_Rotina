import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../../feedback/ToastProvider'
import * as projetoApi from '../api/projetoApi'
import { ProjetoDrawer } from '../ProjetoDrawer'
import { ProjetosPage } from './ProjetosPage'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

vi.mock('../api/projetoApi', () => ({
  listarProjetos: vi.fn(),
  arquivarProjeto: vi.fn(),
  reativarProjeto: vi.fn(),
  criarProjeto: vi.fn(),
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
        <LocationProbe />
        <Routes>
          <Route path="/projetos" element={<ProjetosPage />}>
            <Route path="nova" element={<ProjetoDrawer />} />
          </Route>
          <Route path="/projetos/:id" element={<p>página editar projeto</p>} />
          <Route path="/tarefas" element={<p>página tarefas</p>} />
          <Route path="/capacidade" element={<p>página capacidade</p>} />
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
    vi.mocked(projetoApi.criarProjeto).mockReset()
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

  it('T3: card expõe Editar e Ver tarefas com projetoId na URL', async () => {
    renderLista()
    const card = (await screen.findByText('TCC')).closest('li')
    expect(card).toBeTruthy()

    const editar = within(card as HTMLElement).getByRole('link', { name: /^editar$/i })
    expect(editar).toHaveAttribute('href', '/projetos/p-tcc')

    const verTarefas = within(card as HTMLElement).getByRole('link', {
      name: /ver tarefas/i,
    })
    expect(verTarefas).toHaveAttribute('href', '/tarefas?projetoId=p-tcc')
  })

  it('T4: há caminho para /capacidade a partir da lista', async () => {
    renderLista()
    await screen.findByText('TCC')

    expect(screen.getByRole('link', { name: /^capacidade$/i })).toHaveAttribute(
      'href',
      '/capacidade',
    )

    const card = screen.getByText('TCC').closest('li') as HTMLElement
    expect(
      within(card).getByRole('link', { name: /reservar tempo/i }),
    ).toHaveAttribute('href', '/capacidade')
  })

  it('T5: empty arquivados oferece voltar a Ativos', async () => {
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })

    const user = userEvent.setup()
    renderLista('/projetos?status=ARQUIVADO')

    expect(await screen.findByText(/nenhum projeto arquivado/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^ver ativos$/i }))

    await waitFor(() => {
      expect(projetoApi.listarProjetos).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'ATIVO' }),
      )
    })
    expect(screen.getByRole('button', { name: 'Ativos' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('T6: com totalPages > 1, muda page na URL e chama API com a página', async () => {
    const pagina0 = {
      ...pagina,
      content: [{ ...pagina.content[0], id: 'p-1', nome: 'Projeto A' }],
      totalElements: 25,
      totalPages: 2,
      number: 0,
    }
    const pagina1 = {
      ...pagina,
      content: [{ ...pagina.content[0], id: 'p-2', nome: 'Projeto B' }],
      totalElements: 25,
      totalPages: 2,
      number: 1,
    }
    vi.mocked(projetoApi.listarProjetos)
      .mockResolvedValueOnce(pagina0)
      .mockResolvedValueOnce(pagina1)

    const user = userEvent.setup()
    renderLista()

    expect(await screen.findByText('Projeto A')).toBeInTheDocument()
    expect(projetoApi.listarProjetos).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
    )

    await user.click(screen.getByRole('button', { name: /^próxima$/i }))

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent('/projetos?page=1')
    })
    await waitFor(() => {
      expect(projetoApi.listarProjetos).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, size: 20, status: 'ATIVO' }),
      )
    })
    expect(await screen.findByText('Projeto B')).toBeInTheDocument()
  })

  it('T6: /projetos/nova abre drawer sobre a lista; criar navega para /projetos/:id', async () => {
    vi.mocked(projetoApi.criarProjeto).mockResolvedValue({
      ...pagina.content[0],
      id: 'p-novo',
      nome: 'Novo',
      descricao: null,
    })
    const user = userEvent.setup()
    renderLista('/projetos?status=ARQUIVADO')
    await screen.findByText('TCC')

    await user.click(screen.getByRole('link', { name: /^novo projeto$/i }))

    const drawer = await screen.findByRole('dialog', { name: /novo projeto/i })
    expect(screen.getByTestId('location')).toHaveTextContent('/projetos/nova?status=ARQUIVADO')
    expect(screen.getByText('TCC')).toBeInTheDocument()
    expect(within(drawer).getByLabelText(/^nome$/i)).toHaveFocus()

    await user.type(within(drawer).getByLabelText(/^nome$/i), 'Novo')
    await user.click(within(drawer).getByRole('button', { name: /criar projeto/i }))

    await waitFor(() => {
      expect(projetoApi.criarProjeto).toHaveBeenCalledWith({ nome: 'Novo', descricao: null })
    })
    expect(await screen.findByText('página editar projeto')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/projetos/p-novo')
    expect(screen.getByRole('status')).toHaveTextContent(/projeto criado/i)
  })

  it('cancelar o drawer de criação volta à lista e pede confirmação se houver dados', async () => {
    const user = userEvent.setup()
    renderLista()
    await screen.findByText('TCC')

    await user.click(screen.getByRole('link', { name: /^novo projeto$/i }))
    const drawer = await screen.findByRole('dialog', { name: /novo projeto/i })
    await user.type(within(drawer).getByLabelText(/^nome$/i), 'Rascunho')
    await user.click(within(drawer).getByRole('button', { name: /^cancelar$/i }))

    const confirmacao = await screen.findByRole('dialog', { name: /descartar projeto/i })
    await user.click(within(confirmacao).getByRole('button', { name: /^descartar$/i }))

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/projetos$/)
    })
    expect(screen.queryByRole('dialog', { name: /novo projeto/i })).not.toBeInTheDocument()
    expect(projetoApi.criarProjeto).not.toHaveBeenCalled()
  })
})
