const SUFIXO = 'Controle de Rotina'

/**
 * Título da aba por pathname (rotas protegidas e auth pública).
 * Ex.: "Tarefas · Controle de Rotina"
 */
export function tituloPorRota(pathname: string): string {
  if (pathname === '/') {
    return `Hoje · ${SUFIXO}`
  }
  if (pathname === '/tarefas') {
    return `Tarefas · ${SUFIXO}`
  }
  if (pathname === '/tarefas/nova') {
    return `Nova tarefa · ${SUFIXO}`
  }
  if (/^\/tarefas\/[^/]+$/.test(pathname)) {
    return `Editar tarefa · ${SUFIXO}`
  }
  if (pathname === '/capacidade') {
    return `Capacidade · ${SUFIXO}`
  }
  if (pathname === '/historico') {
    return `Histórico · ${SUFIXO}`
  }
  if (pathname === '/perfil') {
    return `Perfil · ${SUFIXO}`
  }
  if (pathname === '/login') {
    return `Login · ${SUFIXO}`
  }
  if (pathname === '/cadastro') {
    return `Cadastro · ${SUFIXO}`
  }
  return SUFIXO
}
