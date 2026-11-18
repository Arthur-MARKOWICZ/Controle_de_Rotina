import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import { ToastProvider } from '../../feedback/ToastProvider'
import * as objetivoApi from '../api/objetivoApi'
import type { ObjetivoResponse, PageObjetivo } from '../api/objetivoApi'
import { ObjetivosPage } from './ObjetivosPage'

vi.mock('../api/objetivoApi', () => ({
  listarObjetivos: vi.fn(),
  concluirObjetivo: vi.fn(),
  arquivarObjetivo: vi.fn(),
  reabrirObjetivo: vi.fn(),
  criarObjetivo: vi.fn(),
  buscarObjetivo: vi.fn(),
  atualizarObjetivo: vi.fn(),
}))

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

function objetivo(parcial: Partial<ObjetivoResponse> = {}): ObjetivoResponse {
  return {
    id: 'obj-1',
    titulo: 'Perder 5 kg',
    descricao: null,
    prazo: null,
    status: 'ATIVO',
    concluidoEm: null,
    arquivadoEm: null,
    createdAt: '2026-11-01T12:00:00Z',
    updatedAt: '2026-11-01T12:00:00Z',
    progresso: null,
    ...parcial,
  }
}

function pagina(content: ObjetivoResponse[], extra: Partial<PageObjetivo['page']> = {}): PageObjetivo {
  return {
    content,
    page: { size: 20, number: 0, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra },
  }
}

const VAZIA = pagina([])

