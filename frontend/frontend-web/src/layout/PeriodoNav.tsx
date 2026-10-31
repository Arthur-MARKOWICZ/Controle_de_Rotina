import { CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react'

type OpcaoVisao<T extends string> = { valor: T; label: string }

type Props<T extends string> = {
  opcoes: OpcaoVisao<T>[]
  visao: T
  onVisaoChange: (visao: T) => void
  rotulo: string
  onAnterior: () => void
  onProximo: () => void
  onHoje: () => void
  rotuloAnterior?: string
  rotuloProximo?: string
  podeAvancar?: boolean
}

export function PeriodoNav<T extends string>({
  opcoes,
  visao,
  onVisaoChange,
  rotulo,
  onAnterior,
  onProximo,
  onHoje,
  rotuloAnterior = 'Período anterior',
  rotuloProximo = 'Próximo período',
  podeAvancar = true,
}: Props<T>) {
  return (
    <div className="periodo-nav">
      <div className="segmented" role="radiogroup" aria-label="Visão">
        {opcoes.map((opcao) => (
          <button
            key={opcao.valor}
            type="button"
            role="radio"
            aria-checked={visao === opcao.valor}
            className={
              visao === opcao.valor ? 'segmented__item segmented__item--ativo' : 'segmented__item'
            }
            onClick={() => onVisaoChange(opcao.valor)}
          >
            {opcao.label}
          </button>
        ))}
      </div>

      <div className="periodo-nav__passos" role="group" aria-label="Navegação do período">
        <button
          type="button"
          className="btn btn--ghost btn--icon"
          aria-label={rotuloAnterior}
          onClick={onAnterior}
        >
          <CaretLeftIcon aria-hidden />
        </button>
        <button type="button" className="btn btn--secondary btn--sm" onClick={onHoje}>
          Hoje
        </button>
        <button
          type="button"
          className="btn btn--ghost btn--icon"
          aria-label={rotuloProximo}
          disabled={!podeAvancar}
          onClick={onProximo}
        >
          <CaretRightIcon aria-hidden />
        </button>
        <p className="periodo-nav__rotulo" aria-live="polite">
          {rotulo}
        </p>
      </div>
    </div>
  )
}
