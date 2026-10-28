import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'

import { focoEmCampoEditavel, rotaPorAtalhoGo } from './atalhos'

type Options = {
  onNovaTarefa: () => void
  onAjuda: () => void
  enabled?: boolean
}

const GO_TIMEOUT_MS = 1200

export function useAtalhosTeclado({ onNovaTarefa, onAjuda, enabled = true }: Options) {
  const navigate = useNavigate()
  const aguardandoGo = useRef(false)
  const goTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onNovaTarefaRef = useRef(onNovaTarefa)
  const onAjudaRef = useRef(onAjuda)

  useEffect(() => {
    onNovaTarefaRef.current = onNovaTarefa
    onAjudaRef.current = onAjuda
  }, [onNovaTarefa, onAjuda])

  useEffect(() => {
    if (!enabled) {
      return
    }

    function limparGo() {
      aguardandoGo.current = false
      if (goTimer.current) {
        clearTimeout(goTimer.current)
        goTimer.current = null
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) {
        return
      }
      if (focoEmCampoEditavel(event.target)) {
        return
      }

      const key = event.key

      if (aguardandoGo.current) {
        limparGo()
        const rota = rotaPorAtalhoGo(key)
        if (rota) {
          event.preventDefault()
          navigate(rota)
        }
        return
      }

      if (key === '?' || (key === '/' && event.shiftKey)) {
        event.preventDefault()
        onAjudaRef.current()
        return
      }

      if (key === 'n' || key === 'N') {
        event.preventDefault()
        onNovaTarefaRef.current()
        return
      }

      if (key === 'g' || key === 'G') {
        event.preventDefault()
        aguardandoGo.current = true
        goTimer.current = setTimeout(limparGo, GO_TIMEOUT_MS)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      limparGo()
    }
  }, [enabled, navigate])
}
