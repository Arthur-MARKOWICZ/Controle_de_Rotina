import { describe, expect, it } from 'vitest'

import { mensagensFeedbackXp } from './feedbackXp'
import type { XpConclusao } from './types'

const base: XpConclusao = {
  ganhoNesteRequest: 90,
  eventos: [{ tipo: 'BLOCO_CONCLUIDO', quantidade: 90 }],
  progresso: { xpTotal: 90, nivel: 4, xpNoNivel: 30, xpParaProximoNivel: 40 },
}

describe('mensagensFeedbackXp', () => {
  it('T5: com ganho > 0 retorna +N XP', () => {
    expect(mensagensFeedbackXp(base, 1)).toEqual(['+90 XP', 'Nível 4!'])
  })

  it('indica bônus quando há TAREFA_FINALIZADA', () => {
    const comBonus: XpConclusao = {
      ...base,
      ganhoNesteRequest: 99,
      eventos: [
        { tipo: 'BLOCO_CONCLUIDO', quantidade: 90 },
        { tipo: 'TAREFA_FINALIZADA', quantidade: 9 },
      ],
    }
    expect(mensagensFeedbackXp(comBonus, 4)).toEqual(['+99 XP (inclui bônus)'])
  })

  it('T7: level-up dispara mensagem distinta', () => {
    expect(mensagensFeedbackXp(base, 3)).toContain('Nível 4!')
  })

  it('sem ganho e sem subida não gera mensagens', () => {
    const zero: XpConclusao = {
      ganhoNesteRequest: 0,
      eventos: [],
      progresso: { xpTotal: 10, nivel: 2, xpNoNivel: 0, xpParaProximoNivel: 20 },
    }
    expect(mensagensFeedbackXp(zero, 2)).toEqual([])
  })
})
