import { CaretUpDownIcon, SignOutIcon, UserCircleIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '../auth/session/AuthContext'
import { ThemePreferenceField } from '../theme/ThemePreferenceField'
import { useXpProgress } from '../xp/XpProgressContext'

function iniciais(nome: string | null): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean)
  if (partes.length === 0) {
    return '?'
  }
  const primeira = partes[0][0]
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : ''
  return (primeira + ultima).toUpperCase()
}

export function MenuConta() {
  const { logout } = useAuth()
  const { nomeUsuario } = useXpProgress()
  const [aberto, setAberto] = useState(false)
  const raizRef = useRef<HTMLDivElement>(null)
  const gatilhoRef = useRef<HTMLButtonElement>(null)
  const popoverId = useId()

  const fechar = useCallback((devolverFoco: boolean) => {
    setAberto(false)
    if (devolverFoco) {
      gatilhoRef.current?.focus()
    }
  }, [])

  useEffect(() => {
    if (!aberto) {
      return
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        fechar(true)
      }
    }
    function handlePointerDown(event: PointerEvent) {
      if (!raizRef.current?.contains(event.target as Node)) {
        fechar(false)
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('pointerdown', handlePointerDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('pointerdown', handlePointerDown)
    }
  }, [aberto, fechar])

  return (
    <div className="menu-conta" ref={raizRef}>
      {aberto ? (
        <div id={popoverId} className="menu-conta__popover" role="group" aria-label="Conta">
          <Link to="/perfil" className="menu-conta__item" onClick={() => fechar(false)}>
            <UserCircleIcon aria-hidden />
            Perfil
          </Link>
          <ThemePreferenceField />
          <button
            type="button"
            className="menu-conta__item menu-conta__item--sair"
            onClick={() => void logout()}
          >
            <SignOutIcon aria-hidden />
            Sair
          </button>
        </div>
      ) : null}
      <button
        ref={gatilhoRef}
        type="button"
        className="menu-conta__gatilho"
        aria-label={`Menu da conta: ${nomeUsuario ?? 'Conta'}`}
        aria-expanded={aberto}
        aria-controls={aberto ? popoverId : undefined}
        onClick={() => setAberto((atual) => !atual)}
      >
        <span className="menu-conta__avatar" aria-hidden>
          {iniciais(nomeUsuario)}
        </span>
        <span className="menu-conta__nome">{nomeUsuario ?? 'Conta'}</span>
        <CaretUpDownIcon className="menu-conta__caret" aria-hidden />
      </button>
    </div>
  )
}
