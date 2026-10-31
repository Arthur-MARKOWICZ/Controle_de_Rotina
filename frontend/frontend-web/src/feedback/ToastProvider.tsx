import {
  CheckCircleIcon,
  InfoIcon,
  WarningCircleIcon,
  XIcon,
  type Icon,
} from '@phosphor-icons/react'
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

export type ToastAction = {
  label: string
  onClick: () => void
}

type ToastTone = 'success' | 'error' | 'info'

type ToastItem = {
  id: number
  tone: ToastTone
  message: string
  action?: ToastAction
  critical?: boolean
}

type ToastOptions = {
  action?: ToastAction
  critical?: boolean
  durationMs?: number
}

type ToastApi = {
  success: (message: string, options?: ToastOptions) => void
  error: (message: string, options?: ToastOptions) => void
  info: (message: string, options?: ToastOptions) => void
  dismiss: (id?: number) => void
}

const ToastContext = createContext<ToastApi | null>(null)

const DEFAULT_DURATION_MS = 5000

const TOAST_ICON: Record<ToastTone, Icon> = {
  success: CheckCircleIcon,
  error: WarningCircleIcon,
  info: InfoIcon,
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(1)
  const timers = useRef(new Map<number, number>())

  const dismiss = useCallback((id?: number) => {
    setToasts((atual) => {
      if (id === undefined) {
        for (const timer of timers.current.values()) {
          window.clearTimeout(timer)
        }
        timers.current.clear()
        return []
      }
      const timer = timers.current.get(id)
      if (timer !== undefined) {
        window.clearTimeout(timer)
        timers.current.delete(id)
      }
      return atual.filter((toast) => toast.id !== id)
    })
  }, [])

  const push = useCallback(
    (tone: ToastTone, message: string, options?: ToastOptions) => {
      const id = nextId.current
      nextId.current += 1
      const item: ToastItem = {
        id,
        tone,
        message,
        action: options?.action,
        critical: options?.critical,
      }
      setToasts((atual) => [...atual, item])

      const duration = options?.durationMs ?? DEFAULT_DURATION_MS
      if (duration > 0) {
        const timer = window.setTimeout(() => dismiss(id), duration)
        timers.current.set(id, timer)
      }
    },
    [dismiss],
  )

  const api = useMemo<ToastApi>(
    () => ({
      success: (message, options) => push('success', message, options),
      error: (message, options) => push('error', message, options),
      info: (message, options) => push('info', message, options),
      dismiss,
    }),
    [push, dismiss],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-host" aria-live="polite">
        {toasts.map((toast) => {
          const role = toast.tone === 'error' && toast.critical ? 'alert' : 'status'
          const Icon = TOAST_ICON[toast.tone]
          return (
            <div
              key={toast.id}
              className={`toast toast--${toast.tone}`}
              role={role}
            >
              <Icon className="toast__icon" aria-hidden />
              <p className="toast__message">{toast.message}</p>
              <div className="toast__actions">
                {toast.action ? (
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    onClick={() => {
                      toast.action?.onClick()
                      dismiss(toast.id)
                    }}
                  >
                    {toast.action.label}
                  </button>
                ) : null}
                <button
                  type="button"
                  className="btn btn--ghost btn--sm btn--icon"
                  aria-label="Fechar"
                  onClick={() => dismiss(toast.id)}
                >
                  <XIcon aria-hidden />
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast deve ser usado dentro de ToastProvider')
  }
  return ctx
}
