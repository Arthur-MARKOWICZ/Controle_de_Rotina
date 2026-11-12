import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import { atalhosPrazo, type PrazoAtalhoId } from './prazoAtalhos'

type Props = {
  open: boolean
  value: string
  disabled?: boolean
  onApply: (prazoLocal: string) => void
  onClose: () => void
}

export function PrazoDialog({ open, value, disabled = false, onApply, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const atalhosLabelId = useId()
  const [rascunho, setRascunho] = useState(value)
  const [atalhoAtivo, setAtalhoAtivo] = useState<PrazoAtalhoId | null>(null)
  const atalhos = atalhosPrazo()

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
    setRascunho(value)
    setAtalhoAtivo(null)
  }, [open, value])

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

  function aplicarAtalho(id: PrazoAtalhoId) {
    const atalho = atalhos.find((item) => item.id === id)
    if (!atalho) {
      return
    }
    setAtalhoAtivo(id)
    setRascunho(atalho.prazoLocal)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onApply(rascunho)
  }

  if (!open) {
    return null
  }

  return (
    <dialog ref={dialogRef} className="confirm-dialog prazo-dialog" aria-labelledby={titleId}>
      <form method="dialog" className="confirm-dialog__form" onSubmit={handleSubmit}>
        <h2 id={titleId} className="confirm-dialog__title">
          Prazo
        </h2>
        <p className="confirm-dialog__message">Atalho ou data e hora. Aplicar grava no formulário.</p>
        <div className="field">
          <span className="field-label" id={atalhosLabelId}>
            Prazo rápido
          </span>
          <div className="prazo-chips" role="group" aria-labelledby={atalhosLabelId}>
            {atalhos.map((atalho) => (
              <button
                key={atalho.id}
                type="button"
                className={atalhoAtivo === atalho.id ? 'prazo-chip prazo-chip--ativo' : 'prazo-chip'}
                aria-pressed={atalhoAtivo === atalho.id}
                disabled={disabled}
                onClick={() => aplicarAtalho(atalho.id)}
              >
                {atalho.label}
              </button>
            ))}
          </div>
        </div>
        <div className="field">
          <label htmlFor="prazo">Prazo (opcional)</label>
          <input
            id="prazo"
            name="prazo"
            type="datetime-local"
            value={rascunho}
            onChange={(e) => {
              setAtalhoAtivo(null)
              setRascunho(e.target.value)
            }}
            disabled={disabled}
          />
        </div>
        <div className="confirm-dialog__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primary" disabled={disabled}>
            Aplicar
          </button>
        </div>
      </form>
    </dialog>
  )
}
