/** Como a revisão terminou em relação ao plano da próxima semana. */
export type DesfechoPlano = 'APROVADO' | 'RECUSADO' | 'NAO_TRATADO'

export type ResumoSessaoRevisao = {
  tarefasTratadas: number
  itensDecididos: number
  desfecho: DesfechoPlano
}
