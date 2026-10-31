import { formatarCapacidadeMinutos } from '../planner/data'

export type ResumoBlocos = {
  total: number
  /** Só no plano em execução: quantos já foram concluídos. */
  concluidos?: number
}

type CapacidadeBarProps = {
  usadosMinutos: number
  disponiveisMinutos: number
  sobrecarga?: boolean
  versao?: number
  /** Quando presente, o texto vira o resumo do dia (blocos · tempo). */
  blocos?: ResumoBlocos
}

function percentualPreenchimento(usados: number, disponiveis: number): number {
  if (disponiveis <= 0) {
    return usados > 0 ? 100 : 0
  }
  return Math.min(100, Math.round((usados / disponiveis) * 100))
}

function textoBlocos({ total, concluidos }: ResumoBlocos): string {
  const plural = total === 1 ? 'bloco' : 'blocos'
  return concluidos == null ? `${total} ${plural}` : `${concluidos} de ${total} ${plural}`
}

export function CapacidadeBar({
  usadosMinutos,
  disponiveisMinutos,
  sobrecarga = false,
  versao,
  blocos,
}: CapacidadeBarProps) {
  const usados = formatarCapacidadeMinutos(usadosMinutos)
  const disponiveis = formatarCapacidadeMinutos(disponiveisMinutos)
  const resumo = `${usados} / ${disponiveis}`
  const pct = percentualPreenchimento(usadosMinutos, disponiveisMinutos)
  const emSobrecarga = sobrecarga || usadosMinutos > disponiveisMinutos
  const ariaLabel = emSobrecarga
    ? `Capacidade com sobrecarga: ${resumo}`
    : `Capacidade: ${resumo}`

  return (
    <div className="capacidade-bar-wrap">
      <p className="plano-dia-capacidade">
        {blocos ? (
          <>
            <strong>{textoBlocos(blocos)}</strong>
            {' · '}
            <span className="plano-dia-capacidade__tempo">
              {usados} de {disponiveis}
            </span>
          </>
        ) : (
          <>
            Capacidade: <strong>{resumo}</strong>
          </>
        )}
        {versao != null && versao > 0 ? (
          <span className="plano-dia-versao"> · versão {versao}</span>
        ) : null}
      </p>
      <div
        className={
          emSobrecarga ? 'capacidade-bar capacidade-bar--sobrecarga' : 'capacidade-bar'
        }
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={ariaLabel}
      >
        <div className="capacidade-bar__fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
