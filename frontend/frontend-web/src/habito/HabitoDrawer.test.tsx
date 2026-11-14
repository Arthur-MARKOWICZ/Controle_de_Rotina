import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as habitoApi from './api/habitoApi'
import type { HabitoResponse } from './api/habitoApi'
import { HabitoDrawer } from './HabitoDrawer'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

vi.mock('./api/habitoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/habitoApi')>()
  return {
    ...actual,
    criarHabito: vi.fn(),
    buscarHabito: vi.fn(),
    atualizarHabito: vi.fn(),
    excluirHabito: vi.fn(),
    restaurarHabito: vi.fn(),
    buscarHistoricoHabito: vi.fn(),
  }
})

const habito: HabitoResponse = {
  id: 'h-1',
  titulo: 'Correr',
  descricao: null,
  duracaoMinutos: 30,
  diasSemana: ['SEGUNDA'],
  horarioPreferencial: null,
  status: 'ATIVO',
  streakAtual: 1,
  melhorStreak: 3,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

const aoCriar = vi.fn()
const aoAtualizar = vi.fn()

function ListaFake() {
  return <Outlet context={{ aoCriar, aoAtualizar }} />
}

function renderDrawer(path: string) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/habitos" element={<ListaFake />}>
            <Route path="nova" element={<HabitoDrawer />} />
            <Route path=":id" element={<HabitoDrawer />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('HabitoDrawer', () => {
  beforeEach(() => {
    aoCriar.mockReset()
    aoAtualizar.mockReset()
    vi.mocked(habitoApi.criarHabito).mockReset()
    vi.mocked(habitoApi.buscarHabito).mockReset()
    vi.mocked(habitoApi.atualizarHabito).mockReset()
    vi.mocked(habitoApi.excluirHabito).mockReset()
    vi.mocked(habitoApi.restaurarHabito).mockReset()
    vi.mocked(habitoApi.buscarHistoricoHabito).mockReset().mockResolvedValue([])
  })

  it('cria hábito válido e notifica a lista', async () => {
    vi.mocked(habitoApi.criarHabito).mockResolvedValue(habito)
    const user = userEvent.setup()
    renderDrawer('/habitos/nova')

    await user.type(screen.getByLabelText('Título'), 'Correr')
    await user.click(screen.getByRole('checkbox', { name: /segunda/i }))
    await user.click(screen.getByRole('button', { name: /criar hábito/i }))

    await waitFor(() => {
      expect(habitoApi.criarHabito).toHaveBeenCalled()
    })
    expect(aoCriar).toHaveBeenCalledWith(habito)
    expect(await screen.findByText('Hábito criado')).toBeInTheDocument()
  })

  it('não chama a API quando a validação falha (nenhum dia)', async () => {
    const user = userEvent.setup()
    renderDrawer('/habitos/nova')

    await user.type(screen.getByLabelText('Título'), 'Meditar')
    await user.click(screen.getByRole('button', { name: /criar hábito/i }))

    expect(await screen.findByText(/pelo menos um dia/i)).toBeInTheDocument()
    expect(habitoApi.criarHabito).not.toHaveBeenCalled()
    expect(aoCriar).not.toHaveBeenCalled()
  })

  it('carrega hábito existente para edição', async () => {
    vi.mocked(habitoApi.buscarHabito).mockResolvedValue(habito)
    renderDrawer('/habitos/h-1')

    expect(await screen.findByDisplayValue('Correr')).toBeInTheDocument()
    expect(screen.getByText(/sequência atual: 1/i)).toBeInTheDocument()
  })

  it('mostra erro e não preenche o form quando buscar falha', async () => {
    vi.mocked(habitoApi.buscarHabito).mockRejectedValue(new ApiError(404, 'Hábito não encontrado'))
    renderDrawer('/habitos/h-sumiu')

    expect(await screen.findByText('Hábito não encontrado')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('Correr')).not.toBeInTheDocument()
  })

  it('salva edição e atualiza o contexto', async () => {
    vi.mocked(habitoApi.buscarHabito).mockResolvedValue(habito)
    vi.mocked(habitoApi.atualizarHabito).mockResolvedValue({ ...habito, titulo: 'Correr 5km' })
    const user = userEvent.setup()
    renderDrawer('/habitos/h-1')

    const titulo = await screen.findByLabelText('Título')
    await user.clear(titulo)
    await user.type(titulo, 'Correr 5km')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    await waitFor(() => {
      expect(habitoApi.atualizarHabito).toHaveBeenCalledWith(
        'h-1',
        expect.objectContaining({ titulo: 'Correr 5km' }),
      )
    })
    expect(aoAtualizar).toHaveBeenCalled()
  })

  it('mostra erro de API ao salvar e preserva os valores digitados', async () => {
    vi.mocked(habitoApi.buscarHabito).mockResolvedValue(habito)
    vi.mocked(habitoApi.atualizarHabito).mockRejectedValue(new ApiError(409, 'Título duplicado'))
    const user = userEvent.setup()
    renderDrawer('/habitos/h-1')

    const titulo = await screen.findByLabelText('Título')
    await user.clear(titulo)
    await user.type(titulo, 'Duplicado')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    expect(await screen.findByText('Título duplicado')).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveValue('Duplicado')
    expect(aoAtualizar).not.toHaveBeenCalled()
  })

  it('não exclui quando o usuário cancela a confirmação', async () => {
    vi.mocked(habitoApi.buscarHabito).mockResolvedValue(habito)
    const user = userEvent.setup()
    renderDrawer('/habitos/h-1')

    await screen.findByDisplayValue('Correr')
    await user.click(screen.getByRole('button', { name: /excluir hábito/i }))
    const dialogo = await screen.findByRole('dialog', { name: /excluir hábito/i })
    await user.click(within(dialogo).getByRole('button', { name: /^cancelar$/i }))

    expect(habitoApi.excluirHabito).not.toHaveBeenCalled()
    expect(screen.getByDisplayValue('Correr')).toBeInTheDocument()
  })

  it('mostra erro quando restaurar falha e mantém o hábito excluído', async () => {
    vi.mocked(habitoApi.buscarHabito).mockResolvedValue({
      ...habito,
      deletada: true,
      deletedAt: '2026-11-01T00:00:00Z',
    })
    vi.mocked(habitoApi.restaurarHabito).mockRejectedValue(new ApiError(500, 'Falha ao restaurar'))
    const user = userEvent.setup()
    renderDrawer('/habitos/h-1')

    await user.click(await screen.findByRole('button', { name: /restaurar/i }))

    expect(await screen.findByText('Falha ao restaurar')).toBeInTheDocument()
    expect(aoAtualizar).not.toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: /hábito excluído/i })).toBeInTheDocument()
  })

  describe('seção Histórico', () => {
    it('não mostra a seção nem consulta o histórico ao criar', async () => {
      renderDrawer('/habitos/nova')

      expect(await screen.findByLabelText('Título')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Histórico' })).not.toBeInTheDocument()
      expect(habitoApi.buscarHistoricoHabito).not.toHaveBeenCalled()
    })

    it('mostra a seção com o histórico do hábito em edição', async () => {
      vi.mocked(habitoApi.buscarHabito).mockResolvedValue(habito)
      vi.mocked(habitoApi.buscarHistoricoHabito).mockResolvedValue([
        { data: '2026-11-10', minutosDedicados: 30, metaMinutos: 30, suficiente: true, origem: 'INFORMADO' },
      ])
      renderDrawer('/habitos/h-1')

      expect(await screen.findByRole('heading', { name: 'Histórico' })).toBeInTheDocument()
      expect(await screen.findByText('Meta atingida')).toBeInTheDocument()
      expect(habitoApi.buscarHistoricoHabito).toHaveBeenCalledWith('h-1')
    })

    it('erro no histórico não impede salvar o hábito', async () => {
      vi.mocked(habitoApi.buscarHabito).mockResolvedValue(habito)
      vi.mocked(habitoApi.buscarHistoricoHabito).mockRejectedValue(new ApiError(500, 'Erro interno'))
      vi.mocked(habitoApi.atualizarHabito).mockResolvedValue({ ...habito, titulo: 'Correr 5km' })
      const user = userEvent.setup()
      renderDrawer('/habitos/h-1')

      expect(await screen.findByText(/não foi possível carregar o histórico/i)).toBeInTheDocument()
      const titulo = screen.getByLabelText('Título')
      await user.clear(titulo)
      await user.type(titulo, 'Correr 5km')
      await user.click(screen.getByRole('button', { name: /^salvar$/i }))

      await waitFor(() => {
        expect(habitoApi.atualizarHabito).toHaveBeenCalled()
      })
      expect(aoAtualizar).toHaveBeenCalled()
    })
  })
})
