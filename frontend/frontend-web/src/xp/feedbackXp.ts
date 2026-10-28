import type { XpConclusao } from './types'

/** Monta mensagens de toast após concluir (ganho e level-up). */
export function mensagensFeedbackXp(
  xp: XpConclusao,
  nivelAnterior: number | null,
): string[] {
  const mensagens: string[] = []

  if (xp.ganhoNesteRequest > 0) {
    const temBonus = xp.eventos.some((evento) => evento.tipo === 'TAREFA_FINALIZADA')
    mensagens.push(
      temBonus
        ? `+${xp.ganhoNesteRequest} XP (inclui bônus)`
        : `+${xp.ganhoNesteRequest} XP`,
    )
  }

  if (nivelAnterior != null && xp.progresso.nivel > nivelAnterior) {
    mensagens.push(`Nível ${xp.progresso.nivel}!`)
  }

  return mensagens
}
