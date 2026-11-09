import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { EmptyStatePlano } from './EmptyStatePlano'

describe('EmptyStatePlano', () => {
  it('T2: empty oferece caminho de volta a /tarefas', () => {
    render(
      <MemoryRouter>
        <EmptyStatePlano gerando={false} onGerar={vi.fn()} onAjustarCapacidade={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: /^gerar plano de hoje$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /voltar às tarefas/i })).toHaveAttribute(
      'href',
      '/tarefas',
    )
  })
})
