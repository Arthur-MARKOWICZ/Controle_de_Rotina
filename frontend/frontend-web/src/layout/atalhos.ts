/** Destinos do chord `G` + letra (Hoje / Tarefas / Capacidade / Histórico). */
export const ATALHOS_GO: Record<string, string> = {
  h: '/',
  t: '/tarefas',
  c: '/capacidade',
  i: '/historico',
}

export const LISTA_ATALHOS_AJUDA: { teclas: string; descricao: string }[] = [
  { teclas: 'N', descricao: 'Nova tarefa (quick add)' },
  { teclas: 'G H', descricao: 'Ir para Hoje' },
  { teclas: 'G T', descricao: 'Ir para Tarefas' },
  { teclas: 'G C', descricao: 'Ir para Capacidade' },
  { teclas: 'G I', descricao: 'Ir para Histórico' },
  { teclas: '?', descricao: 'Abrir esta ajuda' },
]

export function focoEmCampoEditavel(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
    return true
  }
  return Boolean(target.isContentEditable)
}

export function rotaPorAtalhoGo(letra: string): string | null {
  return ATALHOS_GO[letra.toLowerCase()] ?? null
}
