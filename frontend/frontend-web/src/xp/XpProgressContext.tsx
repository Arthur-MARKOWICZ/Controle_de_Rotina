import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import { buscarMe } from '../auth/api/meApi'
import type { XpProgresso } from './types'

type XpProgressApi = {
  progresso: XpProgresso | null
  aplicarProgresso: (proximo: XpProgresso) => void
}

const XpProgressContext = createContext<XpProgressApi | null>(null)

export function XpProgressProvider({ children }: { children: ReactNode }) {
  const [progresso, setProgresso] = useState<XpProgresso | null>(null)

  useEffect(() => {
    let ativo = true
    void buscarMe()
      .then((me) => {
        if (!ativo) {
          return
        }
        setProgresso({
          xpTotal: me.xpTotal,
          nivel: me.nivel,
          xpNoNivel: me.xpNoNivel,
          xpParaProximoNivel: me.xpParaProximoNivel,
        })
      })
      .catch(() => {
        /* shell segue sem barra se /me falhar */
      })
    return () => {
      ativo = false
    }
  }, [])

  const aplicarProgresso = useCallback((proximo: XpProgresso) => {
    setProgresso(proximo)
  }, [])

  const value = useMemo(
    () => ({ progresso, aplicarProgresso }),
    [progresso, aplicarProgresso],
  )

  return <XpProgressContext.Provider value={value}>{children}</XpProgressContext.Provider>
}

export function useXpProgress(): XpProgressApi {
  const ctx = useContext(XpProgressContext)
  if (!ctx) {
    throw new Error('useXpProgress deve ser usado dentro de XpProgressProvider')
  }
  return ctx
}
