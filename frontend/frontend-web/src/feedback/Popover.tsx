import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react'

type PopoverProps = {
  /** Conteúdo do botão gatilho. */
  label: ReactNode
  /** Nome acessível do gatilho, quando `label` não basta (ex.: só ícone). */
  ariaLabel?: string
  className?: string
  triggerClassName?: string
  align?: 'start' | 'end'
  /** Fecha ao clicar num botão ou link do painel (menus de ação). */
  fecharAoEscolher?: boolean
  children: ReactNode
}

export function Popover({
  label,
  ariaLabel,
  className,
  triggerClassName = 'btn btn--secondary btn--sm',
  align = 'start',
  fecharAoEscolher = false,
  children,
}: PopoverProps) {
  const [aberto, setAberto] = useState(false)
  const raizRef = useRef<HTMLDivElement>(null)
  const gatilhoRef = useRef<HTMLButtonElement>(null)
  const painelId = useId()

  const fechar = useCallback(() => {
    setAberto(false)
    gatilhoRef.current?.focus()
  }, [])

  useEffect(() => {
    if (!aberto) {
      return
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        fechar()
      }
    }
    function handlePointerDown(event: PointerEvent) {
      if (!raizRef.current?.contains(event.target as Node)) {
        setAberto(false)
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('pointerdown', handlePointerDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('pointerdown', handlePointerDown)
    }
  }, [aberto, fechar])

  return (
    <div className={className ? `popover ${className}` : 'popover'} ref={raizRef}>
      <button
        ref={gatilhoRef}
        type="button"
        className={triggerClassName}
        aria-label={ariaLabel}
        aria-expanded={aberto}
        aria-controls={aberto ? painelId : undefined}
        onClick={() => setAberto((atual) => !atual)}
      >
        {label}
      </button>
      {aberto ? (
        <div
          id={painelId}
          className={`popover__painel popover__painel--${align}`}
          onClick={(event) => {
            if (fecharAoEscolher && (event.target as Element).closest('a, button')) {
              setAberto(false)
            }
          }}
        >
          {children}
        </div>
      ) : null}
    </div>
  )
}
