import { useMemo, type ReactNode } from 'react'

import { ArrasteHandle } from '../lista/ArrasteHandle'
import { useListaArrastavel } from '../lista/useListaArrastavel'
import type { TarefaResponse } from './api/tarefaApi'

type Props = {
  tarefas: TarefaResponse[]
  onReordenar: (ids: string[]) => void | Promise<void>
  disabled?: boolean
  className?: string
  ariaLabel?: string
  renderItem: (tarefa: TarefaResponse, arrasteHandle: ReactNode) => ReactNode
}

export function TarefaListaArrastavel({
  tarefas,
  onReordenar,
  disabled = false,
  className,
  ariaLabel,
  renderItem,
}: Props) {
  const ids = useMemo(() => tarefas.map((tarefa) => tarefa.id), [tarefas])
  const { idsVisiveis, arrastandoId, registrarItem, handleProps } = useListaArrastavel({
    ids,
    onReordenar,
    disabled,
  })
  const porId = useMemo(() => new Map(tarefas.map((tarefa) => [tarefa.id, tarefa])), [tarefas])

  return (
    <ul className={className} aria-label={ariaLabel}>
      {idsVisiveis.map((id) => {
        const tarefa = porId.get(id)
        if (!tarefa) {
          return null
        }
        const handle = disabled ? null : (
          <ArrasteHandle label={`Reordenar ${tarefa.titulo}`} {...handleProps(id)} />
        )
        return (
          <li
            key={id}
            ref={(el) => registrarItem(id, el)}
            className={
              arrastandoId === id
                ? 'tarefa-arvore-item lista-arraste__item lista-arraste__item--arrastando'
                : 'tarefa-arvore-item lista-arraste__item'
            }
          >
            {renderItem(tarefa, handle)}
          </li>
        )
      })}
    </ul>
  )
}
