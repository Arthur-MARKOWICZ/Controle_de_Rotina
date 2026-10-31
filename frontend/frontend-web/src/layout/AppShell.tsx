import {
  CalendarBlankIcon,
  ChartLineUpIcon,
  CheckCircleIcon,
  CheckSquareIcon,
  FolderSimpleIcon,
  GaugeIcon,
  PlusIcon,
  SunHorizonIcon,
  type Icon,
} from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

import { ToastProvider } from '../feedback/ToastProvider'
import { XpBar } from '../xp/XpBar'
import { XpProgressProvider, useXpProgress } from '../xp/XpProgressContext'
import { AtalhosAjudaDialog } from './AtalhosAjudaDialog'
import { rotuloPorRota } from './documentTitle'
import { MenuConta } from './MenuConta'
import { MenuMais } from './MenuMais'
import { QuickAddProvider, useQuickAdd } from './QuickAddContext'
import { useAtalhosTeclado } from './useAtalhosTeclado'

type ItemNav = { to: string; label: string; Icon: Icon; end?: boolean }

const HOJE: ItemNav = { to: '/', label: 'Hoje', Icon: SunHorizonIcon, end: true }
const TAREFAS: ItemNav = { to: '/tarefas', label: 'Tarefas', Icon: CheckSquareIcon }
const PROJETOS: ItemNav = { to: '/projetos', label: 'Projetos', Icon: FolderSimpleIcon }
const CALENDARIO: ItemNav = { to: '/calendario', label: 'Calendário', Icon: CalendarBlankIcon }
const CAPACIDADE: ItemNav = { to: '/capacidade', label: 'Capacidade', Icon: GaugeIcon }
const HISTORICO: ItemNav = { to: '/historico', label: 'Histórico', Icon: ChartLineUpIcon }

const NAV_SIDEBAR = [HOJE, TAREFAS, PROJETOS, CALENDARIO, CAPACIDADE, HISTORICO]
const NAV_BOTTOM = [HOJE, TAREFAS, CALENDARIO, CAPACIDADE]

function linkClassName({ isActive }: { isActive: boolean }) {
  return isActive ? 'app-shell__link app-shell__link--active' : 'app-shell__link'
}

function LinkNav({ item }: { item: ItemNav }) {
  const { to, label, Icon, end } = item
  return (
    <NavLink to={to} end={end === true} className={linkClassName}>
      {({ isActive }) => (
        <>
          <Icon aria-hidden weight={isActive ? 'fill' : 'regular'} />
          <span>{label}</span>
        </>
      )}
    </NavLink>
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

function BarraSuperior() {
  const { pathname } = useLocation()
  const { abrir } = useQuickAdd()

  return (
    <header className="app-shell__top">
      <p className="app-shell__titulo">{rotuloPorRota(pathname)}</p>
      <div className="app-shell__top-acoes">
        <XpSlot />
        <button
          type="button"
          className="btn btn--primary app-shell__nova"
          aria-label="Nova tarefa"
          onClick={abrir}
        >
          <PlusIcon aria-hidden />
          <span className="app-shell__nova-rotulo">Nova tarefa</span>
        </button>
      </div>
    </header>
  )
}

/** Páginas cujas rotas filhas abrem um drawer por cima: o foco é gerido pelo drawer. */
const PAGINAS_COM_DRAWER: { base: string; drawer: RegExp }[] = [
  { base: '/tarefas', drawer: /^\/tarefas\/[^/]+$/ },
  // `/projetos/:id` é o hub (página própria); só a criação abre drawer.
  { base: '/projetos', drawer: /^\/projetos\/nova$/ },
]

function naPagina(pathname: string, pagina: { base: string; drawer: RegExp }): boolean {
  return pathname === pagina.base || pagina.drawer.test(pathname)
}

function mesmaPaginaComDrawer(anterior: string, atual: string): boolean {
  return PAGINAS_COM_DRAWER.some(
    (pagina) => naPagina(anterior, pagina) && naPagina(atual, pagina),
  )
}

/** Ao trocar de rota, leva o foco ao h1 da página (ou ao conteúdo, se não houver h1). */
function ConteudoRota() {
  const { pathname } = useLocation()
  const conteudoRef = useRef<HTMLDivElement>(null)
  const rotaAnterior = useRef(pathname)

  useEffect(() => {
    if (rotaAnterior.current === pathname) {
      return
    }
    const anterior = rotaAnterior.current
    rotaAnterior.current = pathname
    if (mesmaPaginaComDrawer(anterior, pathname)) {
      return
    }
    const conteudo = conteudoRef.current
    const alvo = conteudo?.querySelector<HTMLElement>('h1') ?? conteudo
    if (!alvo) {
      return
    }
    if (!alvo.hasAttribute('tabindex')) {
      alvo.setAttribute('tabindex', '-1')
    }
    alvo.focus()
  }, [pathname])

  return (
    <div ref={conteudoRef} className="app-shell__content">
      <Outlet />
    </div>
  )
}

function ShellAtalhos() {
  const { abrir } = useQuickAdd()
  const [ajudaAberta, setAjudaAberta] = useState(false)

  const abrirAjuda = useCallback(() => setAjudaAberta(true), [])

  useAtalhosTeclado({
    onNovaTarefa: abrir,
    onAjuda: abrirAjuda,
  })

  return <AtalhosAjudaDialog open={ajudaAberta} onClose={() => setAjudaAberta(false)} />
}

export function AppShell() {
  return (
    <ToastProvider>
      <XpProgressProvider>
        <QuickAddProvider>
          <div className="app-shell">
            <aside className="app-shell__sidebar" aria-label="Menu">
              <p className="app-shell__brand">
                <CheckCircleIcon aria-hidden />
                Rotina
              </p>
              <nav className="app-shell__nav app-shell__nav--sidebar" aria-label="Principal">
                {NAV_SIDEBAR.map((item) => (
                  <LinkNav key={item.to} item={item} />
                ))}
              </nav>
              <MenuConta />
            </aside>

            <div className="app-shell__main">
              <BarraSuperior />
              <ConteudoRota />
            </div>

            <nav className="app-shell__nav app-shell__nav--bottom" aria-label="Principal">
              {NAV_BOTTOM.map((item) => (
                <LinkNav key={item.to} item={item} />
              ))}
              <MenuMais />
            </nav>
            <ShellAtalhos />
          </div>
        </QuickAddProvider>
      </XpProgressProvider>
    </ToastProvider>
  )
}
