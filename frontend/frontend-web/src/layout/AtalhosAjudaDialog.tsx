import { useEffect, useId, useRef } from 'react'

import { LISTA_ATALHOS_AJUDA } from './atalhos'

type Props = {
  open: boolean
  onClose: () => void
}

export function AtalhosAjudaDialog({ open, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

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

  if (!open) {
    return null
  }

  return (
    <dialog ref={dialogRef} className="atalhos-ajuda-dialog" aria-labelledby={titleId}>
      <div className="atalhos-ajuda-dialog__body">
        <h2 id={titleId} className="atalhos-ajuda-dialog__title">
          Atalhos de teclado
        </h2>
        <ul className="atalhos-ajuda-dialog__lista">
          {LISTA_ATALHOS_AJUDA.map((item) => (
            <li key={item.teclas}>
              <kbd>{item.teclas}</kbd>
              <span>{item.descricao}</span>
            </li>
          ))}
        </ul>
        <button type="button" className="btn btn--secondary" onClick={onClose}>
          Fechar
        </button>
      </div>
    </dialog>
  )
}
