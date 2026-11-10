import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type FormEvent } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { TarefaForm } from './TarefaForm'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))
import type { TarefaFormValues } from './validation/tarefaForm'

const VAZIO: TarefaFormValues = {
  titulo: '',
  descricao: '',
  prioridade: '',
  prazo: '',
  duracaoMinutos: '',
  status: '',
}

type HarnessProps = {
  inicial?: Partial<TarefaFormValues>
  disabled?: boolean
  onAbrirDescricao?: () => void
}

function Harness({ inicial, disabled = false, onAbrirDescricao = vi.fn() }: HarnessProps) {
  const [values, setValues] = useState<TarefaFormValues>({ ...VAZIO, ...inicial })
  return (
    <TarefaForm
      formId="teste-form"
      modo="criar"
      values={values}
      onValuesChange={setValues}
      projetoId=""
      projetoNomeVinculado={null}
      onProjetoChange={() => {}}
      projetosAtivos={[]}
      disabled={disabled}
      erro={null}
      onSubmit={(e: FormEvent) => e.preventDefault()}
      onAbrirDescricao={onAbrirDescricao}
      tagIds={[]}
      onTagsChange={() => {}}
    />
  )
}

describe('TarefaForm — entrada de descrição', () => {
  it('mostra “Adicionar descrição” quando vazia e não renderiza textarea inline', () => {
    render(<Harness />)

    expect(screen.getByRole('button', { name: /adicionar descrição/i })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /descrição/i })).not.toBeInTheDocument()
  })

  it('mostra preview truncado quando há descrição', () => {
    render(<Harness inicial={{ descricao: 'Notas longas sobre o TCC' }} />)

    expect(screen.getByRole('button', { name: /notas longas sobre o tcc/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /adicionar descrição/i })).not.toBeInTheDocument()
  })

  it('acionar a linha chama onAbrirDescricao', async () => {
    const onAbrirDescricao = vi.fn()
    const user = userEvent.setup()
    render(<Harness onAbrirDescricao={onAbrirDescricao} />)

    await user.click(screen.getByRole('button', { name: /adicionar descrição/i }))

    expect(onAbrirDescricao).toHaveBeenCalledTimes(1)
  })

  it('com disabled não chama onAbrirDescricao', async () => {
    const onAbrirDescricao = vi.fn()
    const user = userEvent.setup()
    render(<Harness disabled onAbrirDescricao={onAbrirDescricao} />)

    const linha = screen.getByRole('button', { name: /adicionar descrição/i })
    expect(linha).toBeDisabled()
    await user.click(linha)

    expect(onAbrirDescricao).not.toHaveBeenCalled()
  })
})
