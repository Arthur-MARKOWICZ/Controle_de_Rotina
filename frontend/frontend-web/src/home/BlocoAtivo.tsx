import { CheckIcon, StopIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'

import { formatarTempoRestante, minutosExcedidos, minutosRestantes } from '../execucao/data'

type BlocoAtivoProps = {
  titulo: string
  duracaoMinutos: number
  /** `null` enquanto o início ainda não foi confirmado pela API. */
  iniciadoEm: string | null
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

/** Relógio local: só este componente re-renderiza a cada segundo. */
function useAgora(ativo: boolean): Date {
  const [agora, setAgora] = useState(() => new Date())
  useEffect(() => {
    if (!ativo) {
      return
    }
    const id = window.setInterval(() => setAgora(new Date()), 1000)
    return () => window.clearInterval(id)
  }, [ativo])
  return agora
}

export function BlocoAtivo({
  titulo,
  duracaoMinutos,
  iniciadoEm,
  ocupado,
  onConcluir,
  onAbortar,
}: BlocoAtivoProps) {
  const agora = useAgora(iniciadoEm != null)
  const restantes = iniciadoEm ? minutosRestantes(iniciadoEm, duracaoMinutos, agora) : duracaoMinutos
  const excedidos = iniciadoEm ? minutosExcedidos(iniciadoEm, duracaoMinutos, agora) : 0
  const pct = percentualDecorrido(duracaoMinutos, restantes)

  return (
    <section className="bloco-ativo" role="region" aria-label="Agora">
      <div className="bloco-ativo__cabecalho">
        <div className="bloco-ativo__texto">
          <p className="bloco-ativo__rotulo">Agora</p>
          <p className="bloco-ativo__titulo">{titulo}</p>
        </div>
        {excedidos > 0 ? (
          <p className="bloco-ativo__tempo bloco-ativo__tempo--excedido" aria-label="Tempo excedido">
            +{excedidos} min
          </p>
        ) : (
          <p className="bloco-ativo__tempo" aria-label="Tempo restante">
            {formatarTempoRestante(restantes)}
            <span className="bloco-ativo__tempo-rotulo"> restantes</span>
          </p>
        )}
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
          className="btn btn--primary btn--touch"
          onClick={onConcluir}
          disabled={ocupado}
          aria-busy={ocupado}
          aria-label={`Concluir ${titulo}`}
        >
          <CheckIcon aria-hidden />
          Concluir
        </button>
        <button
          type="button"
          className="btn btn--ghost btn--danger btn--touch"
          onClick={onAbortar}
          disabled={ocupado}
          aria-label={`Abortar ${titulo}`}
        >
          <StopIcon aria-hidden />
          Abortar
        </button>
      </div>
    </section>
  )
}
