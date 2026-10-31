import { LightningIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'

type DiaConcluidoProps = {
  xpGanho: number
}

export function DiaConcluido({ xpGanho }: DiaConcluidoProps) {
  return (
    <div className="dia-concluido" role="status">
      <LightningIcon className="dia-concluido__icone" aria-hidden weight="fill" />
      <div className="dia-concluido__texto">
        <p className="dia-concluido__titulo">Plano de hoje concluído</p>
        {xpGanho > 0 ? <p className="dia-concluido__xp">+{xpGanho} XP hoje</p> : null}
      </div>
      <Link to="/historico" className="btn btn--secondary btn--sm">
        Ver histórico
      </Link>
    </div>
  )
}
