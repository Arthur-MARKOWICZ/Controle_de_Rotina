import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { QuickAddTarefaDialog } from '../tarefa/QuickAddTarefaDialog'

type QuickAddApi = {
  abrir: () => void
  /** Registra callback para "tarefa criada"; retorna a função de cancelamento. */
  assinarCriada: (callback: () => void) => () => void
}

const QuickAddContext = createContext<QuickAddApi | null>(null)

export function QuickAddProvider({ children }: { children: ReactNode }) {
  const [aberto, setAberto] = useState(false)
  const assinantes = useRef(new Set<() => void>())

  const abrir = useCallback(() => setAberto(true), [])

  const assinarCriada = useCallback((callback: () => void) => {
    assinantes.current.add(callback)
    return () => {
      assinantes.current.delete(callback)
    }
  }, [])

  const notificarCriada = useCallback(() => {
    for (const callback of assinantes.current) {
      callback()
    }
  }, [])

  const value = useMemo(() => ({ abrir, assinarCriada }), [abrir, assinarCriada])

  return (
    <QuickAddContext.Provider value={value}>
      {children}
      <QuickAddTarefaDialog
        open={aberto}
        onClose={() => setAberto(false)}
        onCriada={notificarCriada}
      />
    </QuickAddContext.Provider>
  )
}

export function useQuickAdd(): QuickAddApi {
  const ctx = useContext(QuickAddContext)
  if (!ctx) {
    throw new Error('useQuickAdd deve ser usado dentro de QuickAddProvider')
  }
  return ctx
}
