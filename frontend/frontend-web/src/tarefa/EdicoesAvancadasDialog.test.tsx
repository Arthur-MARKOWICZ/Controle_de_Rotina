import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import type { TarefaFormValues } from './validation/tarefaForm'
import { EdicoesAvancadasDialog } from './EdicoesAvancadasDialog'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

const VAZIO: TarefaFormValues = {
  titulo: 'Estudar',
  descricao: '',
  prioridade: 'MEDIA',
  prazo: '',
  duracaoMinutos: 30,
  status: 'PENDENTE',
}

const projetoAtivo = {
  id: 'p-1',
  nome: 'TCC',
  descricao: null,
  status: 'ATIVO' as const,
  createdAt: '',
  updatedAt: '',
}

function Harness({
  open = true,
  modo = 'editar' as const,
  projetoId = '',
  disabled = false,
  onClose = vi.fn(),
  onDesvincular,
  desvinculando = false,
}: {
  open?: boolean
  modo?: 'criar' | 'editar'
  projetoId?: string
  disabled?: boolean
  onClose?: () => void
  onDesvincular?: () => void
  desvinculando?: boolean
}) {
  const [values, setValues] = useState(VAZIO)
  const [projeto, setProjeto] = useState(projetoId)
  return (
    <>
      <EdicoesAvancadasDialog
        open={open}
        modo={modo}
        values={values}
        onValuesChange={setValues}
        projetoId={projeto}
        projetoNomeVinculado={projeto ? 'TCC' : null}
        onProjetoChange={(id) => setProjeto(id)}
        projetosAtivos={[projetoAtivo]}
        tagIds={[]}
        onTagsChange={() => undefined}
        onDesvincular={onDesvincular}
        desvinculando={desvinculando}
        disabled={disabled}
        onClose={onClose}
      />
      <output data-testid="status">{values.status}</output>
      <output data-testid="projeto">{projeto}</output>
    </>
  )
}

describe('EdicoesAvancadasDialog', () => {
  it('não renderiza nada quando fechado', () => {
    render(<Harness open={false} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText('Edições avançadas')).not.toBeInTheDocument()
  })

  it('mostra status só no modo editar', () => {
    const { rerender } = render(<Harness modo="criar" />)
    expect(screen.queryByLabelText('Status')).not.toBeInTheDocument()

    rerender(<Harness modo="editar" />)
    expect(screen.getByLabelText('Status')).toBeInTheDocument()
  })

  it('altera o status da tarefa', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.selectOptions(screen.getByLabelText('Status'), 'FINALIZADA')

    expect(screen.getByTestId('status')).toHaveTextContent('FINALIZADA')
  })

  it('não altera campos quando desabilitado', () => {
    render(<Harness disabled />)

    expect(screen.getByLabelText('Status')).toBeDisabled()
    expect(screen.getByLabelText(/projeto/i)).toBeDisabled()
  })

  it('vincula projeto ativo', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.selectOptions(screen.getByLabelText(/projeto/i), 'p-1')

    expect(screen.getByTestId('projeto')).toHaveTextContent('p-1')
  })

  it('mostra Desvincular só com projeto no modo editar e dispara o callback', async () => {
    const onDesvincular = vi.fn()
    const user = userEvent.setup()
    render(<Harness projetoId="p-1" onDesvincular={onDesvincular} />)

    await user.click(screen.getByRole('button', { name: /desvincular/i }))

    expect(onDesvincular).toHaveBeenCalledOnce()
  })

  it('não desvincula enquanto a ação está em voo', () => {
    const onDesvincular = vi.fn()
    render(<Harness projetoId="p-1" onDesvincular={onDesvincular} desvinculando />)

    expect(screen.getByRole('button', { name: /desvinculando/i })).toBeDisabled()
    expect(onDesvincular).not.toHaveBeenCalled()
  })

  it('fecha ao confirmar Pronto sem chamar desvincular', async () => {
    const onClose = vi.fn()
    const onDesvincular = vi.fn()
    const user = userEvent.setup()
    render(<Harness onClose={onClose} onDesvincular={onDesvincular} />)

    await user.click(screen.getByRole('button', { name: /pronto/i }))

    expect(onClose).toHaveBeenCalledOnce()
    expect(onDesvincular).not.toHaveBeenCalled()
  })
})
