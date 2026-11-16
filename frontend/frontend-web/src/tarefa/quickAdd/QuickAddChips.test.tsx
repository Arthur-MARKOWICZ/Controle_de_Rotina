import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ERRO_DURACAO, interpretarQuickAdd } from './interpretarQuickAdd'
import { QuickAddChips } from './QuickAddChips'

const REF = new Date('2026-11-18T13:00:00.000Z')
const CATALOGO = [{ id: 't1', nome: 'Faculdade', cor: '#123456' }]

function tokensDe(texto: string) {
  return interpretarQuickAdd(texto, { ref: REF, tagsCatalogo: CATALOGO }).tokens
}

function renderChips(texto: string, confirmadas: string[] = [], disabled = false) {
  const onRemover = vi.fn()
  const onAlternarTag = vi.fn()
  render(
    <QuickAddChips
      tokens={tokensDe(texto)}
      tagsConfirmadas={new Set(confirmadas)}
      onRemover={onRemover}
      onAlternarTag={onAlternarTag}
      disabled={disabled}
    />,
  )
  return { onRemover, onAlternarTag }
}

describe('QuickAddChips', () => {
  it('deveRenderizarOsQuatroTiposDeChip', () => {
    renderChips('Estudar 2h sex !alta #faculdade')
    const lista = screen.getByRole('list', { name: 'Valores interpretados' })
    expect(lista).toHaveTextContent('2h')
    expect(lista).toHaveTextContent('sex 20/11')
    expect(lista).toHaveTextContent('Alta')
    expect(lista).toHaveTextContent('#Faculdade')
    expect(screen.getAllByRole('listitem')).toHaveLength(4)
  })

  it('deveChamarOnRemoverComOTokenAoClicarNoX', async () => {
    const { onRemover } = renderChips('Ler 2h')
    await userEvent.click(screen.getByRole('button', { name: 'Remover duração 2h' }))
    expect(onRemover).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'duracao', textoOriginal: '2h' }))
  })

  it('deveChamarOnAlternarTagAoConfirmarTagPendente', async () => {
    const { onAlternarTag } = renderChips('Treino #academia')
    expect(screen.getByText('criar #academia?')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Criar tag academia' }))
    expect(onAlternarTag).toHaveBeenCalledWith(expect.objectContaining({ nome: 'academia', tag: null }))
  })

  it('deveMostrarTagConfirmadaComOpcaoDeDesfazer', () => {
    renderChips('Treino #academia', ['academia'])
    expect(screen.getByText('#academia (nova)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Desfazer criação da tag academia' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('deveExibirAlertaComAMensagemExataQuandoDuracaoInvalida', () => {
    renderChips('Ligar 20min')
    expect(screen.getByRole('alert')).toHaveTextContent(ERRO_DURACAO)
  })

  it('naoDeveRenderizarNadaQuandoNaoHaTokens', () => {
    const { container } = render(
      <QuickAddChips tokens={[]} tagsConfirmadas={new Set()} onRemover={vi.fn()} onAlternarTag={vi.fn()} />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('deveDarNomeAcessivelPorChip', () => {
    renderChips('Ler 1h30 amanhã !baixa #faculdade')
    expect(screen.getByRole('button', { name: 'Remover duração 1h30' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remover prazo qui 19/11' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remover prioridade Baixa' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remover tag Faculdade' })).toBeInTheDocument()
  })

  it('naoDeveChamarCallbacksQuandoDesabilitado', async () => {
    const { onRemover, onAlternarTag } = renderChips('Treino 1h #academia', [], true)
    await userEvent.click(screen.getByRole('button', { name: 'Remover duração 1h' }))
    await userEvent.click(screen.getByRole('button', { name: 'Criar tag academia' }))
    expect(onRemover).not.toHaveBeenCalled()
    expect(onAlternarTag).not.toHaveBeenCalled()
  })
})
