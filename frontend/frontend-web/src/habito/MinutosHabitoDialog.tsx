import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

/** Minutos aceitos no concluir direto de hábito (mesmo limite do backend). */
const MINUTOS_HABITO_MAXIMO = 1440

type Props = {
  open: boolean
  tituloHabito: string
  /** Duração do hábito; usada como valor inicial do campo. */
  duracaoMinutos: number
  onConfirm: (minutos: number) => void
  onClose: () => void
  ocupado?: boolean
}

/** Pergunta quantos minutos o usuário dedicou antes de concluir um hábito direto (sem timer). */
export function MinutosHabitoDialog({
  open,
  tituloHabito,
  duracaoMinutos,
  onConfirm,
  onClose,
  ocupado = false,
}: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const inputId = useId()
  const [valor, setValor] = useState(String(duracaoMinutos))
  const [erro, setErro] = useState<string | null>(null)

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
    setValor(String(duracaoMinutos))
    setErro(null)
  }, [open, duracaoMinutos])

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

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const minutos = Number(valor)
    if (!valor.trim() || !Number.isInteger(minutos) || minutos < 1 || minutos > MINUTOS_HABITO_MAXIMO) {
      setErro(`Informe entre 1 e ${MINUTOS_HABITO_MAXIMO} minutos.`)
      return
    }
    setErro(null)
    onConfirm(minutos)
  }

  if (!open) {
    return null
  }

  return (
    <dialog ref={dialogRef} className="confirm-dialog" aria-labelledby={titleId}>
      <form method="dialog" className="confirm-dialog__form" onSubmit={handleSubmit} noValidate>
        <h2 id={titleId} className="confirm-dialog__title">
          Concluir {tituloHabito}
        </h2>
        <p className="confirm-dialog__message">
          Quanto tempo você dedicou? A meta do hábito é {duracaoMinutos} min.
        </p>
        <label className="field" htmlFor={inputId}>
          <span>Minutos dedicados</span>
          <input
            id={inputId}
            type="number"
            inputMode="numeric"
            min={1}
            max={MINUTOS_HABITO_MAXIMO}
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            disabled={ocupado}
            aria-invalid={erro ? true : undefined}
            aria-describedby={erro ? `${inputId}-erro` : undefined}
          />
        </label>
        {erro ? (
          <p id={`${inputId}-erro`} className="field-error" role="alert">
            {erro}
          </p>
        ) : null}
        <div className="confirm-dialog__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={ocupado}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primary" disabled={ocupado}>
            Concluir
          </button>
        </div>
      </form>
    </dialog>
  )
}
