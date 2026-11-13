const SUFIXO = 'Controle de Rotina'

const ROTULOS: [RegExp, string][] = [
  [/^\/plano$/, 'Plano'],
  [/^\/tarefas$/, 'Tarefas'],
  [/^\/tarefas\/nova$/, 'Nova tarefa'],
  [/^\/tarefas\/[^/]+$/, 'Editar tarefa'],
  [/^\/decidir$/, 'Decidir'],
  [/^\/decidir\/nova$/, 'Novo item'],
  [/^\/decidir\/[^/]+$/, 'Editar item'],
  [/^\/projetos$/, 'Projetos'],
  [/^\/projetos\/nova$/, 'Novo projeto'],
  [/^\/projetos\/[^/]+$/, 'Projeto'],
  [/^\/habitos$/, 'Hábitos'],
  [/^\/habitos\/nova$/, 'Novo hábito'],
  [/^\/habitos\/[^/]+$/, 'Editar hábito'],
  [/^\/calendario$/, 'Calendário'],
  [/^\/capacidade$/, 'Capacidade'],
  [/^\/historico$/, 'Histórico'],
  [/^\/revisao$/, 'Revisão da semana'],
  [/^\/tags$/, 'Tags'],
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
