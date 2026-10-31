import {
  ChartLineUpIcon,
  DotsThreeIcon,
  FolderSimpleIcon,
  SignOutIcon,
  UserCircleIcon,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'

import { useAuth } from '../auth/session/AuthContext'
import { ThemePreferenceField } from '../theme/ThemePreferenceField'

const ITENS = [
  { to: '/projetos', label: 'Projetos', Icon: FolderSimpleIcon },
  { to: '/historico', label: 'Histórico', Icon: ChartLineUpIcon },
  { to: '/perfil', label: 'Perfil', Icon: UserCircleIcon },
]

/** Bottom bar (< 640px): destinos que não cabem nela, em sheet ancorado embaixo. */
export function MenuMais() {
  const { logout } = useAuth()
  const [aberto, setAberto] = useState(false)
  const gatilhoRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (aberto && dialog && !dialog.open) {
      dialog.showModal()
    }
  }, [aberto])

  function fechar() {
    setAberto(false)
    gatilhoRef.current?.focus()
  }

  return (
    <>
      <button
        ref={gatilhoRef}
        type="button"
        className="app-shell__link app-shell__mais"
        aria-haspopup="dialog"
        aria-expanded={aberto}
        onClick={() => setAberto(true)}
      >
        <DotsThreeIcon aria-hidden />
        <span>Mais</span>
      </button>
      {aberto ? (
        <dialog
          ref={dialogRef}
          className="sheet"
          aria-label="Mais opções"
          onCancel={(event) => {
            event.preventDefault()
            fechar()
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault()
              fechar()
            }
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) {
              fechar()
            }
          }}
        >
          <div className="sheet__corpo">
            <nav aria-label="Mais destinos" className="sheet__nav">
              {ITENS.map(({ to, label, Icon }) => (
                <NavLink key={to} to={to} className="sheet__item" onClick={() => setAberto(false)}>
                  <Icon aria-hidden />
                  {label}
                </NavLink>
              ))}
            </nav>
            <ThemePreferenceField />
            <button
              type="button"
              className="sheet__item sheet__item--sair"
              onClick={() => void logout()}
            >
              <SignOutIcon aria-hidden />
              Sair
            </button>
          </div>
        </dialog>
      ) : null}
    </>
  )
}
