import { describe, expect, it } from 'vitest'

import { confirmacaoCascataPai, type AcaoCascataPai } from './cascataConfirmacao'

describe('confirmacaoCascataPai', () => {
  it('monta o diálogo de finalizar com aviso de XP', () => {
    expect(confirmacaoCascataPai('finalizar')).toEqual({
      title: 'Finalizar tarefa e subtarefas?',
      message: 'As subtarefas também serão concluídas. Esta ação não concede XP.',
      confirmLabel: 'Finalizar',
      cancelLabel: 'Voltar',
    })
  })

  it('monta o diálogo de cancelar em cascata', () => {
    expect(confirmacaoCascataPai('cancelar')).toEqual({
      title: 'Cancelar tarefa e subtarefas?',
      message: 'As subtarefas também serão canceladas.',
      confirmLabel: 'Cancelar tarefas',
      cancelLabel: 'Voltar',
    })
  })

  it('monta o diálogo de excluir com possibilidade de restaurar', () => {
    expect(confirmacaoCascataPai('excluir')).toEqual({
      title: 'Excluir tarefa e subtarefas?',
      message: 'As subtarefas também serão excluídas. Você poderá restaurá-las depois.',
      confirmLabel: 'Excluir',
      cancelLabel: 'Voltar',
    })
  })

  it('usa Voltar como cancelamento em todas as ações conhecidas', () => {
    const acoes: AcaoCascataPai[] = ['finalizar', 'cancelar', 'excluir']

    for (const acao of acoes) {
      expect(confirmacaoCascataPai(acao).cancelLabel).toBe('Voltar')
    }
  })

  it('não reutiliza o mesmo título para ações diferentes', () => {
    const titulos = [
      confirmacaoCascataPai('finalizar').title,
      confirmacaoCascataPai('cancelar').title,
      confirmacaoCascataPai('excluir').title,
    ]

    expect(new Set(titulos).size).toBe(3)
  })

  it('não promete XP ao finalizar em cascata', () => {
    expect(confirmacaoCascataPai('finalizar').message.toLowerCase()).toContain('não concede xp')
  })

  it('devolve undefined para ação desconhecida sem alterar o contrato das conhecidas', () => {
    expect(confirmacaoCascataPai('finalizar').confirmLabel).toBe('Finalizar')
    expect(confirmacaoCascataPai('excluir' as AcaoCascataPai).confirmLabel).toBe('Excluir')
    expect(
      confirmacaoCascataPai('mover' as unknown as AcaoCascataPai),
    ).toBeUndefined()
  })
})
