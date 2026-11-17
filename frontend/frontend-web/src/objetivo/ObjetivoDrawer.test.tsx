import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes, useLocation, type InitialEntry } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { adicionarDiasIso } from '../capacidade/semana'
import { ToastProvider } from '../feedback/ToastProvider'
import { dataHojeIso } from '../planner/data'
import * as objetivoApi from './api/objetivoApi'
import type { ObjetivoResponse } from './api/objetivoApi'
import { ObjetivoDrawer } from './ObjetivoDrawer'

vi.mock('./api/objetivoApi', () => ({
  criarObjetivo: vi.fn(),
  buscarObjetivo: vi.fn(),
  atualizarObjetivo: vi.fn(),
}))

const aoSalvar = vi.fn()

function objetivo(parcial: Partial<ObjetivoResponse> = {}): ObjetivoResponse {
  return {
    id: 'obj-1',
    titulo: 'Perder 5 kg',
    descricao: 'Correr 3x',
    prazo: null,
    status: 'ATIVO',
    concluidoEm: null,
    arquivadoEm: null,
    createdAt: '2026-11-01T12:00:00Z',
    updatedAt: '2026-11-01T12:00:00Z',
    ...parcial,
  }
}

function ListaFake() {
  const location = useLocation()
  return (
    <>
      <p data-testid="location">{`${location.pathname}${location.search}`}</p>
      <Outlet context={{ aoSalvar }} />
    </>
  )
}