function renderPagina(path = '/objetivos') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <LocationProbe />
        <Routes>
          <Route path="/objetivos" element={<ObjetivosPage />}>
            <Route path=":id" element={<p>drawer aberto</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

function card(titulo: string): HTMLElement {
  const link = screen.getByRole('link', { name: titulo })
  return link.closest('li') as HTMLElement
}

async function confirmar(rotulo: string) {
  const dialog = await screen.findByRole('dialog')
  await userEvent.click(within(dialog).getByRole('button', { name: rotulo }))
}

describe('ObjetivosPage', () => {
  beforeEach(() => {
    vi.mocked(objetivoApi.listarObjetivos).mockReset()
    vi.mocked(objetivoApi.concluirObjetivo).mockReset()
    vi.mocked(objetivoApi.arquivarObjetivo).mockReset()
    vi.mocked(objetivoApi.reabrirObjetivo).mockReset()
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(pagina([objetivo()]))
  })

  it('padrão lista ativos com a visão Ativos marcada e o total', async () => {
    renderPagina()

    expect(await screen.findByRole('link', { name: 'Perder 5 kg' })).toBeInTheDocument()
    expect(objetivoApi.listarObjetivos).toHaveBeenCalledWith({ status: 'ATIVO', page: 0, size: 20 })
    expect(screen.getByRole('button', { name: 'Ativos' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('1 objetivo')).toBeInTheDocument()
  })

  it('mostra o placeholder de carregamento sem cards enquanto busca', () => {
    vi.mocked(objetivoApi.listarObjetivos).mockReturnValue(new Promise(() => {}))
    renderPagina()

    expect(document.querySelector('[aria-busy="true"]')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('trocar de visão escreve o status na URL e zera a página', async () => {
    const user = userEvent.setup()
    renderPagina('/objetivos?page=2')
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(screen.getByRole('button', { name: 'Concluídos' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/objetivos?status=CONCLUIDO')
    await waitFor(() => {
      expect(objetivoApi.listarObjetivos).toHaveBeenLastCalledWith({
        status: 'CONCLUIDO',
        page: 0,
        size: 20,
      })
    })
  })

  it('status desconhecido na URL cai na visão Ativos', async () => {
    renderPagina('/objetivos?status=PAUSADO')

    await screen.findByRole('link', { name: 'Perder 5 kg' })
    expect(objetivoApi.listarObjetivos).toHaveBeenCalledWith({ status: 'ATIVO', page: 0, size: 20 })
    expect(screen.getByRole('button', { name: 'Ativos' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('paginação avança e reflete a página na URL', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(
      pagina([objetivo()], { totalElements: 25, totalPages: 2 }),
    )
    renderPagina()
    await screen.findByText('Página 1 de 2')

    await user.click(screen.getByRole('button', { name: 'Próxima' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/objetivos?page=1')
    await waitFor(() => {
      expect(objetivoApi.listarObjetivos).toHaveBeenLastCalledWith({ status: 'ATIVO', page: 1, size: 20 })
    })
  })

  it('sem ativos mostra o vazio com "Criar primeiro objetivo"', async () => {
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(VAZIA)
    renderPagina()

    expect(await screen.findByText('Nenhum objetivo ainda')).toBeInTheDocument()
    expect(screen.getByText(/perder 5 kg/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Criar primeiro objetivo' })).toHaveAttribute(
      'href',
      '/objetivos/nova',
    )
  })

  it('visão Arquivados vazia oferece "Ver ativos"', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(VAZIA)
    renderPagina('/objetivos?status=ARQUIVADO')

    expect(await screen.findByText('Nenhum objetivo arquivado.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver ativos' }))

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/objetivos$/)
  })

  it('falha ao carregar mostra alerta sem cards nem vazio', async () => {
    vi.mocked(objetivoApi.listarObjetivos).mockRejectedValue(new ApiError(500, 'Erro interno'))
    renderPagina()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar os objetivos.')
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(screen.queryByText('Nenhum objetivo ainda')).not.toBeInTheDocument()
  })

  it('concluir confirmado chama a API, mostra toast e recarrega sem o item', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.listarObjetivos)
      .mockResolvedValueOnce(pagina([objetivo()]))
      .mockResolvedValueOnce(VAZIA)
    vi.mocked(objetivoApi.concluirObjetivo).mockResolvedValue(objetivo({ status: 'CONCLUIDO' }))
    renderPagina()
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(within(card('Perder 5 kg')).getByRole('button', { name: 'Concluir' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Concluir objetivo?')
    expect(dialog).toHaveTextContent('“Perder 5 kg” sai dos ativos')
    await user.click(within(dialog).getByRole('button', { name: 'Concluir' }))

    expect(await screen.findByText('Objetivo concluído')).toBeInTheDocument()
    expect(objetivoApi.concluirObjetivo).toHaveBeenCalledWith('obj-1')
    expect(await screen.findByText('Nenhum objetivo ainda')).toBeInTheDocument()
  })

  it('arquivar cancelado não chama a API nem recarrega', async () => {
    const user = userEvent.setup()
    renderPagina()
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(within(card('Perder 5 kg')).getByRole('button', { name: 'Arquivar' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /cancelar/i }))

    expect(objetivoApi.arquivarObjetivo).not.toHaveBeenCalled()
    expect(objetivoApi.listarObjetivos).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('link', { name: 'Perder 5 kg' })).toBeInTheDocument()
  })

  it('arquivar um concluído confirma e mostra "Objetivo arquivado"', async () => {
    const concluido = objetivo({ status: 'CONCLUIDO', concluidoEm: '2026-11-12T15:00:00Z' })
    vi.mocked(objetivoApi.listarObjetivos)
      .mockResolvedValueOnce(pagina([concluido]))
      .mockResolvedValueOnce(VAZIA)
    vi.mocked(objetivoApi.arquivarObjetivo).mockResolvedValue({ ...concluido, status: 'ARQUIVADO' })
    renderPagina('/objetivos?status=CONCLUIDO')
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await userEvent.click(within(card('Perder 5 kg')).getByRole('button', { name: 'Arquivar' }))
    await confirmar('Arquivar')

    expect(await screen.findByText('Objetivo arquivado')).toBeInTheDocument()
    expect(await screen.findByText('Nenhum objetivo concluído.')).toBeInTheDocument()
  })

  it('reabrir não pede confirmação e mostra "Objetivo reaberto"', async () => {
    const user = userEvent.setup()
    const arquivado = objetivo({ status: 'ARQUIVADO', arquivadoEm: '2026-11-10T12:00:00Z' })
    vi.mocked(objetivoApi.listarObjetivos)
      .mockResolvedValueOnce(pagina([arquivado]))
      .mockResolvedValueOnce(VAZIA)
    vi.mocked(objetivoApi.reabrirObjetivo).mockResolvedValue(objetivo())
    renderPagina('/objetivos?status=ARQUIVADO')
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(screen.getByRole('button', { name: 'Reabrir' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByText('Objetivo reaberto')).toBeInTheDocument()
    expect(objetivoApi.reabrirObjetivo).toHaveBeenCalledWith('obj-1')
  })

  it('clique duplo durante a ação envia só uma requisição', async () => {
    const user = userEvent.setup()
    let resolver: (o: ObjetivoResponse) => void = () => {}
    vi.mocked(objetivoApi.reabrirObjetivo).mockReturnValue(
      new Promise((resolve) => {
        resolver = resolve
      }),
    )
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(
      pagina([objetivo({ status: 'ARQUIVADO' })]),
    )
    renderPagina('/objetivos?status=ARQUIVADO')
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    const botao = screen.getByRole('button', { name: 'Reabrir' })
    await user.click(botao)
    await user.click(botao)

    expect(await screen.findByRole('button', { name: 'Reabrindo…' })).toBeDisabled()
    expect(objetivoApi.reabrirObjetivo).toHaveBeenCalledTimes(1)
    resolver(objetivo())
    expect(await screen.findByText('Objetivo reaberto')).toBeInTheDocument()
  })

  it('durante a ação num card, os botões dos outros cards ficam desabilitados', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.reabrirObjetivo).mockReturnValue(new Promise(() => {}))
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(
      pagina([
        objetivo({ status: 'ARQUIVADO' }),
        objetivo({ id: 'obj-2', titulo: 'Ler 12 livros', status: 'ARQUIVADO' }),
      ]),
    )
    renderPagina('/objetivos?status=ARQUIVADO')
    await screen.findByRole('link', { name: 'Ler 12 livros' })

    await user.click(within(card('Perder 5 kg')).getByRole('button', { name: 'Reabrir' }))

    expect(await within(card('Perder 5 kg')).findByRole('button', { name: 'Reabrindo…' })).toBeDisabled()
    expect(within(card('Ler 12 livros')).getByRole('button', { name: 'Reabrir' })).toBeDisabled()
  })

  it('409 ao concluir mostra a mensagem da API, sem toast, e recarrega a lista', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.listarObjetivos)
      .mockResolvedValueOnce(pagina([objetivo()]))
      .mockResolvedValueOnce(VAZIA)
    vi.mocked(objetivoApi.concluirObjetivo).mockRejectedValue(
      new ApiError(409, 'O objetivo informado já está concluído.'),
    )
    renderPagina()
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(within(card('Perder 5 kg')).getByRole('button', { name: 'Concluir' }))
    await confirmar('Concluir')

    expect(await screen.findByRole('alert')).toHaveTextContent('O objetivo informado já está concluído.')
    expect(await screen.findByText('Nenhum objetivo ainda')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('O objetivo informado já está concluído.')
    expect(objetivoApi.listarObjetivos).toHaveBeenCalledTimes(2)
    expect(screen.queryByText('Objetivo concluído')).not.toBeInTheDocument()
  })

  it('404 ao arquivar mostra a mensagem de não encontrado, sem toast', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.arquivarObjetivo).mockRejectedValue(
      new ApiError(404, 'Objetivo inexistente ou sem permissão de acesso.'),
    )
    renderPagina()
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(within(card('Perder 5 kg')).getByRole('button', { name: 'Arquivar' }))
    await confirmar('Arquivar')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Objetivo inexistente ou sem permissão de acesso.',
    )
    expect(screen.queryByText('Objetivo arquivado')).not.toBeInTheDocument()
  })

  it('falha sem ApiError ao reabrir usa a mensagem genérica', async () => {
    const user = userEvent.setup()
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(
      pagina([objetivo({ status: 'ARQUIVADO' })]),
    )
    vi.mocked(objetivoApi.reabrirObjetivo).mockRejectedValue(new TypeError('Failed to fetch'))
    renderPagina('/objetivos?status=ARQUIVADO')
    await screen.findByRole('link', { name: 'Perder 5 kg' })

    await user.click(screen.getByRole('button', { name: 'Reabrir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível reabrir o objetivo.')
  })
})
