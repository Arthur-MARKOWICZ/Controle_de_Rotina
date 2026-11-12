export type AcaoCascataPai = 'finalizar' | 'cancelar' | 'excluir'

export function confirmacaoCascataPai(acao: AcaoCascataPai) {
  switch (acao) {
    case 'finalizar':
      return {
        title: 'Finalizar tarefa e subtarefas?',
        message: 'As subtarefas também serão concluídas. Esta ação não concede XP.',
        confirmLabel: 'Finalizar',
        cancelLabel: 'Voltar',
      }
    case 'cancelar':
      return {
        title: 'Cancelar tarefa e subtarefas?',
        message: 'As subtarefas também serão canceladas.',
        confirmLabel: 'Cancelar tarefas',
        cancelLabel: 'Voltar',
      }
    case 'excluir':
      return {
        title: 'Excluir tarefa e subtarefas?',
        message: 'As subtarefas também serão excluídas. Você poderá restaurá-las depois.',
        confirmLabel: 'Excluir',
        cancelLabel: 'Voltar',
      }
  }
}
