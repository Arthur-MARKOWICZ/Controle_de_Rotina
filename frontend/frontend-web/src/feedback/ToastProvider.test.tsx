import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ToastProvider, useToast } from './ToastProvider'

describe('ToastProvider (T1)', () => {
  it('toast com ação Desfazer dispara o callback ao clicar', async () => {
    const onUndo = vi.fn()
    const user = userEvent.setup()

    function Example() {
      const toast = useToast()
      return (
        <button
          type="button"
          onClick={() =>
            toast.success('Tarefa excluída', {
              action: { label: 'Desfazer', onClick: onUndo },
            })
          }
        >
          Disparar
        </button>
      )
    }

    render(
      <ToastProvider>
        <Example />
      </ToastProvider>,
    )

    await user.click(screen.getByRole('button', { name: /disparar/i }))
    expect(screen.getByRole('status')).toHaveTextContent(/tarefa excluída/i)

    await user.click(screen.getByRole('button', { name: /desfazer/i }))
    expect(onUndo).toHaveBeenCalledTimes(1)
  })
})
