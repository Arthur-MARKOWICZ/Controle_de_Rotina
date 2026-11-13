import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { useAtalhosTeclado } from './useAtalhosTeclado'

function Harness({
  enabled = true,
  onNovaTarefa = vi.fn(),
  onAjuda = vi.fn(),
}: {
  enabled?: boolean
  onNovaTarefa?: () => void
  onAjuda?: () => void
}) {
  useAtalhosTeclado({ onNovaTarefa, onAjuda, enabled })
  return <p>ok</p>
}

function renderAtalhos(ui: ReactElement, initial = '/') {
  const router = createMemoryRouter([{ path: '*', element: ui }], { initialEntries: [initial] })
  return { ...render(<RouterProvider router={router} />), router }
}

describe('useAtalhosTeclado', () => {
  it('dispara nova tarefa com N', () => {
    const onNovaTarefa = vi.fn()
    renderAtalhos(<Harness onNovaTarefa={onNovaTarefa} />)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'n' }))

    expect(onNovaTarefa).toHaveBeenCalledOnce()
  })

  it('abre ajuda com ?', () => {
    const onAjuda = vi.fn()
    renderAtalhos(<Harness onAjuda={onAjuda} />)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: '?' }))

    expect(onAjuda).toHaveBeenCalledOnce()
  })

  it('navega G H para o plano', () => {
    const { router } = renderAtalhos(<Harness />, '/tarefas')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'g' }))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'h' }))

    expect(router.state.location.pathname).toBe('/plano')
  })

  it('não dispara atalho com modificador Ctrl', () => {
    const onNovaTarefa = vi.fn()
    renderAtalhos(<Harness onNovaTarefa={onNovaTarefa} />)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', ctrlKey: true }))

    expect(onNovaTarefa).not.toHaveBeenCalled()
  })

  it('não dispara atalho quando o foco está em input', () => {
    const onNovaTarefa = vi.fn()
    renderAtalhos(
      <>
        <input aria-label="título" />
        <Harness onNovaTarefa={onNovaTarefa} />
      </>,
    )
    const input = document.querySelector('input') as HTMLInputElement
    input.focus()
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }))

    expect(onNovaTarefa).not.toHaveBeenCalled()
  })

  it('não faz nada quando desabilitado', () => {
    const onNovaTarefa = vi.fn()
    const onAjuda = vi.fn()
    renderAtalhos(<Harness enabled={false} onNovaTarefa={onNovaTarefa} onAjuda={onAjuda} />)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'n' }))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: '?' }))

    expect(onNovaTarefa).not.toHaveBeenCalled()
    expect(onAjuda).not.toHaveBeenCalled()
  })

  it('ignora G seguido de tecla sem rota (estado de navegação inalterado)', () => {
    const { router } = renderAtalhos(<Harness />, '/tarefas')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'g' }))
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'z' }))

    expect(router.state.location.pathname).toBe('/tarefas')
  })
})
