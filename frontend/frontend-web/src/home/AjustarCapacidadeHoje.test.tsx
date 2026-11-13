import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { AjustarCapacidadeHoje } from './AjustarCapacidadeHoje'

function renderAjuste(overrides: Partial<Parameters<typeof AjustarCapacidadeHoje>[0]> = {}) {
  const props = {
    aberto: false,
    horasIniciais: 4,
    salvando: false,
    mostrarCtaGerar: false,
    gerando: false,
    onAbrir: vi.fn(),
    onFechar: vi.fn(),
    onSalvar: vi.fn(),
    onGerarVersao: vi.fn(),
    ...overrides,
  }
  render(<AjustarCapacidadeHoje {...props} />)
  return props
}

describe('AjustarCapacidadeHoje', () => {
  it('mostra o gatilho fechado e abre ao clicar', async () => {
    const props = renderAjuste()
    await userEvent.setup().click(screen.getByRole('button', { name: /ajustar capacidade de hoje/i }))
    expect(props.onAbrir).toHaveBeenCalledOnce()
    expect(props.onSalvar).not.toHaveBeenCalled()
  })

  it('não renderiza nada quando fechado sem gatilho nem CTA', () => {
    const { container } = render(
      <AjustarCapacidadeHoje
        aberto={false}
        horasIniciais={4}
        salvando={false}
        mostrarCtaGerar={false}
        gerando={false}
        mostrarGatilho={false}
        onAbrir={vi.fn()}
        onFechar={vi.fn()}
        onSalvar={vi.fn()}
        onGerarVersao={vi.fn()}
      />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('não diminui abaixo de zero (estado inalterado)', async () => {
    const props = renderAjuste({ aberto: true, horasIniciais: 0 })
    const menos = screen.getByRole('button', { name: /diminuir horas/i })
    expect(menos).toBeDisabled()
    await userEvent.setup().click(menos)
    expect(screen.getByLabelText('Horas de hoje')).toHaveTextContent('0h')
    expect(props.onSalvar).not.toHaveBeenCalled()
  })

  it('não aumenta acima de 16', async () => {
    renderAjuste({ aberto: true, horasIniciais: 16 })
    const mais = screen.getByRole('button', { name: /aumentar horas/i })
    expect(mais).toBeDisabled()
    await userEvent.setup().click(mais)
    expect(screen.getByLabelText('Horas de hoje')).toHaveTextContent('16h')
  })

  it('salva as horas ajustadas', async () => {
    const props = renderAjuste({ aberto: true, horasIniciais: 4 })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /aumentar horas/i }))
    await user.click(screen.getByRole('button', { name: /salvar capacidade de hoje/i }))
    expect(props.onSalvar).toHaveBeenCalledWith(5)
  })

  it('fecha sem salvar quando o usuário cancela', async () => {
    const props = renderAjuste({ aberto: true, horasIniciais: 4 })
    await userEvent.setup().click(screen.getByRole('button', { name: /cancelar|fechar/i }))
    expect(props.onFechar).toHaveBeenCalledOnce()
    expect(props.onSalvar).not.toHaveBeenCalled()
  })

  it('mostra erro de salvando desabilitando os steppers', () => {
    renderAjuste({ aberto: true, horasIniciais: 4, salvando: true })
    expect(screen.getByRole('button', { name: /diminuir horas/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /aumentar horas/i })).toBeDisabled()
  })
})
