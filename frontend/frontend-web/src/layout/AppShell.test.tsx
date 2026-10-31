import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from '../auth/api/meApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import { ThemeProvider } from '../theme/ThemeProvider'
import { AppShell } from './AppShell'
import { DocumentTitle } from './DocumentTitle'
import { useQuickAdd } from './QuickAddContext'

const { logoutMock } = vi.hoisted(() => ({ logoutMock: vi.fn() }))

vi.mock('../auth/session/AuthContext', () => ({
  useAuth: () => ({ logout: logoutMock }),
}))

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn(),
  atualizarMe: vi.fn(),
}))

vi.mock('../tarefa/api/tarefaApi', () => ({
  criarTarefa: vi.fn(),
}))

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
  }),
}))

const onTarefaCriada = vi.fn()

function PaginaHoje() {
  const { assinarCriada } = useQuickAdd()
  useEffect(() => assinarCriada(onTarefaCriada), [assinarCriada])
  return (
    <div>
      <p>página hoje</p>
      <label>
        Título
        <input aria-label="Campo título" />
      </label>
    </div>
  )
}

function renderShell(path: string) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <DocumentTitle />
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<PaginaHoje />} />
            <Route path="/tarefas" element={<h1>página tarefas</h1>} />
            <Route path="/projetos" element={<p>página projetos</p>} />
            <Route path="/calendario" element={<p>página calendário</p>} />
            <Route path="/capacidade" element={<p>página capacidade</p>} />
            <Route path="/historico" element={<p>página histórico</p>} />
            <Route path="/perfil" element={<p>página perfil</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('AppShell', () => {
  beforeEach(() => {
    logoutMock.mockReset()
    onTarefaCriada.mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana Souza',
      email: 'ana@email.com',
      xpTotal: 12,
      nivel: 2,
      xpNoNivel: 2,
      xpParaProximoNivel: 20,
    })
    document.title = ''
    document.documentElement.removeAttribute('data-theme')
    localStorage.clear()
  })

  it('renderiza links Hoje, Tarefas, Projetos, Calendário, Capacidade e Histórico', () => {
    renderShell('/')

    expect(screen.getAllByRole('link', { name: 'Hoje' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Tarefas' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Projetos' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Calendário' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Capacidade' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Histórico' }).length).toBeGreaterThan(0)
    expect(screen.getByText('página hoje')).toBeInTheDocument()
  })

  it('marca a rota ativa com aria-current="page"', () => {
    renderShell('/tarefas')

    const tarefas = screen.getAllByRole('link', { name: 'Tarefas' })
    expect(tarefas.every((link) => link.getAttribute('aria-current') === 'page')).toBe(true)

    const hoje = screen.getAllByRole('link', { name: 'Hoje' })
    expect(hoje.every((link) => link.getAttribute('aria-current') !== 'page')).toBe(true)
  })

  it('T1: itens de nav têm nome pelo rótulo e ícones fora da árvore acessível', () => {
    renderShell('/')

    const navs = screen.getAllByRole('navigation', { name: 'Principal' })
    expect(navs).toHaveLength(2)
    for (const nav of navs) {
      for (const link of within(nav).getAllByRole('link')) {
        expect(link).toHaveAccessibleName(link.textContent ?? '')
        const icone = link.querySelector('svg')
        expect(icone).toHaveAttribute('aria-hidden', 'true')
      }
    }
    const [sidebar, bottom] = navs
    expect(within(sidebar).getAllByRole('link').map((l) => l.textContent)).toEqual([
      'Hoje',
      'Tarefas',
      'Projetos',
      'Calendário',
      'Capacidade',
      'Histórico',
    ])
    expect(within(bottom).getAllByRole('link').map((l) => l.textContent)).toEqual([
      'Hoje',
      'Tarefas',
      'Calendário',
      'Capacidade',
    ])
    expect(within(bottom).getByRole('button', { name: 'Mais' })).toBeInTheDocument()
  })

  it('T2: "Mais" abre sheet com Projetos, Histórico, Perfil e Sair; Esc fecha e devolve o foco', async () => {
    const user = userEvent.setup()
    renderShell('/')

    const mais = screen.getByRole('button', { name: 'Mais' })
    await user.click(mais)

    const sheet = screen.getByRole('dialog', { name: 'Mais opções' })
    const projetos = within(sheet).getByRole('link', { name: 'Projetos' })
    expect(within(sheet).getByRole('link', { name: 'Histórico' })).toBeInTheDocument()
    expect(within(sheet).getByRole('link', { name: 'Perfil' })).toBeInTheDocument()
    expect(within(sheet).getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(within(sheet).getByRole('radiogroup', { name: 'Tema' })).toBeInTheDocument()

    projetos.focus()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Mais opções' })).not.toBeInTheDocument()
    expect(mais).toHaveFocus()
  })

  it('T3: "Nova tarefa" do topo e o atalho N abrem o mesmo quick add', async () => {
    const user = userEvent.setup()
    renderShell('/')

    await user.click(screen.getByRole('button', { name: 'Nova tarefa' }))
    expect(await screen.findByRole('heading', { name: 'Nova tarefa' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('heading', { name: 'Nova tarefa' })).not.toBeInTheDocument()

    await user.keyboard('n')
    expect(await screen.findByRole('heading', { name: 'Nova tarefa' })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { name: 'Nova tarefa' })).toHaveLength(1)
  })

  it('T4: criar tarefa pelo shell notifica a página inscrita', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Ler docs',
      descricao: '',
      prioridade: 'MEDIA',
      prazo: null,
      status: 'PENDENTE',
      duracaoMinutos: 60,
      createdAt: '2026-10-28T12:00:00Z',
      updatedAt: '2026-10-28T12:00:00Z',
      deletedAt: null,
      deletada: false,
    })
    const user = userEvent.setup()
    renderShell('/')

    await user.click(screen.getByRole('button', { name: 'Nova tarefa' }))
    const dialog = await screen.findByRole('dialog', { name: 'Nova tarefa' })
    await user.type(within(dialog).getByLabelText(/^título$/i), 'Ler docs')
    await user.click(within(dialog).getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(onTarefaCriada).toHaveBeenCalledOnce()
    })
  })

  it('T5: menu de conta mostra iniciais, troca o tema e Sair chama logout', async () => {
    const user = userEvent.setup()
    renderShell('/')

    const gatilho = await screen.findByRole('button', { name: /menu da conta: ana souza/i })
    expect(gatilho).toHaveTextContent('AS')
    await user.click(gatilho)

    expect(screen.getByRole('link', { name: 'Perfil' })).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /escuro/i }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')

    await user.click(screen.getByRole('button', { name: 'Sair' }))
    expect(logoutMock).toHaveBeenCalledOnce()
  })

  it('T5: Esc fecha o menu de conta e devolve o foco ao gatilho', async () => {
    const user = userEvent.setup()
    renderShell('/')

    const gatilho = await screen.findByRole('button', { name: /menu da conta/i })
    await user.click(gatilho)
    expect(screen.getByRole('group', { name: 'Conta' })).toBeInTheDocument()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('group', { name: 'Conta' })).not.toBeInTheDocument()
    expect(gatilho).toHaveFocus()
  })

  it('T6: ao navegar, o foco vai para o h1 da nova página', async () => {
    const user = userEvent.setup()
    renderShell('/')

    const [linkSidebar] = screen.getAllByRole('link', { name: 'Tarefas' })
    await user.click(linkSidebar)

    const titulo = await screen.findByRole('heading', { level: 1, name: 'página tarefas' })
    expect(titulo).toHaveFocus()
  })

  it('topo mostra o título da página atual', () => {
    renderShell('/capacidade')

    expect(screen.getByRole('banner')).toHaveTextContent('Capacidade')
  })

  it('XP compacto reflete nível e barra a partir de /me', async () => {
    renderShell('/')

    expect(await screen.findByLabelText('Nível 2')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: /2 de 20 XP/i })).toBeInTheDocument()
    expect(screen.getByTitle('2 / 20 XP')).toBeInTheDocument()
    await waitFor(() => {
      expect(meApi.buscarMe).toHaveBeenCalled()
    })
  })

  it('com foco em input, atalho N não abre quick add', async () => {
    const user = userEvent.setup()
    renderShell('/')

    await user.click(screen.getByLabelText('Campo título'))
    await user.keyboard('n')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('sequência G H navega para Hoje (fora de campo)', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    expect(screen.getByText('página tarefas')).toBeInTheDocument()

    await user.keyboard('gh')

    await waitFor(() => {
      expect(screen.getByText('página hoje')).toBeInTheDocument()
    })
  })

  it('? abre dialog de ajuda listando atalhos', async () => {
    const user = userEvent.setup()
    renderShell('/')

    await user.keyboard('?')

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent(/atalhos de teclado/i)
    expect(dialog).toHaveTextContent(/nova tarefa/i)
    expect(dialog).toHaveTextContent(/G H/i)
    expect(dialog).toHaveTextContent(/Ir para Hoje/i)
  })

  it('define document.title conforme a rota', async () => {
    renderShell('/historico')

    await waitFor(() => {
      expect(document.title).toBe('Histórico · Controle de Rotina')
    })
  })
})
