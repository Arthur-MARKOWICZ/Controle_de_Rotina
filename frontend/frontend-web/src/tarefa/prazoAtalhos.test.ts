import { describe, expect, it } from 'vitest'

import { atalhosPrazo, fimDoDiaBrtParaDatetimeLocal } from './prazoAtalhos'

describe('atalhosPrazo (T3)', () => {
  it('chip Amanhã preenche prazo do dia seguinte em BRT (fim do dia)', () => {
    // Quarta 2026-10-28 15:00 UTC = 12:00 BRT
    const ref = new Date('2026-10-28T15:00:00.000Z')
    const atalhos = atalhosPrazo(ref)
    const amanha = atalhos.find((a) => a.id === 'amanha')

    expect(amanha).toBeDefined()
    expect(amanha!.label).toBe('Amanhã')
    expect(amanha!.prazoLocal).toBe(fimDoDiaBrtParaDatetimeLocal(2026, 10, 29))
  })

  it('chip Hoje usa a data civil BRT de referência', () => {
    const ref = new Date('2026-10-28T15:00:00.000Z')
    const hoje = atalhosPrazo(ref).find((a) => a.id === 'hoje')
    expect(hoje!.prazoLocal).toBe(fimDoDiaBrtParaDatetimeLocal(2026, 10, 28))
  })

  it('próxima segunda a partir de uma quarta é 2026-11-02', () => {
    const ref = new Date('2026-10-28T15:00:00.000Z')
    const segunda = atalhosPrazo(ref).find((a) => a.id === 'proxima_segunda')
    expect(segunda!.prazoLocal).toBe(fimDoDiaBrtParaDatetimeLocal(2026, 11, 2))
  })

  it('em 3 dias a partir de quarta é sábado 2026-10-31', () => {
    const ref = new Date('2026-10-28T15:00:00.000Z')
    const em3 = atalhosPrazo(ref).find((a) => a.id === 'em_3_dias')
    expect(em3!.prazoLocal).toBe(fimDoDiaBrtParaDatetimeLocal(2026, 10, 31))
  })

  it('próxima segunda a partir de uma segunda pula 7 dias', () => {
    const ref = new Date('2026-10-26T15:00:00.000Z')
    const segunda = atalhosPrazo(ref).find((a) => a.id === 'proxima_segunda')
    expect(segunda!.prazoLocal).toBe(fimDoDiaBrtParaDatetimeLocal(2026, 11, 2))
  })

  it('usa o dia civil BRT na virada da meia-noite', () => {
    const ref = new Date('2026-10-28T02:00:00.000Z')
    const hoje = atalhosPrazo(ref).find((a) => a.id === 'hoje')
    expect(hoje!.prazoLocal).toBe(fimDoDiaBrtParaDatetimeLocal(2026, 10, 27))
  })

  it('sempre devolve os quatro atalhos na ordem', () => {
    expect(atalhosPrazo(new Date('2026-10-28T15:00:00.000Z')).map((a) => a.id)).toEqual([
      'hoje',
      'amanha',
      'em_3_dias',
      'proxima_segunda',
    ])
  })
})
