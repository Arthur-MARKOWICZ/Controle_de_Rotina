import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as tagApi from './api/tagApi'
import { TagPicker } from './TagPicker'

vi.mock('./api/tagApi', () => ({
  TAG_COR_PADRAO: '#6B7280',
  listarTags: vi.fn(),
  criarTag: vi.fn(),
}))

const catalogo = [
  { id: 't1', nome: 'Trabalho', cor: '#6B7280' },
  { id: 't2', nome: 'Casa', cor: '#2563EB' },
]

describe('TagPicker', () => {
  beforeEach(() => {
    vi.mocked(tagApi.listarTags).mockResolvedValue(catalogo)
    vi.mocked(tagApi.criarTag).mockReset()
  })

  it('lista tags e seleciona existente', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={[]} onChange={onChange} />
      </MemoryRouter>,
    )

    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: /escolher tags/i }))
    await user.click(screen.getByRole('button', { name: /trabalho/i }))

    expect(onChange).toHaveBeenCalledWith(['t1'], [catalogo[0]])
  })

  it('cria tag inline com cor padrão', async () => {
    vi.mocked(tagApi.criarTag).mockResolvedValue({
      id: 't3',
      nome: 'Nova',
      cor: '#6B7280',
    })
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={[]} onChange={onChange} />
      </MemoryRouter>,
    )

    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
    await user.click(screen.getByRole('button', { name: /escolher tags/i }))
    await user.type(screen.getByRole('searchbox', { name: /buscar tag/i }), 'Nova')
    await user.click(screen.getByRole('button', { name: /criar “nova”/i }))

    expect(tagApi.criarTag).toHaveBeenCalledWith({ nome: 'Nova', cor: '#6B7280' })
    expect(onChange).toHaveBeenCalledWith(
      ['t3'],
      [{ id: 't3', nome: 'Nova', cor: '#6B7280' }],
    )
  })

  it('link Gerenciar aponta para /tags', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={[]} onChange={() => {}} />
      </MemoryRouter>,
    )
    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
    await user.click(screen.getByRole('button', { name: /escolher tags/i }))
    expect(screen.getByRole('link', { name: /gerenciar/i })).toHaveAttribute('href', '/tags')
  })

  it('desmarca tag já selecionada', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={['t1']} selectedTags={[catalogo[0]]} onChange={onChange} />
      </MemoryRouter>,
    )
    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
    await user.click(screen.getByRole('button', { name: /remover tag trabalho/i }))
    expect(onChange).toHaveBeenCalledWith([], [])
  })

  it('mostra erro e não altera seleção quando listar falha', async () => {
    vi.mocked(tagApi.listarTags).mockRejectedValue(new Error('rede'))
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={[]} onChange={onChange} />
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: /escolher tags/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/carregar as tags/i)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('mostra erro ao criar e não chama onChange', async () => {
    vi.mocked(tagApi.criarTag).mockRejectedValue(new Error('409'))
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={[]} onChange={onChange} />
      </MemoryRouter>,
    )
    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
    await user.click(screen.getByRole('button', { name: /escolher tags/i }))
    await user.type(screen.getByRole('searchbox', { name: /buscar tag/i }), 'Nova')
    await user.click(screen.getByRole('button', { name: /criar “nova”/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/criar a tag/i)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('disabled não dispara onChange', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TagPicker selectedIds={['t1']} selectedTags={[catalogo[0]]} onChange={onChange} disabled />
      </MemoryRouter>,
    )
    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
    await user.click(screen.getByRole('button', { name: /remover tag trabalho/i }))
    expect(onChange).not.toHaveBeenCalled()
  })
})
