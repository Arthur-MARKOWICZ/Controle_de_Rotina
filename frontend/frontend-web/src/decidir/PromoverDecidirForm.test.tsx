import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as decidirApi from './api/decidirApi'
import type { DecidirResponse } from './api/decidirApi'
import { PromoverDecidirForm } from './PromoverDecidirForm'

vi.mock('./api/decidirApi', () => ({
  promoverDecidir: vi.fn(),
}))

const item: DecidirResponse = {
  id: 'd-1',
  titulo: 'Comprar assinatura',
  descricao: null,
  decidirAte: null,
  projetoId: null,
  tarefaId: null,
  tags: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function renderForm() {
  const onPromovido = vi.fn()
  const onCancelar = vi.fn()
  render(<PromoverDecidirForm item={item} onPromovido={onPromovido} onCancelar={onCancelar} />)
  const dialog = screen.getByRole('dialog')
  return { onPromovido, onCancelar, dialog }
}

describe('PromoverDecidirForm', () => {
  beforeEach(() => {
    vi.mocked(decidirApi.promoverDecidir).mockReset()
  })

  it('promove só com o título', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.promoverDecidir).mockResolvedValue({ ...item, tarefaId: 't-1' })
    const { onPromovido, dialog } = renderForm()

    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    await waitFor(() => expect(onPromovido).toHaveBeenCalledWith({ ...item, tarefaId: 't-1' }))
    expect(decidirApi.promoverDecidir).toHaveBeenCalledWith('d-1', {})
  })

  it('envia duração, prioridade e prazo preenchidos', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.promoverDecidir).mockResolvedValue({ ...item, tarefaId: 't-1' })
    const { dialog } = renderForm()

    await user.type(within(dialog).getByLabelText(/duração/i), '45')
    await user.selectOptions(within(dialog).getByLabelText(/prioridade/i), 'ALTA')
    await user.type(within(dialog).getByLabelText(/prazo/i), '2030-01-10T18:00')
    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    await waitFor(() => expect(decidirApi.promoverDecidir).toHaveBeenCalledTimes(1))
    const [, body] = vi.mocked(decidirApi.promoverDecidir).mock.calls[0]
    expect(body).toMatchObject({ duracaoMinutos: 45, prioridade: 'ALTA' })
    expect(body?.prazo).toBe(new Date('2030-01-10T18:00').toISOString())
  })

  it('cancelar não chama a API', async () => {
    const user = userEvent.setup()
    const { onCancelar, dialog } = renderForm()

    await user.click(within(dialog).getByRole('button', { name: /cancelar/i }))

    expect(onCancelar).toHaveBeenCalled()
    expect(decidirApi.promoverDecidir).not.toHaveBeenCalled()
  })

  it('clique duplo envia uma única promoção', async () => {
    const user = userEvent.setup()
    let resolver: (v: DecidirResponse) => void = () => {}
    vi.mocked(decidirApi.promoverDecidir).mockReturnValue(
      new Promise((resolve) => {
        resolver = resolve
      }),
    )
    const { dialog } = renderForm()
    const botao = within(dialog).getByRole('button', { name: /^promover$/i })

    await user.dblClick(botao)

    expect(decidirApi.promoverDecidir).toHaveBeenCalledTimes(1)
    expect(botao).toBeDisabled()
    resolver({ ...item, tarefaId: 't-1' })
    await waitFor(() => expect(botao).not.toBeDisabled())
  })

  it('mostra a mensagem de conflito quando o item já foi promovido', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.promoverDecidir).mockRejectedValue(
      new ApiError(409, 'Item já promovido.'),
    )
    const { onPromovido, dialog } = renderForm()

    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Item já promovido.')
    expect(onPromovido).not.toHaveBeenCalled()
  })

  it('mostra a mensagem de prazo no passado (400)', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.promoverDecidir).mockRejectedValue(
      new ApiError(400, 'O prazo não pode estar no passado.'),
    )
    const { dialog } = renderForm()

    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'O prazo não pode estar no passado.',
    )
  })

  it('usa o texto padrão quando a falha não é ApiError (500/timeout)', async () => {
    const user = userEvent.setup()
    vi.mocked(decidirApi.promoverDecidir).mockRejectedValue(new TypeError('Failed to fetch'))
    const { onPromovido, dialog } = renderForm()

    await user.click(within(dialog).getByRole('button', { name: /^promover$/i }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Não foi possível promover o item.',
    )
    expect(onPromovido).not.toHaveBeenCalled()
  })
})
