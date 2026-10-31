const SUFIXO = 'Controle de Rotina'

const ROTULOS: [RegExp, string][] = [
  [/^\/$/, 'Hoje'],
  [/^\/tarefas$/, 'Tarefas'],
  [/^\/tarefas\/nova$/, 'Nova tarefa'],
  [/^\/tarefas\/[^/]+$/, 'Editar tarefa'],
  [/^\/projetos$/, 'Projetos'],
  [/^\/projetos\/nova$/, 'Novo projeto'],
  [/^\/projetos\/[^/]+$/, 'Projeto'],
  [/^\/calendario$/, 'Calendário'],
  [/^\/capacidade$/, 'Capacidade'],
  [/^\/historico$/, 'Histórico'],
  [/^\/perfil$/, 'Perfil'],
  [/^\/login$/, 'Login'],
  [/^\/cadastro$/, 'Cadastro'],
]

/** Nome curto da página por pathname (ex.: "Tarefas"); `null` se a rota não é conhecida. */
export function rotuloPorRota(pathname: string): string | null {
  return ROTULOS.find(([padrao]) => padrao.test(pathname))?.[1] ?? null
}

/**
 * Título da aba por pathname (rotas protegidas e auth pública).
 * Ex.: "Tarefas · Controle de Rotina"
 */
export function tituloPorRota(pathname: string): string {
  const rotulo = rotuloPorRota(pathname)
  return rotulo ? `${rotulo} · ${SUFIXO}` : SUFIXO
}
