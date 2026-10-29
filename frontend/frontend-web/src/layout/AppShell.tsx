import { useCallback, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'

import { useAuth } from '../auth/session/AuthContext'
import { ToastProvider } from '../feedback/ToastProvider'
import { QuickAddTarefaDialog } from '../tarefa/QuickAddTarefaDialog'
import { XpBar } from '../xp/XpBar'
import { XpProgressProvider, useXpProgress } from '../xp/XpProgressContext'
import { AtalhosAjudaDialog } from './AtalhosAjudaDialog'
import { useAtalhosTeclado } from './useAtalhosTeclado'

const NAV_PRINCIPAL: { to: string; label: string; end?: boolean }[] = [
  { to: '/', label: 'Hoje', end: true },
  { to: '/tarefas', label: 'Tarefas' },
  { to: '/calendario', label: 'Calendário' },
  { to: '/capacidade', label: 'Capacidade' },
  { to: '/historico', label: 'Histórico' },
]

function linkClassName({ isActive }: { isActive: boolean }) {
  return isActive ? 'app-shell__link app-shell__link--active' : 'app-shell__link'
}

function NavPrincipal({ className }: { className: string }) {
  return (
    <nav className={className} aria-label="Principal">
      {NAV_PRINCIPAL.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end === true} className={linkClassName}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}

function ContaNav({ className }: { className: string }) {
  const { logout } = useAuth()

  return (
    <div className={className}>
      <NavLink to="/perfil" className={linkClassName}>
        Perfil
      </NavLink>
      <button type="button" className="app-shell__logout" onClick={() => void logout()}>
        Sair
      </button>
    </div>
  )
}

function XpSlot() {
  const { progresso } = useXpProgress()
  return (
    <div
      className="app-shell__xp-slot"
      role="region"
      aria-label="Progresso de XP"
      data-xp-slot
    >
      {progresso ? <XpBar progresso={progresso} /> : null}
    </div>
  )
}

function ShellAtalhos() {
  const [quickAddAberto, setQuickAddAberto] = useState(false)
  const [ajudaAberta, setAjudaAberta] = useState(false)

  const abrirNova = useCallback(() => setQuickAddAberto(true), [])
  const abrirAjuda = useCallback(() => setAjudaAberta(true), [])

  useAtalhosTeclado({
    onNovaTarefa: abrirNova,
    onAjuda: abrirAjuda,
  })

  return (
    <>
      <QuickAddTarefaDialog open={quickAddAberto} onClose={() => setQuickAddAberto(false)} />
      <AtalhosAjudaDialog open={ajudaAberta} onClose={() => setAjudaAberta(false)} />
    </>
  )
}

export function AppShell() {
  return (
    <ToastProvider>
      <XpProgressProvider>
        <div className="app-shell">
          <aside className="app-shell__sidebar" aria-label="Menu">
            <p className="app-shell__brand">Controle de Rotina</p>
            <NavPrincipal className="app-shell__nav app-shell__nav--sidebar" />
            <ContaNav className="app-shell__conta app-shell__conta--sidebar" />
          </aside>

          <div className="app-shell__main">
            <header className="app-shell__top">
              <XpSlot />
              <ContaNav className="app-shell__conta app-shell__conta--mobile" />
            </header>

            <div className="app-shell__content">
              <Outlet />
            </div>
          </div>

          <NavPrincipal className="app-shell__nav app-shell__nav--bottom" />
          <ShellAtalhos />
        </div>
      </XpProgressProvider>
    </ToastProvider>
  )
}
