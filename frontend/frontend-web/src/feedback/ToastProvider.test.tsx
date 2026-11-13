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

describe('ToastProvider ícones (T5)', () => {
  it('botão de fechar só com ícone tem nome acessível e ícones ficam fora da árvore acessível', async () => {
    const user = userEvent.setup()

    function Example() {
      const toast = useToast()
      return (
        <button type="button" onClick={() => toast.info('Plano salvo')}>
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
    const fechar = screen.getByRole('button', { name: 'Fechar' })
    expect(fechar).toHaveClass('btn--icon')
    expect(fechar).toHaveTextContent('')

    const icones = screen.getByRole('status').querySelectorAll('svg')
    expect(icones.length).toBe(2)
    for (const icone of icones) {
      expect(icone).toHaveAttribute('aria-hidden', 'true')
    }
  })
})

describe('ToastProvider extras', () => {
  it('useToast fora do provider lança', () => {
    function Broken() {
      useToast()
      return null
    }
    expect(() => render(<Broken />)).toThrow(/ToastProvider/)
  })

  it('fecha o toast pelo botão Fechar', async () => {
    const user = userEvent.setup()
    function Example() {
      const toast = useToast()
      return (
        <button type="button" onClick={() => toast.error('Falhou')}>
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
    expect(screen.getByRole('status')).toHaveTextContent(/falhou/i)
    await user.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('erro crítico usa role alert e duration 0 não auto-fecha', async () => {
    const user = userEvent.setup()
    function Example() {
      const toast = useToast()
      return (
        <button
          type="button"
          onClick={() => toast.error('Crítico', { critical: true, durationMs: 0 })}
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
    expect(screen.getByRole('alert')).toHaveTextContent(/crítico/i)
  })

  it('toast info aparece com a mensagem', async () => {
    const user = userEvent.setup()
    function Example() {
      const toast = useToast()
      return (
        <button type="button" onClick={() => toast.info('Plano salvo')}>
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
    expect(screen.getByRole('status')).toHaveTextContent(/plano salvo/i)
  })

  it('success sem ação não mostra botão Desfazer', async () => {
    const user = userEvent.setup()
    function Example() {
      const toast = useToast()
      return (
        <button type="button" onClick={() => toast.success('Salvo')}>
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
    expect(screen.getByRole('status')).toHaveTextContent(/salvo/i)
    expect(screen.queryByRole('button', { name: /desfazer/i })).not.toBeInTheDocument()
  })
})
