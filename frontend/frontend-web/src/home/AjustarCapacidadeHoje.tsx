import { useEffect, useState } from 'react'

type AjustarCapacidadeHojeProps = {
  aberto: boolean
  horasIniciais: number
  salvando: boolean
  mostrarCtaGerar: boolean
  gerando: boolean
  onAbrir: () => void
  onFechar: () => void
  onSalvar: (horas: number) => void
  onGerarVersao: () => void
}

const HORAS_MAX = 16

export function AjustarCapacidadeHoje({
  aberto,
  horasIniciais,
  salvando,
  mostrarCtaGerar,
  gerando,
  onAbrir,
  onFechar,
  onSalvar,
  onGerarVersao,
}: AjustarCapacidadeHojeProps) {
  const [horas, setHoras] = useState(horasIniciais)

  useEffect(() => {
    if (aberto) {
      setHoras(horasIniciais)
    }
  }, [aberto, horasIniciais])

  return (
    <div className="ajustar-capacidade-hoje">
      {!aberto ? (
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={onAbrir}
        >
          Ajustar capacidade de hoje
        </button>
      ) : (
        <div
          className="ajustar-capacidade-hoje__painel"
          role="group"
          aria-label="Ajustar capacidade de hoje"
        >
          <p className="ajustar-capacidade-hoje__titulo">Quanto tempo você tem hoje?</p>
          <div className="capacidade-stepper">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              aria-label="Diminuir horas de hoje"
              disabled={horas <= 0 || salvando}
              onClick={() => setHoras((atual) => Math.max(0, atual - 1))}
            >
              −
            </button>
            <span aria-label="Horas de hoje">{horas}h</span>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              aria-label="Aumentar horas de hoje"
              disabled={horas >= HORAS_MAX || salvando}
              onClick={() => setHoras((atual) => Math.min(HORAS_MAX, atual + 1))}
            >
              +
            </button>
          </div>
          <div className="ajustar-capacidade-hoje__acoes">
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={salvando}
              aria-busy={salvando}
              onClick={() => onSalvar(horas)}
            >
              {salvando ? 'Salvando…' : 'Salvar capacidade de hoje'}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={salvando}
              onClick={onFechar}
            >
              Cancelar
            </button>
          </div>
        </div>
      )}

      {mostrarCtaGerar ? (
        <div className="ajustar-capacidade-hoje__cta">
          <p>Capacidade de hoje atualizada. Quer gerar uma nova versão do plano?</p>
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            disabled={gerando}
            aria-busy={gerando}
            onClick={onGerarVersao}
          >
            {gerando ? 'Gerando…' : 'Gerar nova versão do plano'}
          </button>
        </div>
      ) : null}
    </div>
  )
}
