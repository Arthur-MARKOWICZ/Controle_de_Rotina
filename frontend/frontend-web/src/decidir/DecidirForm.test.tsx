import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type FormEvent } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { DecidirForm, type DecidirFormValues } from './DecidirForm'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

const VAZIO: DecidirFormValues = {
  titulo: '',
  descricao: '',
  decidirAte: '',
}

function Harness({
  disabled = false,
  erro = null as string | null,
  onSubmit = (e: FormEvent) => e.preventDefault(),
}: {
  disabled?: boolean
  erro?: string | null
  onSubmit?: (e: FormEvent) => void
}) {
  const [values, setValues] = useState(VAZIO)
  const [projetoId, setProjetoId] = useState('')
  return (
    <>
      <DecidirForm
        formId="decidir-teste"
        values={values}
        onValuesChange={setValues}
        projetoId={projetoId}
        onProjetoChange={setProjetoId}
        projetosAtivos={[
          { id: 'p-1', nome: 'TCC', descricao: null, status: 'ATIVO', createdAt: '', updatedAt: '' },
        ]}
        tagIds={[]}
        onTagsChange={() => undefined}
        disabled={disabled}
        erro={erro}
        onSubmit={onSubmit}
      />
      <output data-testid="titulo">{values.titulo}</output>
      <output data-testid="projeto">{projetoId}</output>
    </>
  )
}

describe('DecidirForm', () => {
  it('atualiza o título', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText('Título'), 'Comprar curso')

    expect(screen.getByTestId('titulo')).toHaveTextContent('Comprar curso')
  })

  it('vincula um projeto opcional', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.selectOptions(screen.getByLabelText(/projeto/i), 'p-1')

    expect(screen.getByTestId('projeto')).toHaveTextContent('p-1')
  })

  it('não altera campos quando desabilitado (item promovido)', async () => {
    const user = userEvent.setup()
    render(<Harness disabled />)

    expect(screen.getByLabelText('Título')).toBeDisabled()
    await user.selectOptions(screen.getByLabelText(/projeto/i), 'p-1')
    expect(screen.getByTestId('projeto')).toHaveTextContent('')
  })

  it('mostra alerta de erro sem limpar o formulário', () => {
    render(<Harness erro="Informe um título." />)

    expect(screen.getByText('Informe um título.')).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveValue('')
  })

  it('não mostra alerta quando erro é nulo', () => {
    render(<Harness />)

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('dispara onSubmit no form', async () => {
    const onSubmit = vi.fn((e: FormEvent) => e.preventDefault())
    const user = userEvent.setup()
    render(<Harness onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText('Título'), 'Item')
    await user.click(screen.getByLabelText('Título'))
    screen.getByLabelText('Título').closest('form')?.requestSubmit()

    expect(onSubmit).toHaveBeenCalled()
  })

  it('aceita descrição e decidirAte opcionais', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByLabelText(/descrição/i), 'Notas')
    await user.type(screen.getByLabelText(/decidir até/i), '2026-12-01T10:00')

    expect(screen.getByLabelText(/descrição/i)).toHaveValue('Notas')
    expect(screen.getByLabelText(/decidir até/i)).toHaveValue('2026-12-01T10:00')
  })
})
