import { SunHorizonIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'

type EmptyStatePlanoProps = {
  gerando: boolean
  onGerar: () => void
  onAjustarCapacidade: () => void
}

export function EmptyStatePlano({ gerando, onGerar, onAjustarCapacidade }: EmptyStatePlanoProps) {
  return (
    <div className="plano-dia-vazio-bloco">
      <SunHorizonIcon className="plano-dia-vazio-bloco__icone" aria-hidden weight="duotone" />
      <p className="plano-dia-vazio" role="status">
        Ainda não há plano para hoje.
      </p>
      <p className="plano-dia-empty-fluxo">
        Gere um plano a partir das suas tarefas → revise → aprove → execute
      </p>
      <div className="plano-dia-acoes">
        <button
          type="button"
          className="btn btn--primary"
          onClick={onGerar}
          disabled={gerando}
          aria-busy={gerando}
        >
          {gerando ? 'Gerando…' : 'Gerar plano de hoje'}
        </button>
        <Link to="/tarefas" className="btn btn--secondary">
          Voltar às tarefas
        </Link>
        <button type="button" className="btn btn--ghost" onClick={onAjustarCapacidade}>
          Ajustar capacidade de hoje
        </button>
      </div>
    </div>
  )
}
