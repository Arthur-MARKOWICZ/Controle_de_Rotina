import { XIcon } from '@phosphor-icons/react'
import { useEffect, useId, useRef, type ReactNode } from 'react'

const CAMPOS_FOCAVEIS =
  'input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled])'

const DURACAO_SAIDA_MS = 120

type DrawerProps = {
  /** `false` toca a animação de saída e então chama `onExited`. */
  open: boolean
  title: string
  /** Pedido de fechamento (Esc, backdrop, botão fechar). */
  onClose: () => void
  onExited?: () => void
  /** Enquanto `true`, o foco inicial espera o conteúdo carregar. */
  busy?: boolean
  footer?: ReactNode
  children: ReactNode
}

function prefereMenosMovimento(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false
}

export function Drawer({
  open,
  title,
  onClose,
  onExited,
  busy = false,
  footer,
  children,
}: DrawerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const corpoRef = useRef<HTMLDivElement>(null)
  const fecharRef = useRef<HTMLButtonElement>(null)
  const gatilhoRef = useRef<Element | null>(null)
  const titleId = useId()

  useEffect(() => {
    gatilhoRef.current = document.activeElement
    const dialog = dialogRef.current
    if (dialog && !dialog.open) {
      dialog.showModal()
    }
    return () => {
      const gatilho = gatilhoRef.current
      if (gatilho instanceof HTMLElement && gatilho.isConnected) {
        gatilho.focus()
      }
    }
  }, [])

  useEffect(() => {
    if (busy) {
      return
    }
    const primeiro = corpoRef.current?.querySelector<HTMLElement>(CAMPOS_FOCAVEIS)
    ;(primeiro ?? fecharRef.current)?.focus()
  }, [busy])

  useEffect(() => {
    if (open) {
      return
    }
    const espera = prefereMenosMovimento() ? 0 : DURACAO_SAIDA_MS
    const timer = window.setTimeout(() => onExited?.(), espera)
    return () => window.clearTimeout(timer)
  }, [open, onExited])

  return (
    <dialog
      ref={dialogRef}
      className={open ? 'drawer' : 'drawer drawer--saindo'}
      aria-labelledby={titleId}
      aria-busy={busy || undefined}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          onClose()
        }
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <header className="drawer__header">
        <h2 id={titleId} className="drawer__title">
          {title}
        </h2>
        <button
          ref={fecharRef}
          type="button"
          className="btn btn--ghost btn--icon"
          aria-label="Fechar"
          onClick={onClose}
        >
          <XIcon aria-hidden />
        </button>
      </header>
      <div ref={corpoRef} className="drawer__corpo">
        {children}
      </div>
      {footer ? <footer className="drawer__rodape">{footer}</footer> : null}
    </dialog>
  )
}
