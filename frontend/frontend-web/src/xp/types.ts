export type XpProgresso = {
  xpTotal: number
  nivel: number
  xpNoNivel: number
  xpParaProximoNivel: number
}

export type TipoXpEvento = 'BLOCO_CONCLUIDO' | 'TAREFA_FINALIZADA'

export type XpEventoResumo = {
  tipo: TipoXpEvento
  quantidade: number
}

export type XpConclusao = {
  ganhoNesteRequest: number
  eventos: XpEventoResumo[]
  progresso: XpProgresso
}
