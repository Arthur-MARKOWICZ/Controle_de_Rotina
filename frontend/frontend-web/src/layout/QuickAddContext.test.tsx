import { render, renderHook, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { QuickAddProvider, useQuickAdd } from './QuickAddContext'

vi.mock('../tarefa/QuickAddTarefaDialog', () => ({
  QuickAddTarefaDialog: ({
    open,
    onCriada,
  }: {
    open: boolean
    onClose: () => void
    onCriada: () => void
  }) =>
    open ? (
      <div role="dialog">
        Quick add
        <button type="button" onClick={onCriada}>
          Criada
        </button>
      </div>
    ) : null,
}))

function Probe() {
  const { abrir, novaTarefa, assinarCriada, registrarFocoInline } = useQuickAdd()
  return (
    <div>
      <button type="button" onClick={abrir}>
        Abrir topo
      </button>
      <button type="button" onClick={novaTarefa}>
        Nova tarefa
      </button>
      <button
        type="button"
        onClick={() => {
          assinarCriada(() => {
            document.body.dataset.criada = '1'
          })
        }}
      >
        Assinar
      </button>
      <button
        type="button"
        onClick={() => registrarFocoInline(() => {
          document.body.dataset.foco = '1'
        })}
      >
        Registrar inline
      </button>
    </div>
  )
}

function renderQuick() {
  return render(
    <MemoryRouter>
      <QuickAddProvider>
        <Probe />
      </QuickAddProvider>
    </MemoryRouter>,
  )
}

describe('QuickAddContext', () => {
  it('abre o dialog pelo botão do topo', async () => {
    const user = userEvent.setup()
    renderQuick()

    await user.click(screen.getByRole('button', { name: /abrir topo/i }))

    expect(screen.getByRole('dialog')).toHaveTextContent('Quick add')
  })

  it('foca o inline quando registrado em vez de abrir o dialog', async () => {
    const user = userEvent.setup()
    renderQuick()

    await user.click(screen.getByRole('button', { name: /registrar inline/i }))
    await user.click(screen.getByRole('button', { name: /nova tarefa/i }))

    expect(document.body.dataset.foco).toBe('1')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('abre o dialog em novaTarefa se não há inline', async () => {
    const user = userEvent.setup()
    renderQuick()

    await user.click(screen.getByRole('button', { name: /nova tarefa/i }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('notifica assinantes ao criar', async () => {
    const user = userEvent.setup()
    delete document.body.dataset.criada
    renderQuick()

    await user.click(screen.getByRole('button', { name: /assinar/i }))
    await user.click(screen.getByRole('button', { name: /abrir topo/i }))
    await user.click(screen.getByRole('button', { name: /criada/i }))

    expect(document.body.dataset.criada).toBe('1')
  })

  it('não notifica depois de cancelar a inscrição', async () => {
    function ProbeOff() {
      const { abrir, assinarCriada } = useQuickAdd()
      return (
        <div>
          <button
            type="button"
            onClick={() => {
              const off = assinarCriada(() => {
                document.body.dataset.criada = '1'
              })
              off()
            }}
          >
            Assinar e sair
          </button>
          <button type="button" onClick={abrir}>
            Abrir
          </button>
        </div>
      )
    }
    const user = userEvent.setup()
    delete document.body.dataset.criada
    render(
      <MemoryRouter>
        <QuickAddProvider>
          <ProbeOff />
        </QuickAddProvider>
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: /assinar e sair/i }))
    await user.click(screen.getByRole('button', { name: /abrir/i }))
    await user.click(screen.getByRole('button', { name: /criada/i }))

    expect(document.body.dataset.criada).toBeUndefined()
  })

  it('volta a abrir o dialog depois de limpar o foco inline', async () => {
    function ProbeCleanup() {
      const { novaTarefa, registrarFocoInline } = useQuickAdd()
      return (
        <div>
          <button
            type="button"
            onClick={() => {
              const off = registrarFocoInline(() => {
                document.body.dataset.foco = '1'
              })
              off()
            }}
          >
            Registrar e limpar
          </button>
          <button type="button" onClick={novaTarefa}>
            Nova
          </button>
        </div>
      )
    }
    const user = userEvent.setup()
    delete document.body.dataset.foco
    render(
      <MemoryRouter>
        <QuickAddProvider>
          <ProbeCleanup />
        </QuickAddProvider>
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: /registrar e limpar/i }))
    await user.click(screen.getByRole('button', { name: /nova/i }))

    expect(document.body.dataset.foco).toBeUndefined()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('lança fora do provider', () => {
    expect(() => renderHook(() => useQuickAdd())).toThrow(
      /useQuickAdd deve ser usado dentro de QuickAddProvider/,
    )
  })
})
