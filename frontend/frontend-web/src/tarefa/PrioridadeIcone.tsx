import { CaretDoubleUpIcon, CaretUpIcon, MinusIcon, type Icon } from '@phosphor-icons/react'

import type { Prioridade } from './validation/tarefaForm'

const ICONE: Record<Prioridade, Icon> = {
  ALTA: CaretDoubleUpIcon,
  MEDIA: CaretUpIcon,
  BAIXA: MinusIcon,
}

type Props = {
  prioridade: Prioridade | null | undefined
  className?: string
}

/**
 * Prioridade por forma (⇈ ↑ −) e cor: a cor sozinha não distingue alta de média
 * em deuteranopia. Decorativo: o rótulo acessível vem de quem usa.
 */
export function PrioridadeIcone({ prioridade, className }: Props) {
  const Desenho = prioridade ? ICONE[prioridade] : undefined
  if (!prioridade || !Desenho) {
    return null
  }
  const classes = ['prioridade-icone', `prioridade-icone--${prioridade.toLowerCase()}`, className]
    .filter(Boolean)
    .join(' ')
  return <Desenho className={classes} aria-hidden weight="bold" />
}
