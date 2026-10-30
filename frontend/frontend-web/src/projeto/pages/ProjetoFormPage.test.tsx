import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../../feedback/ToastProvider'
import * as projetoApi from '../api/projetoApi'
import { ProjetoFormPage } from './ProjetoFormPage'

vi.mock('../api/projetoApi', () => ({
  criarProjeto: vi.fn(),
  buscarProjeto: vi.fn(),
  atualizarProjeto: vi.fn(),
  arquivarProjeto: vi.fn(),
  reativarProjeto: vi.fn(),
}))

const projetoExistente = {
  id: 'p-tcc',
  nome: 'TCC',
  descricao: 'Trabalho de conclusão',
  status: 'ATIVO' as const,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

function renderCriar() {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={['/projetos/nova']}>
        <Routes>
          <Route path="/projetos/nova" element={<ProjetoFormPage />} />
          <Route path="/projetos/:id" element={<p>Hub do projeto</p>} />
          <Route path="/projetos" element={<p>Lista de projetos</p>} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function renderEditar(path = '/projetos/p-tcc') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/projetos/:id" element={<ProjetoFormPage />} />
          <Route path="/projetos" element={<p>Lista de projetos</p>} />
          <Route path="/tarefas" element={<p>Lista de tarefas</p>} />
          <Route path="/capacidade" element={<p>Capacidade</p>} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('ProjetoFormPage (F1)', () => {
  beforeEach(() => {
    vi.mocked(projetoApi.criarProjeto).mockReset()
    vi.mocked(projetoApi.buscarProjeto).mockReset()
    vi.mocked(projetoApi.atualizarProjeto).mockReset()
    vi.mocked(projetoApi.arquivarProjeto).mockReset()
    vi.mocked(projetoApi.reativarProjeto).mockReset()
  })

  it('T1: create e edit não usam layout auth-page', async () => {
    vi.mocked(projetoApi.buscarProjeto).mockResolvedValue(projetoExistente)

    renderCriar()
    expect(document.querySelector('main.auth-page')).not.toBeInTheDocument()
    expect(document.querySelector('main.projetos-page')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /novo projeto/i })).toBeInTheDocument()

    renderEditar()
    expect(await screen.findByDisplayValue('TCC')).toBeInTheDocument()
    expect(document.querySelectorAll('main.auth-page')).toHaveLength(0)
    expect(document.querySelectorAll('main.projetos-page').length).toBeGreaterThanOrEqual(1)
  })

  it('T1b: loading do edit usa Skeleton (aria-busy), não só texto', async () => {
    let resolveBusca!: (value: typeof projetoExistente) => void
    vi.mocked(projetoApi.buscarProjeto).mockReturnValue(
      new Promise((resolve) => {
        resolveBusca = resolve
      }),
    )

    renderEditar()

    const status = screen.getByRole('status')
    expect(status).toHaveAttribute('aria-busy', 'true')
    expect(screen.queryByText(/carregando projeto/i)).not.toBeInTheDocument()
    expect(document.querySelector('main.auth-page')).not.toBeInTheDocument()
    expect(document.querySelector('main.projetos-page')).toBeInTheDocument()

    resolveBusca(projetoExistente)
    expect(await screen.findByDisplayValue('TCC')).toBeInTheDocument()
  })

  it('T2: toast ao criar e redireciona para /projetos/:id', async () => {
    vi.mocked(projetoApi.criarProjeto).mockResolvedValue({
      ...projetoExistente,
      id: 'p-novo',
      nome: 'Novo',
      descricao: null,
    })

    const user = userEvent.setup()
    renderCriar()

    await user.type(screen.getByLabelText(/^nome$/i), 'Novo')
    await user.click(screen.getByRole('button', { name: /criar projeto/i }))

    await waitFor(() => {
      expect(projetoApi.criarProjeto).toHaveBeenCalledWith({
        nome: 'Novo',
        descricao: null,
      })
    })

    expect(await screen.findByRole('status')).toHaveTextContent(/projeto criado/i)
    expect(await screen.findByText(/hub do projeto/i)).toBeInTheDocument()
  })

  it('T2b: toast ao salvar e permanece no form (não volta à lista)', async () => {
    vi.mocked(projetoApi.buscarProjeto).mockResolvedValue(projetoExistente)
    vi.mocked(projetoApi.atualizarProjeto).mockResolvedValue({
      ...projetoExistente,
      nome: 'TCC atualizado',
    })

    const user = userEvent.setup()
    renderEditar()

    const nome = await screen.findByLabelText(/^nome$/i)
    await user.clear(nome)
    await user.type(nome, 'TCC atualizado')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    await waitFor(() => {
      expect(projetoApi.atualizarProjeto).toHaveBeenCalledWith('p-tcc', {
        nome: 'TCC atualizado',
        descricao: 'Trabalho de conclusão',
      })
    })

    expect(await screen.findByRole('status')).toHaveTextContent(/projeto atualizado|salvo/i)
    expect(screen.queryByText(/lista de projetos/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/^nome$/i)).toBeInTheDocument()
  })
})

