import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { InlineAlert } from '../feedback/InlineAlert'
import { HORAS_PRESET_DIAS_UTEIS } from './capacidadeInicial'

type CapacidadeInicialDialogProps = {
  aberto: boolean
  salvando: boolean
  erro: string | null
  onUsarDiasUteis: () => void
  onSoHoje: (horas: number) => void
  onFechar: () => void
}

const HORAS_MAX = 16

/**
 * Passo de capacidade antes do primeiro plano: aparece no lugar de gerar quando
 * o usuário não tem nenhuma hora configurada. Fechar não grava nem gera nada.
 */
export function CapacidadeInicialDialog({
  aberto,
  salvando,
  erro,
  onUsarDiasUteis,
  onSoHoje,
  onFechar,
}: CapacidadeInicialDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tituloRef = useRef<HTMLHeadingElement>(null)
  const gatilhoRef = useRef<Element | null>(null)
  const tituloId = useId()
  const descricaoId = useId()
  const [horasHoje, setHorasHoje] = useState(HORAS_PRESET_DIAS_UTEIS)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!aberto || !dialog) {
      return
    }
    gatilhoRef.current = document.activeElement
    setHorasHoje(HORAS_PRESET_DIAS_UTEIS)
    if (!dialog.open) {
      dialog.showModal()
    }
    tituloRef.current?.focus()
    return () => {
      if (dialog.open) {
        dialog.close()
      }
      const gatilho = gatilhoRef.current
      if (gatilho instanceof HTMLElement && gatilho.isConnected) {
        gatilho.focus()
      }
    }
  }, [aberto])

  if (!aberto) {
    return null
  }

  function fechar() {
    if (!salvando) {
      onFechar()
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="confirm-dialog capacidade-inicial-dialog"
      aria-labelledby={tituloId}
      aria-describedby={descricaoId}
      aria-busy={salvando || undefined}
      onCancel={(event) => {
        event.preventDefault()
        fechar()
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          fechar()
        }
      }}
    >
      <div className="confirm-dialog__form">
        <h2 id={tituloId} ref={tituloRef} tabIndex={-1} className="confirm-dialog__title">
          Quanto tempo você tem por dia?
        </h2>
        <p id={descricaoId} className="confirm-dialog__message">
          Sem horas definidas o plano sai vazio. Escolha um ponto de partida; dá para mudar depois.
        </p>

        {erro ? <InlineAlert>{erro}</InlineAlert> : null}

        <div className="capacidade-inicial-dialog__opcao">
          <button
            type="button"
            className="btn btn--primary"
            disabled={salvando}
            aria-busy={salvando}
            onClick={onUsarDiasUteis}
          >
            {salvando ? 'Salvando…' : 'Usar dias úteis 4h'}
          </button>
          <p className="capacidade-inicial-dialog__hint">
            Segunda a sexta, 4h por dia. Depois o plano é gerado.
          </p>
        </div>

        <div
          className="capacidade-inicial-dialog__opcao"
          role="group"
          aria-label="Só hoje"
        >
          <div className="capacidade-inicial-dialog__hoje">
            <div className="capacidade-stepper">
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                aria-label="Diminuir horas de hoje"
                disabled={horasHoje <= 1 || salvando}
                onClick={() => setHorasHoje((atual) => Math.max(1, atual - 1))}
              >
                −
              </button>
              <span aria-label="Horas de hoje">{horasHoje}h</span>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                aria-label="Aumentar horas de hoje"
                disabled={horasHoje >= HORAS_MAX || salvando}
                onClick={() => setHorasHoje((atual) => Math.min(HORAS_MAX, atual + 1))}
              >
                +
              </button>
            </div>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              disabled={salvando}
              onClick={() => onSoHoje(horasHoje)}
            >
              Só hoje
            </button>
          </div>
          <p className="capacidade-inicial-dialog__hint">Vale só para hoje; a semana continua vazia.</p>
        </div>

        <div className="confirm-dialog__actions capacidade-inicial-dialog__rodape">
          <Link to="/capacidade" className="btn btn--ghost btn--sm" onClick={fechar}>
            Configurar a semana
          </Link>
          <button type="button" className="btn btn--ghost btn--sm" disabled={salvando} onClick={fechar}>
            Agora não
          </button>
        </div>
      </div>
    </dialog>
  )
}
