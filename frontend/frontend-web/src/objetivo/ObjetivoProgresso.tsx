import type { ReactNode } from 'react'

import type { ObjetivoResponse } from './api/objetivoApi'
import { normalizarProgresso } from './progressoObjetivo'

type Props = {
  titulo: string
  progresso: ObjetivoResponse['progresso'] | undefined
  /** `compacto` no card da lista, `destaque` no detalhe. */
  tamanho: 'compacto' | 'destaque'
  /** O que mostrar quando não há progresso a medir. Sem isso, não renderiza nada. */
  semProgresso?: ReactNode
}

/** Barra de progresso do objetivo, ponderada por tempo (percentual vindo da API). */
export function ObjetivoProgresso({ titulo, progresso, tamanho, semProgresso = null }: Props) {
  const dados = normalizarProgresso(progresso)

  if (!dados) {
    return semProgresso ? (
      <p className={`objetivo-progresso objetivo-progresso--vazio objetivo-progresso--${tamanho}`}>
        {semProgresso}
      </p>
    ) : null
  }

  return (
    <div className={`objetivo-progresso objetivo-progresso--${tamanho}`}>
      <div
        className="objetivo-progresso__track"
        role="progressbar"
        aria-label={`Progresso de ${titulo}`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={dados.pct}
        aria-valuetext={`${dados.pct}%, ${dados.texto}`}
      >
        <div className="objetivo-progresso__fill" style={{ width: `${dados.pct}%` }} />
      </div>
      <p className="objetivo-progresso__legenda" aria-hidden="true">
        <strong>{dados.pct}%</strong> · {dados.texto}
      </p>
    </div>
  )
}
