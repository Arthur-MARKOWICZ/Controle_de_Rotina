type EmptyStatePlanoProps = {
  gerando: boolean
  onGerar: () => void
}

export function EmptyStatePlano({ gerando, onGerar }: EmptyStatePlanoProps) {
  return (
    <div className="plano-dia-vazio-bloco">
      <p className="plano-dia-vazio" role="status">
        Ainda não há plano para hoje.
      </p>
      <p className="plano-dia-empty-fluxo">
        Gere o plano → revise → aprove → execute
      </p>
      <div className="plano-dia-acoes">
        <button
          type="button"
          className="btn btn--primary"
          onClick={onGerar}
          disabled={gerando}
          aria-busy={gerando}
        >
          {gerando ? 'Gerando…' : 'Gerar plano'}
        </button>
      </div>
    </div>
  )
}
