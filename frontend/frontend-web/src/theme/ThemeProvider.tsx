import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import {
  aplicarTemaEfetivo,
  lerPreferenciaTema,
  preferenciaParaTemaEfetivo,
  salvarPreferenciaTema,
  type PreferenciaTema,
  type TemaEfetivo,
} from './themePreference'

type ThemeContextValue = {
  preferencia: PreferenciaTema
  temaEfetivo: TemaEfetivo
  setPreferencia: (preferencia: PreferenciaTema) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function lerPrefersDark(): boolean {
  if (typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preferencia, setPreferenciaState] = useState<PreferenciaTema>(() => lerPreferenciaTema())
  const [prefersDark, setPrefersDark] = useState(lerPrefersDark)

  const temaEfetivo = preferenciaParaTemaEfetivo(preferencia, prefersDark)

  useEffect(() => {
    aplicarTemaEfetivo(temaEfetivo)
  }, [temaEfetivo])

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setPrefersDark(media.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  const setPreferencia = useCallback((proxima: PreferenciaTema) => {
    salvarPreferenciaTema(proxima)
    setPreferenciaState(proxima)
  }, [])

  const value = useMemo(
    () => ({
      preferencia,
      temaEfetivo,
      setPreferencia,
    }),
    [preferencia, temaEfetivo, setPreferencia],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) {
    throw new Error('useTheme deve ser usado dentro de ThemeProvider')
  }
  return ctx
}