function renderDrawer(entries: InitialEntry[]) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
        <Routes>
          <Route path="/objetivos" element={<ListaFake />}>
            <Route path="nova" element={<ObjetivoDrawer />} />
            <Route path=":id" element={<ObjetivoDrawer />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function drawer(nome: RegExp) {
  return screen.getByRole('dialog', { name: nome })
}

describe('ObjetivoDrawer', () => {
  beforeEach(() => {
    aoSalvar.mockReset()
    vi.mocked(objetivoApi.criarObjetivo).mockReset()
    vi.mocked(objetivoApi.buscarObjetivo).mockReset()
    vi.mocked(objetivoApi.atualizarObjetivo).mockReset()
  })

  it('cria objetivo só com título, fecha, recarrega a lista e mostra toast', async () => {
    vi.mocked(objetivoApi.criarObjetivo).mockResolvedValue(objetivo())
    const user = userEvent.setup()
    renderDrawer(['/objetivos', { pathname: '/objetivos/nova', state: { daLista: true } }])

    await user.type(screen.getByLabelText('Título'), '  Perder 5 kg ')
    await user.click(screen.getByRole('button', { name: 'Criar objetivo' }))

    expect(await screen.findByText('Objetivo criado')).toBeInTheDocument()
    expect(objetivoApi.criarObjetivo).toHaveBeenCalledWith({
      titulo: 'Perder 5 kg',
      descricao: null,
      prazo: null,
    })
    expect(aoSalvar).toHaveBeenCalledOnce()
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: /novo objetivo/i })).not.toBeInTheDocument()
    })
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/objetivos$/)
  })

  it('criar a partir da visão Concluídos fecha na visão Ativos, onde o objetivo aparece', async () => {
    vi.mocked(objetivoApi.criarObjetivo).mockResolvedValue(objetivo())
    const user = userEvent.setup()
    renderDrawer([
      '/objetivos?status=CONCLUIDO',
      { pathname: '/objetivos/nova', search: '?status=CONCLUIDO', state: { daLista: true } },
    ])

    await user.type(screen.getByLabelText('Título'), 'Perder 5 kg')
    await user.click(screen.getByRole('button', { name: 'Criar objetivo' }))

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/objetivos$/)
    })
  })

  it('edita o título, envia o PATCH e mostra "Objetivo atualizado"', async () => {
    vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo({ prazo: '2099-12-31' }))
    vi.mocked(objetivoApi.atualizarObjetivo).mockResolvedValue(objetivo({ titulo: 'Perder 6 kg' }))
    const user = userEvent.setup()
    renderDrawer(['/objetivos/obj-1'])

    const titulo = await screen.findByDisplayValue('Perder 5 kg')
    await user.clear(titulo)
    await user.type(titulo, 'Perder 6 kg')
    await user.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Objetivo atualizado')).toBeInTheDocument()
    expect(objetivoApi.atualizarObjetivo).toHaveBeenCalledWith('obj-1', {
      titulo: 'Perder 6 kg',
      descricao: 'Correr 3x',
      prazo: '2099-12-31',
    })
    expect(aoSalvar).toHaveBeenCalledOnce()
  })

  it('remover prazo envia prazo null e descrição vazia limpa a descrição', async () => {
    vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo({ prazo: '2099-12-31' }))
    vi.mocked(objetivoApi.atualizarObjetivo).mockResolvedValue(objetivo())
    const user = userEvent.setup()
    renderDrawer(['/objetivos/obj-1'])

    await screen.findByDisplayValue('Perder 5 kg')
    await user.click(screen.getByRole('button', { name: 'Remover prazo' }))
    await user.clear(screen.getByLabelText('Descrição (opcional)'))
    await user.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => {
      expect(objetivoApi.atualizarObjetivo).toHaveBeenCalledWith('obj-1', {
        titulo: 'Perder 5 kg',
        descricao: '',
        prazo: null,
      })
    })
  })

  it('mantém prazo vencido inalterado sem erro e sem data mínima no campo', async () => {
    vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo({ prazo: '2020-01-01' }))
    vi.mocked(objetivoApi.atualizarObjetivo).mockResolvedValue(objetivo())
    const user = userEvent.setup()
    renderDrawer(['/objetivos/obj-1'])

    const titulo = await screen.findByDisplayValue('Perder 5 kg')
    expect(screen.getByLabelText('Prazo (opcional)')).not.toHaveAttribute('min')
    await user.type(titulo, '!')
    await user.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => {
      expect(objetivoApi.atualizarObjetivo).toHaveBeenCalledWith(
        'obj-1',
        expect.objectContaining({ prazo: '2020-01-01' }),
      )
    })
  })

  it('campo de prazo de objetivo novo tem hoje como data mínima', () => {
    renderDrawer(['/objetivos/nova'])

    expect(screen.getByLabelText('Prazo (opcional)')).toHaveAttribute('min', dataHojeIso())
  })

  it('título em branco mostra erro no título e não chama a API', async () => {
    const user = userEvent.setup()
    renderDrawer(['/objetivos/nova'])

    await user.type(screen.getByLabelText('Título'), '   ')
    await user.click(screen.getByRole('button', { name: 'Criar objetivo' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Informe o título do objetivo.')
    expect(screen.getByLabelText('Título')).toHaveAttribute('aria-invalid', 'true')
    expect(objetivoApi.criarObjetivo).not.toHaveBeenCalled()
    expect(aoSalvar).not.toHaveBeenCalled()
  })

  it('prazo de ontem num objetivo novo mostra erro no prazo e não chama a API', async () => {
    const user = userEvent.setup()
    renderDrawer(['/objetivos/nova'])

    await user.type(screen.getByLabelText('Título'), 'Ler 12 livros')
    fireEvent.change(screen.getByLabelText('Prazo (opcional)'), {
      target: { value: adicionarDiasIso(dataHojeIso(), -1) },
    })
    await user.click(screen.getByRole('button', { name: 'Criar objetivo' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('O prazo não pode estar no passado.')
    expect(screen.getByLabelText('Prazo (opcional)')).toHaveAttribute('aria-invalid', 'true')
    expect(objetivoApi.criarObjetivo).not.toHaveBeenCalled()
  })

  it('erro 400 da API mantém o drawer aberto com os dados e mostra a mensagem', async () => {
    vi.mocked(objetivoApi.criarObjetivo).mockRejectedValue(
      new ApiError(400, 'O prazo não pode estar no passado.'),
    )
    const user = userEvent.setup()
    renderDrawer(['/objetivos/nova'])

    await user.type(screen.getByLabelText('Título'), 'Ler 12 livros')
    await user.click(screen.getByRole('button', { name: 'Criar objetivo' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('O prazo não pode estar no passado.')
    expect(drawer(/novo objetivo/i)).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveValue('Ler 12 livros')
    expect(aoSalvar).not.toHaveBeenCalled()
    expect(screen.queryByText('Objetivo criado')).not.toBeInTheDocument()
  })

  it('objetivo não encontrado mostra a mensagem da API sem campos nem botão Salvar', async () => {
    vi.mocked(objetivoApi.buscarObjetivo).mockRejectedValue(
      new ApiError(404, 'Objetivo inexistente ou sem permissão de acesso.'),
    )
    renderDrawer(['/objetivos/sumiu'])

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Objetivo inexistente ou sem permissão de acesso.',
    )
    expect(screen.queryByLabelText('Título')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Salvar' })).not.toBeInTheDocument()
  })

  it('Esc com alteração pede descarte; continuar editando mantém os dados', async () => {
    const user = userEvent.setup()
    renderDrawer(['/objetivos/nova'])

    await user.type(screen.getByLabelText('Título'), 'Aprender Kotlin')
    await user.keyboard('{Escape}')

    const confirmacao = await screen.findByRole('dialog', { name: /descartar objetivo/i })
    await user.click(within(confirmacao).getByRole('button', { name: 'Continuar editando' }))

    expect(drawer(/novo objetivo/i)).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveValue('Aprender Kotlin')
  })

  it('fechar sem alteração volta à mesma visão sem confirmação', async () => {
    vi.mocked(objetivoApi.buscarObjetivo).mockResolvedValue(objetivo({ status: 'CONCLUIDO' }))
    const user = userEvent.setup()
    renderDrawer([
      '/objetivos?status=CONCLUIDO',
      { pathname: '/objetivos/obj-1', search: '?status=CONCLUIDO', state: { daLista: true } },
    ])

    await screen.findByDisplayValue('Perder 5 kg')
    await user.click(screen.getAllByRole('button', { name: 'Fechar' })[0])

    expect(screen.queryByRole('dialog', { name: /descartar/i })).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent('/objetivos?status=CONCLUIDO')
    })
  })
})
