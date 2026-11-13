import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as decidirApi from '../decidir/api/decidirApi'
import { itemDecidir, paginaDecidir } from '../test/revisaoFixtures'
import { PassoDecidir } from './PassoDecidir'

vi.mock('../decidir/api/decidirApi', () => ({
  listarDecidir: vi.fn(),
  excluirDecidir: vi.fn(),
  promoverDecidir: vi.fn(),
}))

const a = itemDecidir({ id: 'd-a', titulo: 'Trocar de plano de celular' })
const b = itemDecidir({ id: 'd-b', titulo: 'Curso de inglês' })

function renderPasso() {
  const onTratou = vi.fn()
  render(<PassoDecidir onTratou={onTratou} />)
  return { onTratou }
}

describe('PassoDecidir', () => {
  beforeEach(() => {
    vi.mocked(decidirApi.listarDecidir).mockReset()
    vi.mocked(decidirApi.excluirDecidir).mockReset()
    vi.mocked(decidirApi.promoverDecidir).mockReset()
  })

  it('promover tira o item da lista e conta como decidido', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir)
      .mockResolvedValueOnce(paginaDecidir([a, b]))
      .mockResolvedValueOnce(paginaDecidir([b]))
    vi.mocked(decidirApi.promoverDecidir).mockResolvedValue({ ...a, tarefaId: 't-1' })
    const { onTratou } = renderPasso()

    const linha = (await screen.findByText(a.titulo)).closest('li') as HTMLElement
    await user.click(within(linha).getByRole('button', { name: /promover/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    await waitFor(() => expect(screen.queryByText(a.titulo)).not.toBeInTheDocument())
    expect(screen.getByText(b.titulo)).toBeInTheDocument()
    expect(onTratou).toHaveBeenCalledTimes(1)
    expect(decidirApi.listarDecidir).toHaveBeenCalledWith(expect.objectContaining({ promovidos: false }))
  })

  it('mostra estado vazio quando a fila já está vazia', async () => {
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue(paginaDecidir([]))

    renderPasso()

    expect(await screen.findByText(/fila decidir vazia/i)).toHaveAttribute('role', 'status')
  })

  it('destaca item com prazo de decisão vencido', async () => {
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue(
      paginaDecidir([{ ...a, decidirAte: '2020-01-01T12:00:00Z' }, b]),
    )

    renderPasso()

    const vencido = (await screen.findByText(a.titulo)).closest('li') as HTMLElement
    expect(vencido).toHaveAttribute('data-vencido', 'true')
    expect(within(vencido).getByText(/prazo de decisão vencido/i)).toBeInTheDocument()
    const normal = screen.getByText(b.titulo).closest('li') as HTMLElement
    expect(normal).not.toHaveAttribute('data-vencido')
  })

  it('descartar exige confirmação e não chama a API se cancelar', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue(paginaDecidir([a]))
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /descartar/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /cancelar/i }))

    expect(decidirApi.excluirDecidir).not.toHaveBeenCalled()
    expect(onTratou).not.toHaveBeenCalled()
  })

  it('descartar confirmado remove o item', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir)
      .mockResolvedValueOnce(paginaDecidir([a]))
      .mockResolvedValueOnce(paginaDecidir([]))
    vi.mocked(decidirApi.excluirDecidir).mockResolvedValue()
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /descartar/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^descartar$/i }))

    await waitFor(() => expect(decidirApi.excluirDecidir).toHaveBeenCalledWith('d-a'))
    expect(onTratou).toHaveBeenCalledTimes(1)
    expect(await screen.findByText(/fila decidir vazia/i)).toBeInTheDocument()
  })

  it('item já promovido em outra aba (409) mostra o conflito e não conta', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue(paginaDecidir([a]))
    vi.mocked(decidirApi.promoverDecidir).mockRejectedValue(
      new ApiError(409, 'Item já foi promovido.'),
    )
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /promover/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Item já foi promovido.')
    expect(decidirApi.promoverDecidir).toHaveBeenCalledTimes(1)
    expect(onTratou).not.toHaveBeenCalled()
  })

  it('erro 500 ao listar mostra mensagem', async () => {
    vi.mocked(decidirApi.listarDecidir).mockRejectedValue(new ApiError(500, 'Erro interno.'))

    renderPasso()

    expect(await screen.findByRole('alert')).toHaveTextContent('Erro interno.')
  })

  it('falha ao descartar mantém o item e mostra a mensagem', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.listarDecidir).mockResolvedValue(paginaDecidir([a]))
    vi.mocked(decidirApi.excluirDecidir).mockRejectedValue(new ApiError(404, 'Item não encontrado.'))
    renderPasso()

    await user.click(await screen.findByRole('button', { name: /descartar/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^descartar$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Item não encontrado.')
    expect(screen.getByText(a.titulo)).toBeInTheDocument()
  })
})
