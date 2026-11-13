import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as decidirApi from './api/decidirApi'
import type { DecidirResponse } from './api/decidirApi'
import { DecidirDrawer } from './DecidirDrawer'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

vi.mock('./api/decidirApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/decidirApi')>()
  return {
    ...actual,
    criarDecidir: vi.fn(),
    buscarDecidir: vi.fn(),
    atualizarDecidir: vi.fn(),
  }
})

const item: DecidirResponse = {
  id: 'd-1',
  titulo: 'Comprar curso',
  descricao: null,
  decidirAte: null,
  projetoId: null,
  tarefaId: null,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

const aoCriar = vi.fn()
const aoAtualizar = vi.fn()

function ListaFake() {
  return <Outlet context={{ projetosAtivos: [], aoCriar, aoAtualizar }} />
}

function renderDrawer(path: string) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/decidir" element={<ListaFake />}>
            <Route path="nova" element={<DecidirDrawer />} />
            <Route path=":id" element={<DecidirDrawer />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('DecidirDrawer', () => {
  beforeEach(() => {
    aoCriar.mockReset()
    aoAtualizar.mockReset()
    vi.mocked(decidirApi.criarDecidir).mockReset()
    vi.mocked(decidirApi.buscarDecidir).mockReset()
    vi.mocked(decidirApi.atualizarDecidir).mockReset()
  })

  it('cria item com título válido', async () => {
    vi.mocked(decidirApi.criarDecidir).mockResolvedValue(item)
    const user = userEvent.setup()
    renderDrawer('/decidir/nova')

    await user.type(screen.getByLabelText('Título'), 'Comprar curso')
    await user.click(screen.getByRole('button', { name: /criar item/i }))

    await waitFor(() => {
      expect(decidirApi.criarDecidir).toHaveBeenCalledWith(
        expect.objectContaining({ titulo: 'Comprar curso' }),
      )
    })
    expect(aoCriar).toHaveBeenCalledWith(item)
  })

  it('não cria com título vazio', async () => {
    const user = userEvent.setup()
    renderDrawer('/decidir/nova')

    await user.click(screen.getByRole('button', { name: /criar item/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(decidirApi.criarDecidir).not.toHaveBeenCalled()
    expect(aoCriar).not.toHaveBeenCalled()
  })

  it('carrega item existente para edição', async () => {
    vi.mocked(decidirApi.buscarDecidir).mockResolvedValue(item)
    renderDrawer('/decidir/d-1')

    expect(await screen.findByDisplayValue('Comprar curso')).toBeInTheDocument()
  })

  it('mostra erro quando buscar falha e não preenche o form', async () => {
    vi.mocked(decidirApi.buscarDecidir).mockRejectedValue(new ApiError(404, 'Item não encontrado'))
    renderDrawer('/decidir/d-sumiu')

    expect(await screen.findByText('Item não encontrado')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('Comprar curso')).not.toBeInTheDocument()
  })

  it('salva edição', async () => {
    vi.mocked(decidirApi.buscarDecidir).mockResolvedValue(item)
    vi.mocked(decidirApi.atualizarDecidir).mockResolvedValue({ ...item, titulo: 'Novo' })
    const user = userEvent.setup()
    renderDrawer('/decidir/d-1')

    const titulo = await screen.findByLabelText('Título')
    await user.clear(titulo)
    await user.type(titulo, 'Novo')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    await waitFor(() => {
      expect(decidirApi.atualizarDecidir).toHaveBeenCalledWith(
        'd-1',
        expect.objectContaining({ titulo: 'Novo' }),
      )
    })
    expect(aoAtualizar).toHaveBeenCalled()
  })

  it('mostra erro de API ao salvar e preserva o valor', async () => {
    vi.mocked(decidirApi.buscarDecidir).mockResolvedValue(item)
    vi.mocked(decidirApi.atualizarDecidir).mockRejectedValue(new ApiError(409, 'Conflito'))
    const user = userEvent.setup()
    renderDrawer('/decidir/d-1')

    const titulo = await screen.findByLabelText('Título')
    await user.clear(titulo)
    await user.type(titulo, 'Conflito')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    expect(await screen.findByText('Conflito')).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveValue('Conflito')
    expect(aoAtualizar).not.toHaveBeenCalled()
  })

  it('não permite editar item já promovido a tarefa', async () => {
    vi.mocked(decidirApi.buscarDecidir).mockResolvedValue({ ...item, tarefaId: 't-1' })
    renderDrawer('/decidir/d-1')

    expect(await screen.findByRole('heading', { name: /item decidido/i })).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toBeDisabled()
    expect(screen.queryByRole('button', { name: /^salvar$/i })).not.toBeInTheDocument()
  })
})
