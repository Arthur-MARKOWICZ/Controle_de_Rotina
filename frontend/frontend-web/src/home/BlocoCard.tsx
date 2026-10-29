import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type { StatusExecucao } from '../execucao/api/execucaoApi'
import { formatarCapacidadeMinutos } from '../planner/data'
import type { Prioridade } from '../tarefa/validation/tarefaForm'
import { formatarJustificativaBloco } from './justificativa'
import { labelPrioridade, labelStatusExecucao } from './labels'

export type BlocoDiffMarker = 'novo' | 'removido'

type BlocoCardProps = {
  ordem: number
  titulo: string
  tarefaId: string
  duracaoMinutos: number
  prioridade: Prioridade
  /** API: `string | null`. Não use só `string | undefined` — ver README (null vs undefined). */
  prazo?: string | null
  mostrarJustificativa?: boolean
  diff?: BlocoDiffMarker | null
  statusExecucao?: StatusExecucao
  tempoRestanteLabel?: string | null
  onRemover?: () => void
  removendo?: boolean
  acoes?: ReactNode
}

export function BlocoCard({
  ordem,
  titulo,
  tarefaId,
  duracaoMinutos,
  prioridade,
  prazo,
  mostrarJustificativa = false,
  diff = null,
  statusExecucao,
  tempoRestanteLabel,
  onRemover,
  removendo = false,
  acoes,
}: BlocoCardProps) {
  const justificativa =
    mostrarJustificativa && prazo ? formatarJustificativaBloco(prazo, prioridade) : null

  const className = [
    'plano-bloco',
    diff === 'novo' ? 'plano-bloco--novo' : null,
    diff === 'removido' ? 'plano-bloco--removido' : null,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <li className={className}>
      <span className="plano-bloco-ordem">{ordem}</span>
      <div className="plano-bloco-corpo">
        <p className="plano-bloco-titulo">
          <Link to={`/tarefas/${tarefaId}`}>{titulo}</Link>
          {diff === 'novo' ? (
            <span className="plano-bloco-diff" aria-label="Bloco novo nesta versão">
              Novo
            </span>
          ) : null}
        </p>
        <p className="plano-bloco-meta">
          <span>{formatarCapacidadeMinutos(duracaoMinutos)}</span>
          {' · '}
          <span className={`badge priority-${prioridade.toLowerCase()}`}>
            {labelPrioridade(prioridade)}
          </span>
          {statusExecucao ? (
            <>
              {' · '}
              <span className={`status-execucao status-execucao-${statusExecucao.toLowerCase()}`}>
                {labelStatusExecucao(statusExecucao)}
              </span>
            </>
          ) : null}
          {tempoRestanteLabel ? (
            <>
              {' · '}
              <span aria-label="Tempo restante">Restante: {tempoRestanteLabel}</span>
            </>
          ) : null}
        </p>
        {justificativa ? (
          <p className="plano-bloco-justificativa" title={justificativa}>
            {justificativa}
          </p>
        ) : null}
      </div>
      {onRemover ? (
        <button
          type="button"
          className="btn btn--secondary btn--sm plano-bloco-remover"
          onClick={onRemover}
          disabled={removendo}
          aria-busy={removendo}
          aria-label={`Remover ${titulo}`}
        >
          {removendo ? 'Removendo…' : 'Remover'}
        </button>
      ) : null}
      {acoes ? <div className="plano-bloco-acoes-execucao">{acoes}</div> : null}
    </li>
  )
}
