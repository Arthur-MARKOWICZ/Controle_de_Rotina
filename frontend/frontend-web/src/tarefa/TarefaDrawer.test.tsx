import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import { QuickAddProvider } from '../layout/QuickAddContext'
import * as projetoApi from '../projeto/api/projetoApi'
import * as tarefaApi from './api/tarefaApi'
import type { TarefaResponse } from './api/tarefaApi'
import { TarefasPage } from './pages/TarefasPage'
import { TarefaDrawer } from './TarefaDrawer'

vi.mock('./api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  criarTarefa: vi.fn(),
  buscarTarefa: vi.fn(),
  atualizarTarefa: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
}))

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

const projetosAtivos = {
  content: [
    {
      id: 'proj-tcc',
      nome: 'TCC',
      descricao: null,
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

const tarefa: TarefaResponse = {
  id: 't-1',
  titulo: 'Estudar',
  descricao: 'Spring',
  prioridade: 'ALTA',
  prazo: '2030-06-01T15:00:00Z',
  status: 'PENDENTE',
  duracaoMinutos: 240,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

function pagina(content: TarefaResponse[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 20 }
}

function prazoFuturoLocal(): string {
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function Local() {
  const location = useLocation()
  return <p data-testid="local">{`${location.pathname}${location.search}`}</p>
}

function renderApp(path: string) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <QuickAddProvider>
          <Routes>
            <Route path="/tarefas" element={<TarefasPage />}>
              <Route path="nova" element={<TarefaDrawer />} />
              <Route path=":id" element={<TarefaDrawer />} />
            </Route>
          </Routes>
          <Local />
        </QuickAddProvider>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function drawer(nome: RegExp = /tarefa/i) {
  return screen.getByRole('dialog', { name: nome })
}

async function esperarDrawerFechar() {
  await waitFor(() => {
    expect(screen.queryByRole('dialog', { name: /(editar|nova) tarefa/i })).not.toBeInTheDocument()
  })
}

describe('TarefaDrawer', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tarefaApi.buscarTarefa).mockReset()
    vi.mocked(tarefaApi.atualizarTarefa).mockReset()
    vi.mocked(tarefaApi.excluirTarefa).mockReset()
    vi.mocked(tarefaApi.restaurarTarefa).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(pagina([tarefa]))
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(tarefa)
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue(projetosAtivos)
  })

  describe('rotas', () => {
    it('T2: deep link /tarefas/:id renderiza a lista e o drawer com os dados da tarefa', async () => {
      renderApp('/tarefas/t-1')

      expect(await screen.findByDisplayValue('Estudar')).toBeInTheDocument()
      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(await screen.findByText('Estudar', { selector: '.tarefa-card span' })).toBeInTheDocument()
      expect(tarefaApi.buscarTarefa).toHaveBeenCalledWith('t-1')
    })

    it('T3: fechar o drawer volta a /tarefas com os mesmos filtros', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1?visao=pendentes&prioridade=ALTA')

      await screen.findByDisplayValue('Estudar')
      await user.click(within(drawer()).getByRole('button', { name: 'Fechar' }))

      await esperarDrawerFechar()
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas?visao=pendentes&prioridade=ALTA')
    })

    it('T3: abrir pelo card e fechar mantém a query string da lista', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas?prioridade=ALTA')

      await user.click(await screen.findByRole('link', { name: /estudar/i }))
      expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/t-1?prioridade=ALTA')
      await screen.findByDisplayValue('Estudar')

      await user.click(within(drawer()).getByRole('button', { name: /^cancelar$/i }))

      await esperarDrawerFechar()
      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas\?prioridade=ALTA$/)
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(1)
    })

    it('T4: fechar com alterações pede confirmação; continuar editando mantém o drawer', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.type(screen.getByLabelText(/^título$/i), ' mais')
      await user.click(within(drawer()).getByRole('button', { name: 'Fechar' }))

      const confirmacao = screen.getByRole('dialog', { name: /descartar alterações/i })
      await user.click(within(confirmacao).getByRole('button', { name: /continuar editando/i }))

      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/^título$/i)).toHaveValue('Estudar mais')

      await user.click(within(drawer()).getByRole('button', { name: 'Fechar' }))
      await user.click(
        within(screen.getByRole('dialog', { name: /descartar alterações/i })).getByRole('button', {
          name: /^descartar$/i,
        }),
      )
      await esperarDrawerFechar()
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
    })

    it('fechar sem alterações não pede confirmação', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(within(drawer()).getByRole('button', { name: 'Fechar' }))

      expect(screen.queryByRole('dialog', { name: /descartar/i })).not.toBeInTheDocument()
      await esperarDrawerFechar()
    })
  })

  describe('salvar e criar', () => {
    it('T5: salvar edição atualiza o item na lista antes da leitura silenciosa, sem skeleton', async () => {
      vi.mocked(tarefaApi.listarTarefas)
        .mockResolvedValueOnce(pagina([tarefa]))
        .mockReturnValueOnce(new Promise(() => {}))
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...tarefa,
        titulo: 'Estudar mais',
        status: 'EM_PROGRESSO',
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      expect(screen.getByLabelText(/^status$/i)).toBeInTheDocument()
      await user.clear(screen.getByLabelText(/^título$/i))
      await user.type(screen.getByLabelText(/^título$/i), 'Estudar mais')
      await user.selectOptions(screen.getByLabelText(/^status$/i), 'EM_PROGRESSO')
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
          't-1',
          expect.objectContaining({ titulo: 'Estudar mais', status: 'EM_PROGRESSO' }),
        )
      })
      await esperarDrawerFechar()
      expect(await screen.findByText('Estudar mais', { selector: '.tarefa-card span' })).toBeInTheDocument()
      expect(screen.getByText('Tarefa salva')).toBeInTheDocument()
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(2)
      expect(document.querySelector('.skeleton')).not.toBeInTheDocument()
      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas$/)
    })

    it('no create não exibe status, cria tarefa válida, fecha e faz leitura silenciosa', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({ ...tarefa, id: 't-9', titulo: 'Novo' })
      const user = userEvent.setup()
      renderApp('/tarefas/nova')

      await screen.findByText('Estudar', { selector: '.tarefa-card span' })
      expect(drawer(/nova tarefa/i)).toBeInTheDocument()
      expect(screen.queryByLabelText(/^status$/i)).not.toBeInTheDocument()

      await user.type(screen.getByLabelText(/^título$/i), 'Estudar')
      await user.type(screen.getByLabelText(/descrição/i), 'Spring Boot')
      await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'MEDIA')
      await user.type(screen.getByLabelText(/prazo \(opcional\)/i), prazoFuturoLocal())
      await user.type(screen.getByLabelText(/duração/i), '120')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({
            titulo: 'Estudar',
            descricao: 'Spring Boot',
            prioridade: 'MEDIA',
            duracaoMinutos: 120,
          }),
        )
      })
      await esperarDrawerFechar()
      expect(screen.getByText('Tarefa criada')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(2)
    })

    it('permite criar tarefa sem prazo', async () => {
      vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(tarefa)
      const user = userEvent.setup()
      renderApp('/tarefas/nova')

      await user.type(await screen.findByLabelText(/^título$/i), 'Sem prazo')
      await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'MEDIA')
      await user.type(screen.getByLabelText(/duração/i), '60')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

      await waitFor(() => {
        expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
          expect.objectContaining({
            titulo: 'Sem prazo',
            prioridade: 'MEDIA',
            prazo: null,
            duracaoMinutos: 60,
          }),
        )
      })
    })

    it('mostra erro de validação quando título está vazio', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/nova')

      await user.type(await screen.findByLabelText(/descrição/i), 'Algo')
      await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'BAIXA')
      await user.type(screen.getByLabelText(/prazo \(opcional\)/i), prazoFuturoLocal())
      await user.type(screen.getByLabelText(/duração/i), '60')
      await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
      expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
      expect(drawer(/nova tarefa/i)).toBeInTheDocument()
    })

    it('bloqueia ATRASADA com prazo futuro na validação local', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.selectOptions(screen.getByLabelText(/^status$/i), 'ATRASADA')
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/ATRASADA/i)
      expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
    })

    it('mostra detalhe de erro da API no save e mantém o drawer aberto', async () => {
      vi.mocked(tarefaApi.atualizarTarefa).mockRejectedValue(
        new ApiError(400, 'O prazo não pode estar no passado.'),
      )
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.clear(screen.getByLabelText(/^título$/i))
      await user.type(screen.getByLabelText(/^título$/i), 'Estudar API')
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/prazo não pode estar no passado/i)
      expect(drawer(/editar tarefa/i)).toBeInTheDocument()
    })

    it('T6: quick-add "Mais detalhes" abre /tarefas/nova com o título preenchido', async () => {
      vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(pagina([]))
      const user = userEvent.setup()
      renderApp('/tarefas')

      await user.click(await screen.findByRole('button', { name: /nova tarefa/i }))
      const quickAdd = screen.getByRole('dialog', { name: /nova tarefa/i })
      await user.type(within(quickAdd).getByLabelText(/^título$/i), 'Ler artigo')
      await user.click(within(quickAdd).getByRole('button', { name: /mais detalhes/i }))

      expect(screen.getByTestId('local')).toHaveTextContent(/^\/tarefas\/nova$/)
      expect(drawer(/nova tarefa/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/^título$/i)).toHaveValue('Ler artigo')
      expect(screen.getByLabelText(/descrição/i)).toBeInTheDocument()
    })
  })

  describe('excluir, restaurar e projeto', () => {
    it('excluir no drawer: cancelar confirmação não chama API', async () => {
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))
      const dialog = screen.getByRole('dialog', { name: /excluir tarefa/i })
      await user.click(within(dialog).getByRole('button', { name: /^cancelar$/i }))

      expect(tarefaApi.excluirTarefa).not.toHaveBeenCalled()
      expect(screen.getByRole('button', { name: /excluir tarefa/i })).toBeInTheDocument()
    })

    it('confirma exclusão e permanece no drawer com status excluída', async () => {
      vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))
      const dialog = screen.getByRole('dialog', { name: /excluir tarefa/i })
      await user.click(within(dialog).getByRole('button', { name: /^excluir$/i }))

      await waitFor(() => {
        expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-1')
      })
      expect(
        await within(drawer(/editar tarefa/i)).findByText(/esta tarefa está excluída/i),
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /restaurar tarefa/i })).toBeInTheDocument()
    })

    it('mostra restaurar quando a tarefa está deletada e restaura', async () => {
      const deletada = { ...tarefa, deletada: true, deletedAt: '2026-02-01T10:00:00Z' }
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue(deletada)
      vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue(tarefa)
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      expect(
        await within(drawer(/editar tarefa/i)).findByText(/esta tarefa está excluída/i),
      ).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /excluir tarefa/i })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /restaurar tarefa/i }))

      await waitFor(() => {
        expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-1')
      })
      expect(await screen.findByRole('button', { name: /^salvar$/i })).toBeInTheDocument()
    })

    it('Desvincular envia PATCH com projetoId: null', async () => {
      vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
        ...tarefa,
        projetoId: 'proj-tcc',
        projetoNome: 'TCC',
      })
      vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
        ...tarefa,
        projetoId: null,
        projetoNome: null,
      })
      const user = userEvent.setup()
      renderApp('/tarefas/t-1')

      await screen.findByDisplayValue('Estudar')
      await user.click(screen.getByRole('button', { name: /^desvincular$/i }))

      await waitFor(() => {
        expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith('t-1', { projetoId: null })
      })
    })
  })
})
