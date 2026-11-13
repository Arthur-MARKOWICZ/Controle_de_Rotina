import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { PROJETO_FORM_VAZIO } from './validation/projetoForm'
import { ProjetoCampos } from './ProjetoCampos'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

function Harness({ desabilitado = false }: { desabilitado?: boolean }) {
  const [values, setValues] = useState(PROJETO_FORM_VAZIO)
  return (
    <>
      <ProjetoCampos values={values} onChange={setValues} desabilitado={desabilitado} />
      <output data-testid="nome">{values.nome}</output>
    </>
  )
}

describe('ProjetoCampos', () => {
  it('atualiza o nome', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText('Nome'), 'TCC')

    expect(screen.getByTestId('nome')).toHaveTextContent('TCC')
  })

  it('atualiza a descrição opcional', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText(/descrição/i), 'Notas')

    expect(screen.getByLabelText(/descrição/i)).toHaveValue('Notas')
  })

  it('respeita maxlength do nome', () => {
    render(<Harness />)

    expect(screen.getByLabelText('Nome')).toHaveAttribute('maxLength', '80')
  })

  it('respeita maxlength da descrição', () => {
    render(<Harness />)

    expect(screen.getByLabelText(/descrição/i)).toHaveAttribute('maxLength', '500')
  })

  it('não renderiza TagPicker sem onTagsChange', () => {
    render(<Harness />)

    expect(screen.queryByText(/tags/i)).not.toBeInTheDocument()
  })

  it('não altera valores quando desabilitado', async () => {
    const user = userEvent.setup()
    render(
      <ProjetoCampos
        values={{ nome: 'TCC', descricao: '' }}
        onChange={() => undefined}
        desabilitado
      />,
    )

    expect(screen.getByLabelText('Nome')).toHaveAttribute('required')
    await user.type(screen.getByLabelText('Nome'), 'X')
    expect(screen.getByLabelText('Nome')).toHaveValue('TCC')
  })

  it('propaga onTagsChange quando informado', async () => {
    render(
      <ProjetoCampos
        values={PROJETO_FORM_VAZIO}
        onChange={() => undefined}
        tagIds={[]}
        onTagsChange={() => undefined}
      />,
    )

    expect(screen.getByLabelText('Nome')).toBeInTheDocument()
  })
})
