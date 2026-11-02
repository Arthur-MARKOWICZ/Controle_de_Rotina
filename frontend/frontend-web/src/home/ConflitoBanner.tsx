import { WarningCircleIcon } from '@phosphor-icons/react'

import { formatarCapacidadeMinutos } from '../planner/data'
import type { ItemNaoAlocadoResponse } from '../planner/api/planoApi'

type ConflitoBannerProps = {
  mensagem: string
  temConflito: boolean
  itensNaoAlocados?: ItemNaoAlocadoResponse[]
  onRemoverMenorPrioridade?: () => void
  onAumentarCapacidade?: () => void
  onManterAssim?: () => void
  onAdicionarOmitido?: (item: ItemNaoAlocadoResponse) => void
  adicionandoId?: string | null
  removendo?: boolean
}

export function ConflitoBanner({
  mensagem,
  temConflito,
  itensNaoAlocados = [],
  onRemoverMenorPrioridade,
  onAumentarCapacidade,
  onManterAssim,
  onAdicionarOmitido,
  adicionandoId = null,
  removendo = false,
}: ConflitoBannerProps) {
  const acionavel =
    temConflito &&
    (onRemoverMenorPrioridade || onAumentarCapacidade || onManterAssim || onAdicionarOmitido)

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
      {temConflito && itensNaoAlocados.length > 0 ? (
        <ul className="plano-omitidos" aria-label="Itens não alocados">
          {itensNaoAlocados.map((item) => (
            <li key={`${item.tipo}-${item.id}`} className="plano-omitido">
              <div className="plano-omitido__meta">
                <p className="plano-omitido__titulo">
                  <span className="plano-bloco-origem">
                    {item.tipo === 'HABITO' ? 'Hábito' : 'Tarefa'}
                  </span>
                  {item.titulo}
                </p>
                <p className="plano-omitido__detalhe">
                  {formatarCapacidadeMinutos(item.duracaoMinutos)} · não coube na capacidade
                </p>
              </div>
              {onAdicionarOmitido ? (
                <button
                  type="button"
                  className="btn btn--secondary btn--sm"
                  disabled={adicionandoId === item.id || removendo}
                  aria-busy={adicionandoId === item.id}
                  onClick={() => onAdicionarOmitido(item)}
                >
                  {adicionandoId === item.id ? 'Adicionando…' : 'Adicionar'}
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
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
