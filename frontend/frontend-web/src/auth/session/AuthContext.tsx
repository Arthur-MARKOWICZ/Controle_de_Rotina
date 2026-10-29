import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import { logout as logoutApi } from '../api/authApi'
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from './tokenStore'

type AuthContextValue = {
  isAuthenticated: boolean
  loginWithToken: (accessToken: string) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getAccessToken())

  useEffect(() => subscribeAccessToken(setToken), [])

  const loginWithToken = useCallback((accessToken: string) => {
    setAccessToken(accessToken)
  }, [])

  const logout = useCallback(async () => {
    const current = getAccessToken()
    try {
      await logoutApi(current)
    } finally {
      clearAccessToken()
    }
  }, [])

  const value = useMemo(
    () => ({
      isAuthenticated: Boolean(token),
      loginWithToken,
      logout,
    }),
    [token, loginWithToken, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth deve ser usado dentro de AuthProvider')
  }
  return ctx
}
