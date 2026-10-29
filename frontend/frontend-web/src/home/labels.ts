import type { StatusExecucao } from '../execucao/api/execucaoApi'
import type { StatusPlano } from '../planner/api/planoApi'
import type { Prioridade, StatusTarefa } from '../tarefa/validation/tarefaForm'

export const PRIORIDADE_LABEL: Record<Prioridade, string> = {
  ALTA: 'Alta',
  MEDIA: 'Média',
  BAIXA: 'Baixa',
}

export const STATUS_TAREFA_LABEL: Record<StatusTarefa, string> = {
  PENDENTE: 'Pendente',
  EM_PROGRESSO: 'Em progresso',
  FINALIZADA: 'Finalizada',
  ATRASADA: 'Atrasada',
  CANCELADA: 'Cancelada',
}

export const STATUS_PLANO_LABEL: Record<StatusPlano, string> = {
  RASCUNHO: 'Rascunho',
  APROVADO: 'Aprovado',
  DESCARTADO: 'Descartado',
}

export const STATUS_EXECUCAO_LABEL: Record<StatusExecucao, string> = {
  NAO_INICIADO: 'Não iniciado',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDO: 'Concluído',
  ABORTADO: 'Abortado',
}

export function labelPrioridade(prioridade: Prioridade): string {
  return PRIORIDADE_LABEL[prioridade]
}

export function labelStatusTarefa(status: StatusTarefa): string {
  return STATUS_TAREFA_LABEL[status]
}

export function labelStatusExecucao(status: StatusExecucao): string {
  return STATUS_EXECUCAO_LABEL[status]
}

export function labelStatusPlano(status: StatusPlano): string {
  return STATUS_PLANO_LABEL[status]
}
