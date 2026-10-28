import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ConflitoBanner } from './ConflitoBanner'

describe('ConflitoBanner', () => {
  it('T2: renderiza ação que dispara callback', async () => {
    const onAumentar = vi.fn()
    const user = userEvent.setup()

    render(
      <ConflitoBanner
        mensagem="Capacidade insuficiente"
        temConflito
        onAumentarCapacidade={onAumentar}
        onManterAssim={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: /aumentar capacidade de hoje/i }))
    expect(onAumentar).toHaveBeenCalledOnce()
  })
})
