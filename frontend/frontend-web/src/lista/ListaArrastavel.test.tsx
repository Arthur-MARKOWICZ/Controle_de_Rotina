import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useMemo } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { ArrasteHandle } from './ArrasteHandle'
import { useListaArrastavel } from './useListaArrastavel'

function Lista({
  itens,
  onReordenar,
}: {
  itens: { id: string; titulo: string }[]
  onReordenar: (ids: string[]) => void
}) {
  const ids = useMemo(() => itens.map((item) => item.id), [itens])
  const { idsVisiveis, handleProps, registrarItem } = useListaArrastavel({
    ids,
    onReordenar,
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
})
