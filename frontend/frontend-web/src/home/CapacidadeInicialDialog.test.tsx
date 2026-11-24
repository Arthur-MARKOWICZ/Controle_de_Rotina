import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { CapacidadeInicialDialog } from './CapacidadeInicialDialog'

type Props = Partial<Parameters<typeof CapacidadeInicialDialog>[0]>

function renderDialog(props: Props = {}) {
  const handlers = {
    onUsarDiasUteis: vi.fn(),
    onSoHoje: vi.fn(),
    onFechar: vi.fn(),
  }
  render(
    <MemoryRouter>
      <CapacidadeInicialDialog
        aberto
        salvando={false}
        erro={null}
        {...handlers}
        {...props}
      />
    </MemoryRouter>,
  )
  return handlers
}

/** Gatilho real + diálogo controlado, para verificar a volta do foco. */
function ComGatilho() {
  const [aberto, setAberto] = useState(false)
  return (
    <MemoryRouter>
      <button type="button" onClick={() => setAberto(true)}>
        Gerar plano de hoje
      </button>
      <CapacidadeInicialDialog
        aberto={aberto}
        salvando={false}
        erro={null}
        onUsarDiasUteis={vi.fn()}
        onSoHoje={vi.fn()}
        onFechar={() => setAberto(false)}
      />
    </MemoryRouter>
  )
}

describe('CapacidadeInicialDialog', () => {
  it('deveFocarTituloEChamarUsarDiasUteisAoClicarNoPreset', async () => {
    const user = userEvent.setup()
    const { onUsarDiasUteis } = renderDialog()

    const titulo = screen.getByRole('heading', { name: 'Quanto tempo você tem por dia?' })
    expect(titulo).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Usar dias úteis 4h' }))

    expect(onUsarDiasUteis).toHaveBeenCalledTimes(1)
  })

  it('deveEnviarHorasDoStepperAoEscolherSoHoje', async () => {
    const user = userEvent.setup()
    const { onSoHoje } = renderDialog()

    await user.click(screen.getByRole('button', { name: 'Aumentar horas de hoje' }))
    await user.click(screen.getByRole('button', { name: 'Só hoje' }))

    expect(onSoHoje).toHaveBeenCalledWith(5)
  })

  it('naoDeveRenderizarNadaQuandoFechado', () => {
    renderDialog({ aberto: false })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText('Quanto tempo você tem por dia?')).not.toBeInTheDocument()
  })

  it('deveFecharComEscEDevolverFocoAoBotaoDeOrigem', async () => {
    const user = userEvent.setup()
    render(<ComGatilho />)
    const gatilho = screen.getByRole('button', { name: 'Gerar plano de hoje' })

    await user.click(gatilho)
    expect(screen.getByRole('heading', { name: 'Quanto tempo você tem por dia?' })).toHaveFocus()
    await user.keyboard('{Escape}')

    expect(screen.queryByText('Quanto tempo você tem por dia?')).not.toBeInTheDocument()
    expect(gatilho).toHaveFocus()
  })

  it('naoDeveFecharNemPermitirNovoCliqueEnquantoSalva', async () => {
    const user = userEvent.setup()
    const { onFechar, onUsarDiasUteis } = renderDialog({ salvando: true })

    await user.keyboard('{Escape}')

    expect(onFechar).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Salvando…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Só hoje' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Agora não' })).toBeDisabled()
    expect(onUsarDiasUteis).not.toHaveBeenCalled()
  })

  it('deveTravarStepperEntre1e16Horas', async () => {
    const user = userEvent.setup()
    renderDialog()
    const diminuir = screen.getByRole('button', { name: 'Diminuir horas de hoje' })
    const aumentar = screen.getByRole('button', { name: 'Aumentar horas de hoje' })

    for (let i = 0; i < 5; i++) {
      await user.click(diminuir)
    }
    expect(screen.getByLabelText('Horas de hoje')).toHaveTextContent('1h')
    expect(diminuir).toBeDisabled()

    for (let i = 0; i < 20; i++) {
      if (!aumentar.hasAttribute('disabled')) {
        await user.click(aumentar)
      }
    }
    expect(screen.getByLabelText('Horas de hoje')).toHaveTextContent('16h')
    expect(aumentar).toBeDisabled()
  })

  it('deveExibirErroComoAlertaDentroDoDialogo', () => {
    renderDialog({ erro: 'A capacidade mudou, tente de novo.' })

    expect(screen.getByRole('alert')).toHaveTextContent('A capacidade mudou, tente de novo.')
  })

  it('deveChamarOnFecharEmAgoraNaoELevarLinkParaCapacidade', async () => {
    const user = userEvent.setup()
    const { onFechar, onUsarDiasUteis, onSoHoje } = renderDialog()

    expect(screen.getByRole('link', { name: 'Configurar a semana' })).toHaveAttribute(
      'href',
      '/capacidade',
    )
    await user.click(screen.getByRole('button', { name: 'Agora não' }))

    expect(onFechar).toHaveBeenCalledTimes(1)
    expect(onUsarDiasUteis).not.toHaveBeenCalled()
    expect(onSoHoje).not.toHaveBeenCalled()
  })
})
