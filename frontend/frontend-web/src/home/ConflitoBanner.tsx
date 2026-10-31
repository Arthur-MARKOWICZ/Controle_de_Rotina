import { WarningCircleIcon } from '@phosphor-icons/react'

type ConflitoBannerProps = {
  mensagem: string
  temConflito: boolean
  onRemoverMenorPrioridade?: () => void
  onAumentarCapacidade?: () => void
  onManterAssim?: () => void
  removendo?: boolean
}

export function ConflitoBanner({
  mensagem,
  temConflito,
  onRemoverMenorPrioridade,
  onAumentarCapacidade,
  onManterAssim,
  removendo = false,
}: ConflitoBannerProps) {
  const acionavel = temConflito && (onRemoverMenorPrioridade || onAumentarCapacidade || onManterAssim)

  return (
    <div
      className={temConflito ? 'plano-dia-banner conflito' : 'plano-dia-banner'}
      role="status"
      aria-label={temConflito ? 'Conflito no plano' : 'Mensagem do plano'}
    >
      <div className="plano-dia-banner__corpo">
        <WarningCircleIcon className="plano-dia-banner__icone" aria-hidden />
        <p className="plano-dia-banner__texto">{mensagem}</p>
      </div>
      {acionavel ? (
        <div className="plano-conflito-acoes">
          {onRemoverMenorPrioridade ? (
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={onRemoverMenorPrioridade}
              disabled={removendo}
              aria-busy={removendo}
            >
              Remover tarefa de menor prioridade
            </button>
          ) : null}
          {onAumentarCapacidade ? (
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={onAumentarCapacidade}
            >
              Aumentar capacidade de hoje
            </button>
          ) : null}
          {onManterAssim ? (
            <button type="button" className="btn btn--ghost btn--sm" onClick={onManterAssim}>
              Manter assim
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
