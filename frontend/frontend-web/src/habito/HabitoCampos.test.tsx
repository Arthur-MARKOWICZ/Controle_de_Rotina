import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { HABITO_FORM_VAZIO, type HabitoFormValues } from './validation/habitoForm'
import { HabitoCampos } from './HabitoCampos'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

function Harness({
  inicial = HABITO_FORM_VAZIO,
  desabilitado = false,
}: {
  inicial?: HabitoFormValues
  desabilitado?: boolean
}) {
  const [values, setValues] = useState(inicial)
  return (
    <>
      <HabitoCampos
        values={values}
        onChange={setValues}
        desabilitado={desabilitado}
        tagIds={[]}
        onTagsChange={() => undefined}
      />
      <output data-testid="titulo">{values.titulo}</output>
      <output data-testid="dias">{values.diasSemana.join(',')}</output>
      <output data-testid="horario">{values.horarioPreferencial}</output>
    </>
  )
}

describe('HabitoCampos', () => {
  it('atualiza o título digitado', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText('Título'), 'Meditar')

    expect(screen.getByTestId('titulo')).toHaveTextContent('Meditar')
  })

  it('alterna um dia da semana sem perder os demais', async () => {
    const user = userEvent.setup()
    render(<Harness inicial={{ ...HABITO_FORM_VAZIO, diasSemana: ['SEGUNDA'] }} />)

    await user.click(screen.getByRole('checkbox', { name: /quarta/i }))
    expect(screen.getByTestId('dias')).toHaveTextContent('SEGUNDA,QUARTA')

    await user.click(screen.getByRole('checkbox', { name: /segunda/i }))
    expect(screen.getByTestId('dias')).toHaveTextContent('QUARTA')
  })

  it('aceita duração vazia enquanto o usuário apaga o campo', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.clear(screen.getByLabelText('Duração (minutos)'))

    expect(screen.getByLabelText('Duração (minutos)')).toHaveValue(null)
  })

  it('não altera valores quando desabilitado (hábito excluído)', async () => {
    const user = userEvent.setup()
    render(
      <Harness
        inicial={{ ...HABITO_FORM_VAZIO, titulo: 'Correr', diasSemana: ['SEGUNDA'] }}
        desabilitado
      />,
    )

    expect(screen.getByLabelText('Título')).toBeDisabled()
    expect(screen.getByLabelText('Duração (minutos)')).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: /terça/i }))
    expect(screen.getByTestId('dias')).toHaveTextContent('SEGUNDA')
  })

  it('mostra e limpa o horário preferencial', async () => {
    const user = userEvent.setup()
    render(<Harness inicial={{ ...HABITO_FORM_VAZIO, horarioPreferencial: '07:00' }} />)

    expect(screen.getByLabelText(/horário preferencial/i)).toHaveValue('07:00')
    await user.click(screen.getByRole('button', { name: /limpar horário/i }))
    expect(screen.getByTestId('horario')).toHaveTextContent('')
    expect(screen.queryByRole('button', { name: /limpar horário/i })).not.toBeInTheDocument()
  })

  it('não renderiza limpar horário quando o campo está vazio', () => {
    render(<Harness />)

    expect(screen.queryByRole('button', { name: /limpar horário/i })).not.toBeInTheDocument()
  })

  it('propaga descrição opcional', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText(/descrição/i), 'Manhã')

    expect(screen.getByLabelText(/descrição/i)).toHaveValue('Manhã')
  })
})
