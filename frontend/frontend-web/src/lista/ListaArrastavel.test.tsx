import { render, screen } from '@testing-library/react'
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
  const { idsVisiveis, handleProps, registrarItem } = useListaArrastavel({
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
          <li key={id} ref={(el) => registrarItem(id, el)}>
            <ArrasteHandle label={`Reordenar ${item.titulo}`} {...handleProps(id)} />
            <span>{item.titulo}</span>
          </li>
        )
      })}
    </ul>
  )
}

describe('ListaArrastavel', () => {
  it('seta para baixo no handle move o item e persiste a nova ordem', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(
      <Lista
        itens={[
          { id: 'a', titulo: 'Alfa' },
          { id: 'b', titulo: 'Beta' },
        ]}
        onReordenar={onReordenar}
      />,
    )

    const handle = screen.getByRole('button', { name: 'Reordenar Alfa' })
    handle.focus()
    await user.keyboard('{ArrowDown}')

    expect(onReordenar).toHaveBeenCalledWith(['b', 'a'])
    const titulos = screen.getAllByText(/Alfa|Beta/).map((el) => el.textContent)
    expect(titulos).toEqual(['Beta', 'Alfa'])
  })

  it('seta para cima no segundo item persiste a nova ordem', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(
      <Lista
        itens={[
          { id: 'a', titulo: 'Alfa' },
          { id: 'b', titulo: 'Beta' },
        ]}
        onReordenar={onReordenar}
      />,
    )
    screen.getByRole('button', { name: 'Reordenar Beta' }).focus()
    await user.keyboard('{ArrowUp}')
    expect(onReordenar).toHaveBeenCalledWith(['b', 'a'])
  })

  it('não persiste quando a seta passa do limite', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(
      <Lista
        itens={[
          { id: 'a', titulo: 'Alfa' },
          { id: 'b', titulo: 'Beta' },
        ]}
        onReordenar={onReordenar}
      />,
    )
    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{ArrowUp}')
    expect(onReordenar).not.toHaveBeenCalled()
  })

  it('não reordena quando disabled', async () => {
    const onReordenar = vi.fn()
    render(
      <Lista
        itens={[
          { id: 'a', titulo: 'Alfa' },
          { id: 'b', titulo: 'Beta' },
        ]}
        onReordenar={onReordenar}
        disabled
      />,
    )
    expect(screen.queryByRole('button', { name: /reordenar/i })).not.toBeInTheDocument()
    expect(screen.getByText('Alfa')).toBeInTheDocument()
    expect(onReordenar).not.toHaveBeenCalled()
  })

  it('reverte a ordem visível quando persistir rejeita', async () => {
    const onReordenar = vi.fn().mockRejectedValue(new Error('409'))
    const user = userEvent.setup()
    render(
      <Lista
        itens={[
          { id: 'a', titulo: 'Alfa' },
          { id: 'b', titulo: 'Beta' },
        ]}
        onReordenar={onReordenar}
      />,
    )
    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{ArrowDown}')
    expect(onReordenar).toHaveBeenCalled()
    const titulos = (await screen.findAllByText(/Alfa|Beta/)).map((el) => el.textContent)
    expect(titulos).toEqual(['Alfa', 'Beta'])
  })

  it('ignora teclas que não são setas', async () => {
    const onReordenar = vi.fn()
    const user = userEvent.setup()
    render(
      <Lista
        itens={[
          { id: 'a', titulo: 'Alfa' },
          { id: 'b', titulo: 'Beta' },
        ]}
        onReordenar={onReordenar}
      />,
    )
    screen.getByRole('button', { name: 'Reordenar Alfa' }).focus()
    await user.keyboard('{Enter}')
    expect(onReordenar).not.toHaveBeenCalled()
  })

  it('lista vazia não oferece handle nem chama persistência', () => {
    const onReordenar = vi.fn()
    render(<Lista itens={[]} onReordenar={onReordenar} />)
    expect(screen.queryByRole('button', { name: /reordenar/i })).not.toBeInTheDocument()
    expect(onReordenar).not.toHaveBeenCalled()
  })
})
