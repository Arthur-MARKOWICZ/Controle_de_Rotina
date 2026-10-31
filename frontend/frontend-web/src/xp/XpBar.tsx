import { LightningIcon } from '@phosphor-icons/react'

import type { XpProgresso } from './types'

type Props = {
  progresso: XpProgresso
}

export function XpBar({ progresso }: Props) {
  const pct =
    progresso.xpParaProximoNivel > 0
      ? Math.min(100, Math.round((progresso.xpNoNivel / progresso.xpParaProximoNivel) * 100))
      : 0
  const resumo = `${progresso.xpNoNivel} / ${progresso.xpParaProximoNivel} XP`

  return (
    <div className="xp-bar" aria-label={`Nível ${progresso.nivel}`} title={resumo}>
      <LightningIcon className="xp-bar__icone" aria-hidden />
      <p className="xp-bar__nivel">Nível {progresso.nivel}</p>
      <div
        className="xp-bar__track"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={progresso.xpParaProximoNivel}
        aria-valuenow={progresso.xpNoNivel}
        aria-label={`${progresso.xpNoNivel} de ${progresso.xpParaProximoNivel} XP`}
      >
        <div className="xp-bar__fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
