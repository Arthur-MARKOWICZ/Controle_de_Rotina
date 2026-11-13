import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as tagApi from './api/tagApi'
import { TagFiltroPopover } from './TagFiltroPopover'

vi.mock('./api/tagApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/tagApi')>()
  return {
    ...actual,
    listarTags: vi.fn(),
  }
})

const tags = [
  { id: 'tag-1', nome: 'Casa', cor: '#6B7280' },
  { id: 'tag-2', nome: 'Trabalho', cor: '#2563EB' },
]

describe('TagFiltroPopover', () => {
  beforeEach(() => {
    vi.mocked(tagApi.listarTags).mockReset().mockResolvedValue(tags)
  })

  it('lista tags e aplica seleção', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(<TagFiltroPopover tagIds={[]} onAplicar={onAplicar} />)

    await user.click(screen.getByRole('button', { name: /tags/i }))
    await user.click(await screen.findByRole('checkbox', { name: /casa/i }))

    expect(onAplicar).toHaveBeenCalledWith(['tag-1'])
  })

  it('desmarca tag já selecionada', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(<TagFiltroPopover tagIds={['tag-1']} onAplicar={onAplicar} />)

    await user.click(screen.getByRole('button', { name: /tags/i }))
    await user.click(await screen.findByRole('checkbox', { name: /casa/i }))

    expect(onAplicar).toHaveBeenCalledWith([])
  })

  it('mostra estado vazio quando não há tags', async () => {
    vi.mocked(tagApi.listarTags).mockResolvedValue([])
    const user = userEvent.setup()
    render(<TagFiltroPopover tagIds={[]} onAplicar={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /tags/i }))

    expect(await screen.findByText('Nenhuma tag ainda.')).toBeInTheDocument()
  })

  it('mostra catálogo vazio quando listar falha (estado inalterado)', async () => {
    const onAplicar = vi.fn()
    vi.mocked(tagApi.listarTags).mockRejectedValue(new ApiError(500, 'Falha'))
    const user = userEvent.setup()
    render(<TagFiltroPopover tagIds={['tag-1']} onAplicar={onAplicar} />)

    await user.click(screen.getByRole('button', { name: /tags/i }))

    expect(await screen.findByText('Nenhuma tag ainda.')).toBeInTheDocument()
    expect(onAplicar).not.toHaveBeenCalled()
  })

  it('alterna filtro Sem tag', async () => {
    const onSemTag = vi.fn()
    const user = userEvent.setup()
    render(<TagFiltroPopover tagIds={[]} onAplicar={vi.fn()} semTag={false} onSemTag={onSemTag} />)

    await user.click(screen.getByRole('button', { name: /tags/i }))
    await user.click(await screen.findByRole('checkbox', { name: /sem tag/i }))

    expect(onSemTag).toHaveBeenCalledWith(true)
  })

  it('não mostra Sem tag sem callback', async () => {
    const user = userEvent.setup()
    render(<TagFiltroPopover tagIds={[]} onAplicar={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /tags/i }))
    await waitFor(() => expect(screen.queryByText('Carregando…')).not.toBeInTheDocument())

    expect(screen.queryByRole('checkbox', { name: /sem tag/i })).not.toBeInTheDocument()
  })

  it('marca o gatilho como ativo quando há filtro', () => {
    render(<TagFiltroPopover tagIds={['tag-1']} onAplicar={vi.fn()} />)

    expect(screen.getByRole('button', { name: /tags/i }).className).toContain('filtro-gatilho--ativo')
  })
})
