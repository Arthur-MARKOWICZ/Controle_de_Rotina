import { render, screen, within } from '@testing-library/react'
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
  modo?: 'criar' | 'editar'
  disabled?: boolean
  duracaoSomenteLeitura?: boolean
  onAbrirDescricao?: () => void
}

function Harness({
  inicial,
  modo = 'criar',
  disabled = false,
  duracaoSomenteLeitura = false,
  onAbrirDescricao = vi.fn(),
}: HarnessProps) {
  const [values, setValues] = useState<TarefaFormValues>({ ...VAZIO, ...inicial })
  return (
    <TarefaForm
      formId="teste-form"
      modo={modo}
      values={values}
      onValuesChange={setValues}
      projetoId=""
      projetoNomeVinculado={null}
      onProjetoChange={() => {}}
      projetosAtivos={[]}
      disabled={disabled}
      duracaoSomenteLeitura={duracaoSomenteLeitura}
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

describe('TarefaForm — duração do pai', () => {
  it('com duracaoSomenteLeitura o campo fica disabled e explica a soma', () => {
    render(<Harness inicial={{ duracaoMinutos: 120 }} duracaoSomenteLeitura />)

    const campo = screen.getByLabelText(/^duração \(minutos\)$/i)
    expect(campo).toBeDisabled()
    expect(campo).toHaveValue(120)
    expect(screen.getByText(/soma das subtarefas/i)).toBeInTheDocument()
  })
})

describe('TarefaForm — prazo em modal', () => {
  it('mostra botão Definir prazo e esconde chips e datetime-local', () => {
    render(<Harness />)

    expect(screen.getByRole('button', { name: /definir prazo/i })).toBeInTheDocument()
    expect(screen.queryByLabelText(/prazo \(opcional\)/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^hoje$/i })).not.toBeInTheDocument()
  })

  it('abre o modal, aplica atalho e mostra Editar prazo no botão', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: /definir prazo/i }))
    const dialogo = screen.getByRole('dialog', { name: /^prazo$/i })
    expect(within(dialogo).getByLabelText(/prazo \(opcional\)/i)).toBeInTheDocument()

    await user.click(within(dialogo).getByRole('button', { name: /^amanhã$/i }))
    await user.click(within(dialogo).getByRole('button', { name: /^aplicar$/i }))

    expect(screen.queryByRole('dialog', { name: /^prazo$/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /editar prazo/i })).toBeInTheDocument()
  })

  it('cancelar descarta o rascunho do prazo', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: /definir prazo/i }))
    const dialogo = screen.getByRole('dialog', { name: /^prazo$/i })
    await user.click(within(dialogo).getByRole('button', { name: /^amanhã$/i }))
    await user.click(within(dialogo).getByRole('button', { name: /^cancelar$/i }))

    expect(screen.getByRole('button', { name: /definir prazo/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /editar prazo/i })).not.toBeInTheDocument()
  })
})

describe('TarefaForm — edições avançadas', () => {
  it('esconde status, tags e projeto até abrir o modal', () => {
    render(<Harness modo="editar" inicial={{ status: 'PENDENTE' }} />)

    expect(screen.getByRole('button', { name: /edições avançadas/i })).toBeInTheDocument()
    expect(screen.queryByLabelText(/^status$/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^projeto \(opcional\)$/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/^tags$/i)).not.toBeInTheDocument()
  })

  it('no editar o modal mostra status, tags e projeto', async () => {
    const user = userEvent.setup()
    render(<Harness modo="editar" inicial={{ status: 'PENDENTE' }} />)

    await user.click(screen.getByRole('button', { name: /edições avançadas/i }))
    const dialogo = screen.getByRole('dialog', { name: /edições avançadas/i })

    expect(within(dialogo).getByLabelText(/^status$/i)).toBeInTheDocument()
    expect(within(dialogo).getByLabelText(/^projeto \(opcional\)$/i)).toBeInTheDocument()
    expect(within(dialogo).getByText(/^tags$/i)).toBeInTheDocument()
  })

  it('no criar o modal não mostra status', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: /edições avançadas/i }))
    const dialogo = screen.getByRole('dialog', { name: /edições avançadas/i })

    expect(within(dialogo).queryByLabelText(/^status$/i)).not.toBeInTheDocument()
    expect(within(dialogo).getByLabelText(/^projeto \(opcional\)$/i)).toBeInTheDocument()
  })

  it('com disabled os botões de prazo e edições avançadas não abrem', async () => {
    const user = userEvent.setup()
    render(<Harness disabled />)

    expect(screen.getByRole('button', { name: /definir prazo/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /edições avançadas/i })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /definir prazo/i }))
    await user.click(screen.getByRole('button', { name: /edições avançadas/i }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
