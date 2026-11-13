import { render, renderHook, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useMemo } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { ArrasteHandle } from './ArrasteHandle'
import { useListaArrastavel } from './useListaArrastavel'

function Lista({
  itens,
  onReordenar,
  disabled = false,
}: {
  itens: { id: string; titulo: string }[]
  onReordenar: (ids: string[]) => void | Promise<void>
  disabled?: boolean
}) {
  const ids = useMemo(() => itens.map((item) => item.id), [itens])
  const { idsVisiveis, handleProps, registrarItem, arrastandoId } = useListaArrastavel({
    ids,
    onReordenar,
    disabled,
  })
  const porId = new Map(itens.map((item) => [item.id, item]))

  return (
    <ul>
      {idsVisiveis.map((id) => {
        const item = porId.get(id)
        if (!item) {
          return null
        }
        return (
          <li key={id} ref={(el) => registrarItem(id, el)} data-arrastando={arrastandoId === id}>
            <ArrasteHandle label={`Reordenar ${item.titulo}`} {...handleProps(id)} />
            <span>{item.titulo}</span>
          </li>
        )
      })}
    </ul>
  )
}

const itens = [
  { id: 'a', titulo: 'Alfa' },
  { id: 'b', titulo: 'Beta' },
  { id: 'c', titulo: 'Gama' },
]

describe('useListaArrastavel', () => {
  it('move item para baixo com seta e persiste', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(<Lista itens={itens} onReordenar={onReordenar} />)

    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{ArrowDown}')

    expect(onReordenar).toHaveBeenCalledWith(['b', 'a', 'c'])
  })

  it('move item para cima com seta', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(<Lista itens={itens} onReordenar={onReordenar} />)

    screen.getByRole('button', { name: 'Reordenar Beta' }).focus()
    await user.keyboard('{ArrowUp}')

    expect(onReordenar).toHaveBeenCalledWith(['b', 'a', 'c'])
  })

  it('não persiste quando a ordem não muda (seta além do limite)', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(<Lista itens={itens} onReordenar={onReordenar} />)

    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{ArrowUp}')

    expect(onReordenar).not.toHaveBeenCalled()
  })

  it('não reordena quando disabled', () => {
    const onReordenar = vi.fn()
    render(<Lista itens={itens} onReordenar={onReordenar} disabled />)

    expect(screen.queryByRole('button', { name: /reordenar/i })).not.toBeInTheDocument()
    expect(screen.getByText('Alfa')).toBeInTheDocument()
    expect(onReordenar).not.toHaveBeenCalled()
  })

  it('reverte a ordem visível quando persistir rejeita', async () => {
    const onReordenar = vi.fn().mockRejectedValue(new Error('409'))
    const user = userEvent.setup()
    render(<Lista itens={itens} onReordenar={onReordenar} />)

    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{ArrowDown}')

    expect(onReordenar).toHaveBeenCalled()
    expect(await screen.findByText('Alfa')).toBeInTheDocument()
    const titulos = screen.getAllByText(/Alfa|Beta|Gama/).map((el) => el.textContent)
    expect(titulos).toEqual(['Alfa', 'Beta', 'Gama'])
  })

  it('ignora teclas que não são setas', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(<Lista itens={itens} onReordenar={onReordenar} />)

    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{Enter}')
    await user.keyboard('x')

    expect(onReordenar).not.toHaveBeenCalled()
  })

  it('expõe ids iniciais no hook', () => {
    const { result } = renderHook(() =>
      useListaArrastavel({ ids: ['a', 'b'], onReordenar: vi.fn() }),
    )

    expect(result.current.idsVisiveis).toEqual(['a', 'b'])
    expect(result.current.arrastandoId).toBeNull()
  })
})
