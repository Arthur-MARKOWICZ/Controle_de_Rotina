import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ConflitoBanner } from './ConflitoBanner'

describe('ConflitoBanner omitidos (F2)', () => {
  it('lista omitidos e oferece ação Adicionar', async () => {
    const onAdicionar = vi.fn()
    const user = userEvent.setup()

    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        itensNaoAlocados={[
          {
            tipo: 'HABITO',
            id: 'h-1',
            titulo: 'Meditar',
            duracaoMinutos: 15,
            motivo: 'CAPACIDADE',
          },
        ]}
        onAdicionarOmitido={onAdicionar}
        onManterAssim={() => undefined}
      />,
    )

    expect(screen.getByLabelText('Itens não alocados')).toBeInTheDocument()
    expect(screen.getByText('Meditar')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Adicionar' }))
    expect(onAdicionar).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'h-1', tipo: 'HABITO' }),
    )
  })
})
