import { describe, expect, it } from 'vitest'

import type { CapacidadeSemanalResponse } from '../capacidade/api/capacidadeSemanalApi'
import { alteracoesDiasUteis, capacidadeConfigurada } from './capacidadeInicial'

function semanal(
  diaSemana: CapacidadeSemanalResponse['diaSemana'],
  horas: number,
  ativo = true,
): CapacidadeSemanalResponse {
  return {
    id: `s-${diaSemana}`,
    diaSemana,
    horas,
    ativo,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
  }
}

describe('capacidadeConfigurada', () => {
  it('deveSerVerdadeiroQuandoAlgumDiaAtivoTemHoras', () => {
    expect(capacidadeConfigurada([semanal('SABADO', 2)])).toBe(true)
  })

  it('deveSerFalsoParaListaVazia', () => {
    expect(capacidadeConfigurada([])).toBe(false)
  })

  it('deveIgnorarDiasZeradosEInativos', () => {
    expect(capacidadeConfigurada([semanal('SEGUNDA', 0), semanal('TERCA', 6, false)])).toBe(false)
  })
})

describe('alteracoesDiasUteis', () => {
  it('deveCriarSegASexEm4hQuandoSemanaVazia', () => {
    expect(alteracoesDiasUteis([])).toEqual({
      criar: [
        { diaSemana: 'SEGUNDA', horas: 4 },
        { diaSemana: 'TERCA', horas: 4 },
        { diaSemana: 'QUARTA', horas: 4 },
        { diaSemana: 'QUINTA', horas: 4 },
        { diaSemana: 'SEXTA', horas: 4 },
      ],
      atualizar: [],
    })
  })

  it('deveAtualizarDiaUtilExistenteZeradoEmVezDeCriar', () => {
    const { criar, atualizar } = alteracoesDiasUteis([semanal('QUARTA', 0)])

    expect(atualizar).toEqual(['s-QUARTA'])
    expect(criar.map((item) => item.diaSemana)).not.toContain('QUARTA')
    expect(criar).toHaveLength(4)
  })

  it('naoDeveMexerEmDiaUtilQueJaTemHorasNemNoFimDeSemana', () => {
    const { criar, atualizar } = alteracoesDiasUteis([
      semanal('SEGUNDA', 2),
      semanal('SABADO', 0),
      semanal('DOMINGO', 0),
    ])

    expect(atualizar).toEqual([])
    expect(criar.map((item) => item.diaSemana)).toEqual(['TERCA', 'QUARTA', 'QUINTA', 'SEXTA'])
  })

  it('deveTratarRegistroInativoComoAusente', () => {
    const { criar, atualizar } = alteracoesDiasUteis([semanal('SEXTA', 0, false)])

    expect(atualizar).toEqual([])
    expect(criar.map((item) => item.diaSemana)).toContain('SEXTA')
  })
})
