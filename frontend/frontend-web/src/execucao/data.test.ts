import { describe, expect, it } from 'vitest'

import { formatarTempoRestante, minutosExcedidos, minutosRestantes } from './data'

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

  describe('minutosExcedidos', () => {
    it('é zero dentro do tempo e conta minutos além da duração', () => {
      const iniciadoEm = '2026-10-27T14:00:00.000Z'

      expect(minutosExcedidos(iniciadoEm, 60, new Date('2026-10-27T14:59:00.000Z'))).toBe(0)
      expect(minutosExcedidos(iniciadoEm, 60, new Date('2026-10-27T15:05:00.000Z'))).toBe(5)
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
