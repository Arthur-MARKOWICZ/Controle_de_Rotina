import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'

import { mesmaOrdem, moverItem } from './listaOrdem'

type Opcoes = {
  ids: string[]
  onReordenar: (ids: string[]) => void | Promise<void>
  disabled?: boolean
}

export function useListaArrastavel({ ids, onReordenar, disabled = false }: Opcoes) {
  const [idsVisiveis, setIdsVisiveis] = useState(ids)
  const idsRef = useRef(ids)
  const origemRef = useRef(ids)
  const arrasteRef = useRef<{ id: string } | null>(null)
  const [arrastandoId, setArrastandoId] = useState<string | null>(null)
  const itemRefs = useRef(new Map<string, HTMLElement>())
  const onReordenarRef = useRef(onReordenar)
  const idsPropRef = useRef(ids)
  const idsPropsKey = ids.join(',')
  const lastPropsKeyRef = useRef(idsPropsKey)

  idsRef.current = idsVisiveis
  onReordenarRef.current = onReordenar
  idsPropRef.current = ids

  useEffect(() => {
    if (arrasteRef.current) {
      return
    }
    if (lastPropsKeyRef.current === idsPropsKey) {
      return
    }
    lastPropsKeyRef.current = idsPropsKey
    setIdsVisiveis(ids)
    origemRef.current = ids
  }, [ids, idsPropsKey])

  function registrarItem(id: string, el: HTMLElement | null) {
    if (el) {
      itemRefs.current.set(id, el)
    } else {
      itemRefs.current.delete(id)
    }
  }

  function indiceSobre(clientY: number, lista: string[]): number {
    for (let i = 0; i < lista.length; i += 1) {
      const el = itemRefs.current.get(lista[i])
      if (!el) {
        continue
      }
      const rect = el.getBoundingClientRect()
      if (clientY < rect.top + rect.height / 2) {
        return i
      }
    }
    return Math.max(0, lista.length - 1)
  }

  function persistir(proximo: string[]) {
    if (mesmaOrdem(proximo, origemRef.current)) {
      return
    }
    origemRef.current = proximo
    void Promise.resolve(onReordenarRef.current(proximo)).catch(() => {
      setIdsVisiveis(idsPropRef.current)
      origemRef.current = idsPropRef.current
    })
  }

  function moverPara(from: number, to: number) {
    const proximo = moverItem(idsRef.current, from, to)
    setIdsVisiveis(proximo)
    persistir(proximo)
  }

  function handlePointerDown(id: string, event: PointerEvent<HTMLButtonElement>) {
    if (disabled || event.button !== 0) {
      return
    }
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    arrasteRef.current = { id }
    setArrastandoId(id)
  }

  function handlePointerMove(event: PointerEvent<HTMLButtonElement>) {
    const arraste = arrasteRef.current
    if (!arraste) {
      return
    }
    const atual = idsRef.current
    const from = atual.indexOf(arraste.id)
    const to = indiceSobre(event.clientY, atual)
    if (from < 0 || from === to) {
      return
    }
    const proximo = moverItem(atual, from, to)
    idsRef.current = proximo
    setIdsVisiveis(proximo)
  }

  function handlePointerUp() {
    if (!arrasteRef.current) {
      return
    }
    arrasteRef.current = null
    setArrastandoId(null)
    persistir(idsRef.current)
  }

  function handleKeyDown(id: string, event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) {
      return
    }
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') {
      return
    }
    event.preventDefault()
    const from = idsRef.current.indexOf(id)
    const to = event.key === 'ArrowUp' ? from - 1 : from + 1
    moverPara(from, to)
  }

  function handleProps(id: string) {
    if (disabled) {
      return { disabled: true as const }
    }
    return {
      onPointerDown: (event: PointerEvent<HTMLButtonElement>) => handlePointerDown(id, event),
      onPointerMove: handlePointerMove,
      onPointerUp: handlePointerUp,
      onPointerCancel: handlePointerUp,
      onKeyDown: (event: KeyboardEvent<HTMLButtonElement>) => handleKeyDown(id, event),
    }
  }

  return {
    idsVisiveis,
    arrastandoId,
    registrarItem,
    handleProps,
  }
}