describe('ProjetoFormPage (F4 hub)', () => {
  beforeEach(() => {
    vi.mocked(projetoApi.criarProjeto).mockReset()
    vi.mocked(projetoApi.buscarProjeto).mockReset()
    vi.mocked(projetoApi.atualizarProjeto).mockReset()
    vi.mocked(projetoApi.arquivarProjeto).mockReset()
    vi.mocked(projetoApi.reativarProjeto).mockReset()
  })

  it('T7: hub mostra Ver tarefas, Reservar tempo e Arquivar', async () => {
    vi.mocked(projetoApi.buscarProjeto).mockResolvedValue(projetoExistente)

    renderEditar()

    expect(await screen.findByRole('heading', { name: 'TCC' })).toBeInTheDocument()
    expect(document.querySelector('.status-ativo')).toHaveTextContent('Ativo')
    expect(document.querySelector('.projetos-subtitle')).toHaveTextContent(
      'Trabalho de conclusão',
    )

    expect(screen.getByRole('link', { name: /ver tarefas/i })).toHaveAttribute(
      'href',
      '/tarefas?projetoId=p-tcc',
    )
    expect(screen.getByRole('link', { name: /reservar tempo/i })).toHaveAttribute(
      'href',
      '/capacidade',
    )
    expect(screen.getByRole('button', { name: /^arquivar$/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/^nome$/i)).toBeInTheDocument()
  })

  it('T7b: Arquivar pede confirm, chama API e passa a Reativar', async () => {
    vi.mocked(projetoApi.buscarProjeto).mockResolvedValue(projetoExistente)
    vi.mocked(projetoApi.arquivarProjeto).mockResolvedValue({
      ...projetoExistente,
      status: 'ARQUIVADO',
    })

    const user = userEvent.setup()
    renderEditar()
    await screen.findByRole('heading', { name: 'TCC' })

    await user.click(screen.getByRole('button', { name: /^arquivar$/i }))
    expect(projetoApi.arquivarProjeto).not.toHaveBeenCalled()

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /arquivar projeto/i })).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /^arquivar$/i }))

    await waitFor(() => {
      expect(projetoApi.arquivarProjeto).toHaveBeenCalledWith('p-tcc')
    })
    expect(await screen.findByRole('button', { name: /^reativar$/i })).toBeInTheDocument()
    expect(document.querySelector('.status-arquivado')).toHaveTextContent('Arquivado')
    expect(await screen.findByRole('status')).toHaveTextContent(/projeto arquivado/i)
  })

  it('T7c: hub arquivado oferece Reativar', async () => {
    vi.mocked(projetoApi.buscarProjeto).mockResolvedValue({
      ...projetoExistente,
      status: 'ARQUIVADO',
    })
    vi.mocked(projetoApi.reativarProjeto).mockResolvedValue(projetoExistente)

    const user = userEvent.setup()
    renderEditar()

    await screen.findByRole('heading', { name: 'TCC' })
    expect(screen.getByRole('button', { name: /^reativar$/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^arquivar$/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^reativar$/i }))

    await waitFor(() => {
      expect(projetoApi.reativarProjeto).toHaveBeenCalledWith('p-tcc')
    })
    expect(await screen.findByRole('button', { name: /^arquivar$/i })).toBeInTheDocument()
  })
})
