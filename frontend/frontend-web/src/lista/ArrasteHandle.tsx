import { DotsSixVerticalIcon } from '@phosphor-icons/react'
import type { ButtonHTMLAttributes } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  label: string
}

export function ArrasteHandle({ label, disabled, className, ...props }: Props) {
  if (disabled) {
    return null
  }
  const classes = ['arraste-handle', 'btn', 'btn--ghost', 'btn--sm', 'btn--icon', className]
    .filter(Boolean)
    .join(' ')
  return (
    <button
      type="button"
      className={classes}
      aria-label={label}
      title="Arrastar para reordenar. Setas para cima e para baixo no teclado."
      aria-keyshortcuts="ArrowUp ArrowDown"
      {...props}
    >
      <DotsSixVerticalIcon aria-hidden />
    </button>
  )
}
