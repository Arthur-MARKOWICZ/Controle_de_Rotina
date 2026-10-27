import { describe, expect, it } from 'vitest'

import { formatarTempoRestante, minutosRestantes } from './data'

describe('execucao/data', () => {
  describe('minutosRestantes', () => {
    it('T11: calcula restante a partir de iniciadoEm + duração', () => {
      const iniciadoEm = '2026-10-27T14:00:00.000Z'
      const agora = new Date('2026-10-27T14:28:00.000Z')

      expect(minutosRestantes(iniciadoEm, 60, agora)).toBe(32)
    })

    it('não retorna negativo quando o tempo esgota', () => {
      const iniciadoEm = '2026-10-27T14:00:00.000Z'
      const agora = new Date('2026-10-27T16:00:00.000Z')

      expect(minutosRestantes(iniciadoEm, 60, agora)).toBe(0)
    })
  })

  describe('formatarTempoRestante', () => {
    it('formata minutos e horas', () => {
      expect(formatarTempoRestante(45)).toBe('45min')
      expect(formatarTempoRestante(60)).toBe('1h')
      expect(formatarTempoRestante(92)).toBe('1h32')
    })
  })
})
