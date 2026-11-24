import { describe, expect, it } from 'vitest'

import type { ObjetivoEvolucao } from './api/objetivoApi'
import { diasEntreDatas, montarSerie } from './evolucaoObjetivo'

const HOJE = '2026-11-24'

const ATIVO = { status: 'ATIVO', concluidoEm: null, arquivadoEm: null } as const

function evolucao(
  pontos: [string, number, number][],
  antesDoHistorico = 0,
): ObjetivoEvolucao {
  return {
    antesDoHistorico,
    pontos: pontos.map(([data, minutosNoDia, minutosAcumulados]) => ({
      data,
      minutosNoDia,
      minutosAcumulados,
    })),
  }
}

describe('montarSerie', () => {
  it('ativo vai do primeiro ponto até hoje, com o total igual ao último acumulado', () => {
    const serie = montarSerie(evolucao([['2026-11-03', 30, 30], ['2026-11-10', 45, 75]]), ATIVO, HOJE)

    expect(serie).not.toBeNull()
    expect(serie?.temGrafico).toBe(true)
    expect(serie?.inicio).toBe('2026-11-03')
    expect(serie?.fim).toBe(HOJE)
    expect(serie?.total).toBe(75)
    expect(serie?.marcador).toBeNull()
    expect(serie?.degraus).toEqual([
      { data: '2026-11-03', noDia: 30, acumulado: 30 },
      { data: '2026-11-10', noDia: 45, acumulado: 75 },
    ])
  })

  it('ativo cujo último ponto é hoje termina hoje', () => {
    const serie = montarSerie(evolucao([['2026-11-24', 30, 30]]), ATIVO, HOJE)

    expect(serie?.inicio).toBe(HOJE)
    expect(serie?.fim).toBe(HOJE)
  })

  it('concluído termina no encerramento e marca o dia', () => {
    const serie = montarSerie(
      evolucao([['2026-11-03', 30, 30], ['2026-11-10', 45, 75]]),
      { status: 'CONCLUIDO', concluidoEm: '2026-11-12T15:00:00Z', arquivadoEm: null },
      HOJE,
    )

    expect(serie?.fim).toBe('2026-11-12')
    expect(serie?.marcador).toEqual({ data: '2026-11-12', rotulo: 'Concluído em 12 nov' })
  })

  it('ponto depois do encerramento estende o eixo e o marcador continua no encerramento', () => {
    const serie = montarSerie(
      evolucao([['2026-11-03', 30, 30], ['2026-11-15', 30, 60]]),
      { status: 'CONCLUIDO', concluidoEm: '2026-11-12T15:00:00Z', arquivadoEm: null },
      HOJE,
    )

    expect(serie?.fim).toBe('2026-11-15')
    expect(serie?.marcador?.data).toBe('2026-11-12')
  })

  it('arquivado direto de ativo marca o arquivamento', () => {
    const serie = montarSerie(
      evolucao([['2026-11-03', 30, 30]]),
      { status: 'ARQUIVADO', concluidoEm: null, arquivadoEm: '2026-11-18T15:00:00Z' },
      HOJE,
    )

    expect(serie?.marcador).toEqual({ data: '2026-11-18', rotulo: 'Arquivado em 18 nov' })
    expect(serie?.fim).toBe('2026-11-18')
  })

  it('concluído e depois arquivado marca a conclusão, como a API ordena', () => {
    const serie = montarSerie(
      evolucao([['2026-11-03', 30, 30]]),
      { status: 'ARQUIVADO', concluidoEm: '2026-11-08T15:00:00Z', arquivadoEm: '2026-11-20T15:00:00Z' },
      HOJE,
    )

    expect(serie?.marcador).toEqual({ data: '2026-11-08', rotulo: 'Concluído em 8 nov' })
  })

  it('encerramento antes do primeiro ponto puxa o início do eixo para o marcador', () => {
    const serie = montarSerie(
      evolucao([['2026-11-15', 30, 30]]),
      { status: 'CONCLUIDO', concluidoEm: '2026-11-05T15:00:00Z', arquivadoEm: null },
      HOJE,
    )

    expect(serie?.inicio).toBe('2026-11-05')
    expect(serie?.fim).toBe('2026-11-15')
  })

  it('encerramento às 02:30Z cai no dia anterior em BRT, na virada de mês', () => {
    const serie = montarSerie(
      evolucao([['2026-11-20', 30, 30]]),
      { status: 'CONCLUIDO', concluidoEm: '2026-12-01T02:30:00Z', arquivadoEm: null },
      '2026-12-05',
    )

    expect(serie?.marcador).toEqual({ data: '2026-11-30', rotulo: 'Concluído em 30 nov' })
    expect(serie?.fim).toBe('2026-11-30')
  })

  it('mantém os dias em ordem atravessando a virada de mês', () => {
    const serie = montarSerie(
      evolucao([['2026-12-01', 15, 45], ['2026-11-30', 30, 30]]),
      ATIVO,
      '2026-12-05',
    )

    expect(serie?.degraus.map((d) => d.data)).toEqual(['2026-11-30', '2026-12-01'])
    expect(serie?.inicio).toBe('2026-11-30')
  })

  it('só antes do histórico mostra o total sem gráfico', () => {
    const serie = montarSerie(evolucao([], 90), ATIVO, HOJE)

    expect(serie).toMatchObject({ temGrafico: false, total: 90, antesDoHistorico: 90, degraus: [] })
    expect(serie?.marcador).toBeNull()
  })

  it('resposta sem nada devolve null', () => {
    expect(montarSerie(evolucao([], 0), ATIVO, HOJE)).toBeNull()
  })

  it('antes do histórico negativo vindo da API é tratado como zero', () => {
    const serie = montarSerie(evolucao([['2026-11-03', 30, 30]], -10), ATIVO, HOJE)

    expect(serie?.antesDoHistorico).toBe(0)
  })

  it('encerrado com data inválida fica sem marcador e o eixo vai até hoje', () => {
    const serie = montarSerie(
      evolucao([['2026-11-03', 30, 30]]),
      { status: 'CONCLUIDO', concluidoEm: 'ontem', arquivadoEm: null },
      HOJE,
    )

    expect(serie?.marcador).toBeNull()
    expect(serie?.fim).toBe(HOJE)
  })

  it('preserva o "antes do histórico" ao lado dos degraus', () => {
    const serie = montarSerie(evolucao([['2026-11-05', 30, 150]], 120), ATIVO, HOJE)

    expect(serie).toMatchObject({ antesDoHistorico: 120, total: 150, temGrafico: true })
  })
})

describe('diasEntreDatas', () => {
  it('conta dias atravessando o mês', () => {
    expect(diasEntreDatas('2026-11-30', '2026-12-02')).toBe(2)
  })

  it('é zero para o mesmo dia', () => {
    expect(diasEntreDatas('2026-11-30', '2026-11-30')).toBe(0)
  })
})
