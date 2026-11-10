import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { MemoryRouter, Navigate, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from '../auth/api/meApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import { TarefasPage } from '../tarefa/pages/TarefasPage'
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
  listarTarefas: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
  iniciarTarefaSessao: vi.fn(),
  concluirTarefaDireto: vi.fn(),
}))

vi.mock('../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../execucao/api/execucaoApi')>()
  return {
    ...actual,
    listarExecucoes: vi.fn().mockResolvedValue({ planoAprovado: null, itens: [] }),
    concluirExecucao: vi.fn(),
    abortarExecucao: vi.fn(),
  }
})

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

function PaginaPlano() {
  const { assinarCriada } = useQuickAdd()
  useEffect(() => assinarCriada(onTarefaCriada), [assinarCriada])
  return (
    <div>
      <h1>página plano</h1>
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
            <Route path="/" element={<Navigate to="/tarefas" replace />} />
            <Route path="/plano" element={<PaginaPlano />} />
            <Route path="/tarefas" element={<h1>página tarefas</h1>} />
            <Route path="/habitos" element={<p>página hábitos</p>} />
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
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })
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

  it('renderiza links Tarefas, Projetos, Plano, Hábitos, Capacidade, Calendário e Histórico', () => {
    renderShell('/tarefas')

    expect(screen.getAllByRole('link', { name: 'Tarefas' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Projetos' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Plano' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Hábitos' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Capacidade' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Calendário' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Histórico' }).length).toBeGreaterThan(0)
    expect(screen.getByText('página tarefas')).toBeInTheDocument()
  })

  it('marca a rota ativa com aria-current="page"', () => {
    renderShell('/tarefas')

    const tarefas = screen.getAllByRole('link', { name: 'Tarefas' })
    expect(tarefas.every((link) => link.getAttribute('aria-current') === 'page')).toBe(true)

    const plano = screen.getAllByRole('link', { name: 'Plano' })
    expect(plano.every((link) => link.getAttribute('aria-current') !== 'page')).toBe(true)
  })

  it('T1: sidebar ordena Tarefas/Projetos antes de Plano/Hábitos; terciários presentes', () => {
    renderShell('/tarefas')

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
      'Tarefas',
      'Projetos',
      'Plano',
      'Hábitos',
      'Capacidade',
      'Calendário',
      'Histórico',
      'Tags',
    ])
    expect(within(bottom).getAllByRole('link').map((l) => l.textContent)).toEqual([
      'Tarefas',
      'Projetos',
      'Plano',
      'Hábitos',
    ])
    expect(within(bottom).getByRole('button', { name: 'Mais' })).toBeInTheDocument()
  })

  it('T2: bottom tem Tarefas/Projetos/Plano/Hábitos + Mais; Mais tem Capacidade/Calendário/Histórico sem Projetos', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    const mais = screen.getByRole('button', { name: 'Mais' })
    await user.click(mais)

    const sheet = screen.getByRole('dialog', { name: 'Mais opções' })
    expect(within(sheet).queryByRole('link', { name: 'Projetos' })).not.toBeInTheDocument()
    const capacidade = within(sheet).getByRole('link', { name: 'Capacidade' })
    expect(within(sheet).getByRole('link', { name: 'Calendário' })).toBeInTheDocument()
    expect(within(sheet).getByRole('link', { name: 'Histórico' })).toBeInTheDocument()
    expect(within(sheet).getByRole('link', { name: 'Perfil' })).toBeInTheDocument()
    expect(within(sheet).getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(within(sheet).getByRole('radiogroup', { name: 'Tema' })).toBeInTheDocument()

    capacidade.focus()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Mais opções' })).not.toBeInTheDocument()
    expect(mais).toHaveFocus()
  })

  it('T3: rota / redireciona para /tarefas', async () => {
    renderShell('/')

    expect(await screen.findByText('página tarefas')).toBeInTheDocument()
  })

  it('T4: /plano renderiza o conteúdo do plano do dia', () => {
    renderShell('/plano')

    expect(screen.getByRole('heading', { name: 'página plano' })).toBeInTheDocument()
  })

  it('"Nova tarefa" do topo abre o quick add', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    await user.click(screen.getByRole('button', { name: 'Nova tarefa' }))
    expect(await screen.findByRole('heading', { name: 'Nova tarefa' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('heading', { name: 'Nova tarefa' })).not.toBeInTheDocument()
  })

  it('T5: em /tarefas, atalho N foca o inline (não abre dialog)', async () => {
    const user = userEvent.setup()
    render(
      <ThemeProvider>
        <MemoryRouter initialEntries={['/tarefas']}>
          <DocumentTitle />
          <Routes>
            <Route element={<AppShell />}>
              <Route path="/tarefas" element={<TarefasPage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </ThemeProvider>,
    )

    expect(await screen.findByRole('textbox', { name: /adicionar tarefa/i })).toBeInTheDocument()
    await user.keyboard('n')

    expect(screen.getByRole('textbox', { name: /adicionar tarefa/i })).toHaveFocus()
    expect(screen.queryByRole('dialog', { name: /nova tarefa/i })).not.toBeInTheDocument()
  })

  it('T6: fora de /tarefas, atalho N abre dialog mínimo', async () => {
    const user = userEvent.setup()
    renderShell('/plano')

    await user.keyboard('n')

    const dialog = await screen.findByRole('dialog', { name: /nova tarefa/i })
    expect(within(dialog).getByLabelText(/^título$/i)).toBeInTheDocument()
    expect(within(dialog).queryByLabelText(/duração/i)).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText(/prioridade/i)).not.toBeInTheDocument()
  })

  it('criar tarefa pelo shell notifica a página inscrita', async () => {
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
    renderShell('/plano')

    await user.click(screen.getByRole('button', { name: 'Nova tarefa' }))
    const dialog = await screen.findByRole('dialog', { name: 'Nova tarefa' })
    await user.type(within(dialog).getByLabelText(/^título$/i), 'Ler docs')
    await user.click(within(dialog).getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(onTarefaCriada).toHaveBeenCalledOnce()
    })
  })

  it('menu de conta mostra iniciais, troca o tema e Sair chama logout', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    const gatilho = await screen.findByRole('button', { name: /menu da conta: ana souza/i })
    expect(gatilho).toHaveTextContent('AS')
    await user.click(gatilho)

    expect(screen.getByRole('link', { name: 'Perfil' })).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /escuro/i }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')

    await user.click(screen.getByRole('button', { name: 'Sair' }))
    expect(logoutMock).toHaveBeenCalledOnce()
  })

  it('Esc fecha o menu de conta e devolve o foco ao gatilho', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    const gatilho = await screen.findByRole('button', { name: /menu da conta/i })
    await user.click(gatilho)
    expect(screen.getByRole('group', { name: 'Conta' })).toBeInTheDocument()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('group', { name: 'Conta' })).not.toBeInTheDocument()
    expect(gatilho).toHaveFocus()
  })

  it('ao navegar, o foco vai para o h1 da nova página', async () => {
    const user = userEvent.setup()
    renderShell('/plano')

    const [linkSidebar] = screen.getAllByRole('link', { name: 'Tarefas' })
    await user.click(linkSidebar)

    const titulo = await screen.findByRole('heading', { level: 1, name: 'página tarefas' })
    expect(titulo).toHaveFocus()
  })

  it('topo mostra o título da página atual', () => {
    renderShell('/capacidade')

    expect(screen.getByRole('banner')).toHaveTextContent('Capacidade')
  })

  it('T6: topo em /plano mostra Plano', () => {
    renderShell('/plano')

    expect(screen.getByRole('banner')).toHaveTextContent('Plano')
  })

  it('XP compacto reflete nível e barra a partir de /me', async () => {
    renderShell('/tarefas')

    expect(await screen.findByLabelText('Nível 2')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: /2 de 20 XP/i })).toBeInTheDocument()
    expect(screen.getByTitle('2 / 20 XP')).toBeInTheDocument()
    await waitFor(() => {
      expect(meApi.buscarMe).toHaveBeenCalled()
    })
  })

  it('com foco em input, atalho N não abre quick add', async () => {
    const user = userEvent.setup()
    renderShell('/plano')

    await user.click(screen.getByLabelText('Campo título'))
    await user.keyboard('n')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('T5: sequência G H navega para Plano (fora de campo)', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    expect(screen.getByText('página tarefas')).toBeInTheDocument()

    await user.keyboard('gh')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'página plano' })).toBeInTheDocument()
    })
  })

  it('T5: ? abre dialog de ajuda descrevendo Ir para Plano', async () => {
    const user = userEvent.setup()
    renderShell('/tarefas')

    await user.keyboard('?')

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent(/atalhos de teclado/i)
    expect(dialog).toHaveTextContent(/nova tarefa/i)
    expect(dialog).toHaveTextContent(/G H/i)
    expect(dialog).toHaveTextContent(/Ir para Plano/i)
  })

  it('define document.title conforme a rota', async () => {
    renderShell('/historico')

    await waitFor(() => {
      expect(document.title).toBe('Histórico · Controle de Rotina')
    })
  })
})
