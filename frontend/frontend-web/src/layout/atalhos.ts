/** Destinos do chord `G` + letra (Plano / Tarefas / Projetos / Capacidade / Histórico). */
export const ATALHOS_GO: Record<string, string> = {
  h: '/plano',
  t: '/tarefas',
  p: '/projetos',
  c: '/capacidade',
  i: '/historico',
}

export const LISTA_ATALHOS_AJUDA: { teclas: string; descricao: string }[] = [
  { teclas: 'N', descricao: 'Nova tarefa (inline em Tarefas; quick add nas demais)' },
  { teclas: 'G H', descricao: 'Ir para Plano' },
  { teclas: 'G T', descricao: 'Ir para Tarefas' },
  { teclas: 'G P', descricao: 'Ir para Projetos' },
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
