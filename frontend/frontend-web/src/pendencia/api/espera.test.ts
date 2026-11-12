import { describe, expect, it } from 'vitest'

import { esperasIrresolvidas, rotuloEspera, type EsperaResumo } from './espera'

function espera(over: Partial<EsperaResumo> = {}): EsperaResumo {
  return {
    id: 'e-1',
    alvoTipo: 'TAREFA',
    alvoId: 't-2',
    alvoTitulo: 'Revisar API',
    resolvida: false,
    ...over,
  }
}

describe('espera', () => {
  it('ignora esperas resolvidas', () => {
    const lista = [espera(), espera({ id: 'e-2', resolvida: true })]
    expect(esperasIrresolvidas(lista)).toHaveLength(1)
    expect(esperasIrresolvidas(undefined)).toEqual([])
  })

  it('usa o título do alvo quando há uma espera', () => {
    expect(rotuloEspera([espera()])).toBe('Espera Revisar API')
  })

  it('resume quando há várias esperas', () => {
    expect(rotuloEspera([espera(), espera({ id: 'e-2', alvoTitulo: 'TCC' })])).toBe('Espera 2 itens')
  })

  it('não gera rótulo sem espera irresolvida', () => {
    expect(rotuloEspera([espera({ resolvida: true })])).toBeNull()
    expect(rotuloEspera([])).toBeNull()
  })
})
