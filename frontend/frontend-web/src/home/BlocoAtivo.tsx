import { formatarTempoRestante } from '../execucao/data'

type BlocoAtivoProps = {
  titulo: string
  duracaoMinutos: number
  minutosRestantes: number
  ocupado: boolean
  onConcluir: () => void
  onAbortar: () => void
}

function percentualDecorrido(duracaoMinutos: number, restantes: number): number {
  if (duracaoMinutos <= 0) {
    return 100
  }
  const decorrido = Math.max(0, duracaoMinutos - restantes)
  return Math.min(100, Math.round((decorrido / duracaoMinutos) * 100))
}

export function BlocoAtivo({
  titulo,
  duracaoMinutos,
  minutosRestantes,
  ocupado,
  onConcluir,
  onAbortar,
}: BlocoAtivoProps) {
  const restanteLabel = formatarTempoRestante(minutosRestantes)
  const pct = percentualDecorrido(duracaoMinutos, minutosRestantes)

  return (
    <section className="bloco-ativo" role="region" aria-label="Agora">
      <div className="bloco-ativo__cabecalho">
        <p className="bloco-ativo__titulo">
          Agora: <strong>{titulo}</strong>
          <span className="bloco-ativo__restante" aria-label="Tempo restante">
            {' '}
            · Restante: {restanteLabel}
          </span>
        </p>
      </div>
      <div
        className="bloco-ativo__progresso"
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`Progresso: ${pct}%`}
      >
        <div className="bloco-ativo__progresso-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="bloco-ativo__acoes">
        <button
          type="button"
          className="btn btn--primary btn--sm btn--touch"
          onClick={onConcluir}
          disabled={ocupado}
          aria-busy={ocupado}
          aria-label={`Concluir ${titulo}`}
        >
          {ocupado ? 'Salvando…' : 'Concluir'}
        </button>
        <button
          type="button"
          className="btn btn--secondary btn--sm btn--touch"
          onClick={onAbortar}
          disabled={ocupado}
          aria-busy={ocupado}
          aria-label={`Abortar ${titulo}`}
        >
          {ocupado ? 'Abortando…' : 'Abortar'}
        </button>
      </div>
    </section>
  )
}
