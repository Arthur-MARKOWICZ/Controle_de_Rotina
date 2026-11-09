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
  /** Abre o dialog mínimo (ex.: botão do topo). */
  abrir: () => void
  /**
   * Atalho N / fluxo “nova tarefa” contextual: se a lista registrou foco do inline,
   * foca o campo; senão abre o dialog.
   */
  novaTarefa: () => void
  /** Registra callback para "tarefa criada"; retorna a função de cancelamento. */
  assinarCriada: (callback: () => void) => () => void
  /** Registra o foco do inline add da lista; retorna cleanup. */
  registrarFocoInline: (callback: () => void) => () => void
}

const QuickAddContext = createContext<QuickAddApi | null>(null)

export function QuickAddProvider({ children }: { children: ReactNode }) {
  const [aberto, setAberto] = useState(false)
  const assinantes = useRef(new Set<() => void>())
  const focoInline = useRef<(() => void) | null>(null)

  const abrir = useCallback(() => setAberto(true), [])

  const novaTarefa = useCallback(() => {
    if (focoInline.current) {
      focoInline.current()
      return
    }
    setAberto(true)
  }, [])

  const assinarCriada = useCallback((callback: () => void) => {
    assinantes.current.add(callback)
    return () => {
      assinantes.current.delete(callback)
    }
  }, [])

  const registrarFocoInline = useCallback((callback: () => void) => {
    focoInline.current = callback
    return () => {
      if (focoInline.current === callback) {
        focoInline.current = null
      }
    }
  }, [])

  const notificarCriada = useCallback(() => {
    for (const callback of assinantes.current) {
      callback()
    }
  }, [])

  const value = useMemo(
    () => ({ abrir, novaTarefa, assinarCriada, registrarFocoInline }),
    [abrir, novaTarefa, assinarCriada, registrarFocoInline],
  )

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
