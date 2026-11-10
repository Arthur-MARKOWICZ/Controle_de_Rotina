import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

const PRESETS = [15, 25, 30, 45, 60] as const

type Props = {
  open: boolean
  restanteMinutos: number
  tituloAcao: string
  onConfirm: (duracaoMinutos: number) => void
  onClose: () => void
  ocupado?: boolean
}

export function SessaoDuracaoDialog({
  open,
  restanteMinutos,
  tituloAcao,
  onConfirm,
  onClose,
  ocupado = false,
}: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const [custom, setCustom] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const presetsVisiveis = PRESETS.filter((p) => p <= restanteMinutos)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    if (open) {
      if (!dialog.open) {
        dialog.showModal()
      }
    } else if (dialog.open) {
      dialog.close()
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      return
    }
    setCustom('')
    setErro(null)
  }, [open])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    function handleCancel(event: Event) {
      event.preventDefault()
      onClose()
    }
    dialog.addEventListener('cancel', handleCancel)
    return () => dialog.removeEventListener('cancel', handleCancel)
  }, [onClose])

  function confirmar(duracao: number) {
    if (duracao < 1 || duracao > restanteMinutos) {
      setErro(`Informe entre 1 e ${restanteMinutos} minutos.`)
      return
    }
    setErro(null)
    onConfirm(duracao)
  }

  function handleCustom(event: FormEvent) {
    event.preventDefault()
    const valor = Number.parseInt(custom, 10)
    if (Number.isNaN(valor)) {
      setErro('Informe um número válido.')
      return
    }
    confirmar(valor)
  }

  if (!open) {
    return null
  }

  return (
    <dialog
      ref={dialogRef}
      className="confirm-dialog sessao-duracao-dialog"
      aria-labelledby={titleId}
    >
      <form method="dialog" className="confirm-dialog__form" onSubmit={handleCustom}>
        <h2 id={titleId} className="confirm-dialog__title">
          {tituloAcao}
        </h2>
        <p className="confirm-dialog__message">
          Escolha a duração da sessão (restante: {restanteMinutos} min).
        </p>
        <div className="sessao-duracao__presets" role="group" aria-label="Presets de duração">
          {presetsVisiveis.map((preset) => (
            <button
              key={preset}
              type="button"
              className="btn btn--secondary btn--sm"
              disabled={ocupado}
              onClick={() => confirmar(preset)}
            >
              {preset} min
            </button>
          ))}
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            disabled={ocupado || restanteMinutos < 1}
            onClick={() => confirmar(restanteMinutos)}
          >
            Restante ({restanteMinutos})
          </button>
        </div>
        <label className="field">
          <span>Personalizado (min)</span>
          <input
            type="number"
            min={1}
            max={restanteMinutos}
            value={custom}
            onChange={(e) => setCustom(e.target.value)}
            disabled={ocupado}
          />
        </label>
        {erro ? <p className="field-error">{erro}</p> : null}
        <div className="confirm-dialog__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={ocupado}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primary" disabled={ocupado}>
            Confirmar
          </button>
        </div>
      </form>
    </dialog>
  )
}
