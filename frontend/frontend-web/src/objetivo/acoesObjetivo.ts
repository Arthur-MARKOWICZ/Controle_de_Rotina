import {
  arquivarObjetivo,
  concluirObjetivo,
  reabrirObjetivo,
  type ObjetivoResponse,
  type StatusObjetivo,
} from './api/objetivoApi'

export type AcaoObjetivo = 'concluir' | 'arquivar' | 'reabrir'

/** Ações válidas por status: o card e o detalhe oferecem exatamente estas. */
export const ACOES_POR_STATUS: Record<StatusObjetivo, AcaoObjetivo[]> = {
  ATIVO: ['concluir', 'arquivar'],
  CONCLUIDO: ['arquivar', 'reabrir'],
  ARQUIVADO: ['reabrir'],
}

export const ROTULO_ACAO: Record<AcaoObjetivo, { label: string; andamento: string }> = {
  concluir: { label: 'Concluir', andamento: 'Concluindo…' },
  arquivar: { label: 'Arquivar', andamento: 'Arquivando…' },
  reabrir: { label: 'Reabrir', andamento: 'Reabrindo…' },
}

export const EXECUTAR_ACAO: Record<AcaoObjetivo, (id: string) => Promise<ObjetivoResponse>> = {
  concluir: concluirObjetivo,
  arquivar: arquivarObjetivo,
  reabrir: reabrirObjetivo,
}

export const SUCESSO_ACAO: Record<AcaoObjetivo, string> = {
  concluir: 'Objetivo concluído',
  arquivar: 'Objetivo arquivado',
  reabrir: 'Objetivo reaberto',
}

export const FALHA_ACAO: Record<AcaoObjetivo, string> = {
  concluir: 'Não foi possível concluir o objetivo.',
  arquivar: 'Não foi possível arquivar o objetivo.',
  reabrir: 'Não foi possível reabrir o objetivo.',
}

type Confirmacao = { title: string; message: string; confirmLabel: string }

/** Só concluir e arquivar pedem confirmação; reabrir chama a API direto. */
export const CONFIRMACAO_ACAO: Partial<Record<AcaoObjetivo, (titulo: string) => Confirmacao>> = {
  concluir: (titulo) => ({
    title: 'Concluir objetivo?',
    message: `“${titulo}” sai dos ativos. Você pode reabrir depois.`,
    confirmLabel: 'Concluir',
  }),
  arquivar: (titulo) => ({
    title: 'Arquivar objetivo?',
    message: `“${titulo}” fica arquivado. Você pode reabrir depois.`,
    confirmLabel: 'Arquivar',
  }),
}
