import { formatarCapacidadeMinutos } from '../planner/data'

type CapacidadeBarProps = {
  usadosMinutos: number
  disponiveisMinutos: number
  sobrecarga?: boolean
  versao?: number
}

function percentualPreenchimento(usados: number, disponiveis: number): number {
  if (disponiveis <= 0) {
    return usados > 0 ? 100 : 0
  }
  return Math.min(100, Math.round((usados / disponiveis) * 100))
}

export function CapacidadeBar({
  usadosMinutos,
  disponiveisMinutos,
  sobrecarga = false,
  versao,
}: CapacidadeBarProps) {
  const resumo = `${formatarCapacidadeMinutos(usadosMinutos)} / ${formatarCapacidadeMinutos(disponiveisMinutos)}`
  const pct = percentualPreenchimento(usadosMinutos, disponiveisMinutos)
  const emSobrecarga = sobrecarga || usadosMinutos > disponiveisMinutos
  const ariaLabel = emSobrecarga
    ? `Capacidade com sobrecarga: ${resumo}`
    : `Capacidade: ${resumo}`

  return (
    <div className="capacidade-bar-wrap">
      <p className="plano-dia-capacidade">
        Capacidade: <strong>{resumo}</strong>
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
