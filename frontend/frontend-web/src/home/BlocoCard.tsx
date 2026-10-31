import { CheckIcon, PlayIcon, XIcon } from '@phosphor-icons/react'
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
  projetoNome?: string | null
  mostrarJustificativa?: boolean
  diff?: BlocoDiffMarker | null
  statusExecucao?: StatusExecucao
  tempoRestanteLabel?: string | null
  onRemover?: () => void
  removendo?: boolean
  /** Mutação otimista ainda aguardando a API. */
  pendente?: boolean
  acoes?: ReactNode
}

function Marcador({ ordem, status }: { ordem: number; status?: StatusExecucao }) {
  if (status === 'CONCLUIDO') {
    return (
      <span className="plano-bloco-ordem plano-bloco-ordem--concluido">
        <CheckIcon aria-hidden weight="bold" />
      </span>
    )
  }
  if (status === 'EM_ANDAMENTO') {
    return (
      <span className="plano-bloco-ordem plano-bloco-ordem--andamento">
        <PlayIcon aria-hidden weight="fill" />
      </span>
    )
  }
  return <span className="plano-bloco-ordem">{ordem}</span>
}

export function BlocoCard({
  ordem,
  titulo,
  tarefaId,
  duracaoMinutos,
  prioridade,
  prazo,
  projetoNome = null,
  mostrarJustificativa = false,
  diff = null,
  statusExecucao,
  tempoRestanteLabel,
  onRemover,
  removendo = false,
  pendente = false,
  acoes,
}: BlocoCardProps) {
  const justificativa =
    mostrarJustificativa && prazo ? formatarJustificativaBloco(prazo, prioridade) : null

  const className = [
    'plano-bloco',
    diff === 'novo' ? 'plano-bloco--novo' : null,
    diff === 'removido' ? 'plano-bloco--removido' : null,
    statusExecucao === 'CONCLUIDO' ? 'plano-bloco--concluido' : null,
    statusExecucao === 'EM_ANDAMENTO' ? 'plano-bloco--andamento' : null,
    pendente ? 'plano-bloco--pendente' : null,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <li className={className} aria-busy={pendente || undefined}>
      <Marcador ordem={ordem} status={statusExecucao} />
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
          <span className="plano-bloco-duracao">{formatarCapacidadeMinutos(duracaoMinutos)}</span>
          <span className={`plano-bloco-prioridade plano-bloco-prioridade--${prioridade.toLowerCase()}`}>
            {labelPrioridade(prioridade)}
          </span>
          {projetoNome ? <span className="badge badge--projeto">{projetoNome}</span> : null}
          {statusExecucao ? (
            <span className={`status-execucao status-execucao-${statusExecucao.toLowerCase()}`}>
              {labelStatusExecucao(statusExecucao)}
            </span>
          ) : null}
          {tempoRestanteLabel ? (
            <span aria-label="Tempo restante">Restante: {tempoRestanteLabel}</span>
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
          className="btn btn--ghost btn--sm plano-bloco-remover"
          onClick={onRemover}
          disabled={removendo}
          aria-busy={removendo}
          aria-label={`Remover ${titulo}`}
        >
          <XIcon aria-hidden />
          <span className="plano-bloco-acao-rotulo">Remover</span>
        </button>
      ) : null}
      {acoes ? <div className="plano-bloco-acoes-execucao">{acoes}</div> : null}
    </li>
  )
}
