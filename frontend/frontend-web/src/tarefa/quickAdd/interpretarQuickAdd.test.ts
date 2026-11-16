import { describe, expect, it } from 'vitest'

import { fimDoDiaBrtIso } from '../dataCivilBrt'
import {
  ERRO_DURACAO,
  interpretarQuickAdd,
  rotuloDuracao,
  rotuloPrazo,
} from './interpretarQuickAdd'

/** Quarta-feira 18/11/2026 10:00 BRT. */
const QUA = new Date('2026-11-18T13:00:00.000Z')
/** Sexta-feira 20/11/2026 10:00 BRT. */
const SEX = new Date('2026-11-20T13:00:00.000Z')

const CATALOGO = [
  { id: 't1', nome: 'Faculdade', cor: '#111111' },
  { id: 't2', nome: 'Estudo', cor: '#222222' },
]

function interpretar(texto: string, ref = QUA, rejeitados?: Set<string>) {
  return interpretarQuickAdd(texto, { ref, tagsCatalogo: CATALOGO, rejeitados })
}

describe('interpretarQuickAdd', () => {
  describe('caminho feliz', () => {
    it('deveExtrairTodosOsTokensDaFraseCompleta', () => {
      const r = interpretar('Estudar Spring 2h sex !alta #faculdade')
      expect(r.titulo).toBe('Estudar Spring')
      expect(r.duracao).toMatchObject({ minutos: 120, erro: null, textoOriginal: '2h' })
      expect(r.prazo?.prazoIso).toBe(fimDoDiaBrtIso(2026, 11, 20))
      expect(r.prioridade?.prioridade).toBe('ALTA')
      expect(r.tags).toHaveLength(1)
      expect(r.tags[0].tag?.id).toBe('t1')
      expect(r.tokens.map((t) => t.tipo)).toEqual(['duracao', 'prazo', 'prioridade', 'tag'])
    })

    it('deveDevolverSoOTituloQuandoNaoHaTokens', () => {
      const r = interpretar('Comprar pão')
      expect(r.titulo).toBe('Comprar pão')
      expect(r.tokens).toEqual([])
    })
  })

  describe('duração', () => {
    it.each([
      ['1h30', 90],
      ['1h30min', 90],
      ['90m', 90],
      ['45min', 45],
      ['3H', 180],
    ])('deveConverter %s em %i minutos', (token, minutos) => {
      const r = interpretar(`Ler artigo ${token}`)
      expect(r.duracao?.minutos).toBe(minutos)
      expect(r.duracao?.erro).toBeNull()
      expect(r.titulo).toBe('Ler artigo')
    })

    it('deveMarcarErroQuandoDuracaoNaoEhMultiploDe15', () => {
      const r = interpretar('Ligar 20min')
      expect(r.duracao).toMatchObject({ minutos: 20, erro: ERRO_DURACAO })
      expect(r.titulo).toBe('Ligar')
    })

    it('deveMarcarErroQuandoDuracaoEhZero', () => {
      expect(interpretar('Nada 0h').duracao?.erro).toBe(ERRO_DURACAO)
    })
  })

  describe('prazo', () => {
    it('deveResolverHojeEAmanhaComESemAcento', () => {
      expect(interpretar('Pagar hoje').prazo?.data).toEqual({ y: 2026, m: 11, d: 18 })
      expect(interpretar('Pagar amanhã').prazo?.data).toEqual({ y: 2026, m: 11, d: 19 })
      expect(interpretar('Pagar AMANHA').prazo?.data).toEqual({ y: 2026, m: 11, d: 19 })
    })

    it.each(['sab', 'sáb', 'sábado', 'Sabado'])('deveReconhecer %s como sábado', (token) => {
      expect(interpretar(`Feira ${token}`).prazo?.data).toEqual({ y: 2026, m: 11, d: 21 })
    })

    it('deveReconhecerSextaFeiraComHifen', () => {
      expect(interpretar('Entregar sexta-feira').prazo?.data).toEqual({ y: 2026, m: 11, d: 20 })
    })

    it('devePularSeteDiasQuandoODiaDigitadoEhHoje', () => {
      expect(interpretar('Reunião sex', SEX).prazo?.data).toEqual({ y: 2026, m: 11, d: 27 })
    })

    it('deveUsarODiaCivilBrtNaViradaDeDomingoParaSegunda', () => {
      // dom 22/11 23:59 BRT = seg 02:59 UTC
      const r = interpretar('Planejar seg', new Date('2026-11-23T02:59:00.000Z'))
      expect(r.prazo?.data).toEqual({ y: 2026, m: 11, d: 23 })
    })

    it('deveJogarDdMmJaPassadoParaOProximoAno', () => {
      expect(interpretar('Renovar 10/01').prazo?.data).toEqual({ y: 2027, m: 1, d: 10 })
      expect(interpretar('Natal 25/12').prazo?.data).toEqual({ y: 2026, m: 12, d: 25 })
    })

    it('deveManterDataInexistenteNoTitulo', () => {
      const r = interpretar('Evento 31/02')
      expect(r.prazo).toBeNull()
      expect(r.titulo).toBe('Evento 31/02')
    })

    it('deveRotularPrazoComDiaDaSemana', () => {
      expect(rotuloPrazo({ y: 2026, m: 11, d: 21 })).toBe('sáb 21/11')
    })
  })

  describe('prioridade', () => {
    it('deveReconhecerPrioridadeMediaComAcento', () => {
      expect(interpretar('Revisar !média').prioridade?.prioridade).toBe('MEDIA')
      expect(interpretar('Revisar !baixa').prioridade?.prioridade).toBe('BAIXA')
    })

    it('deveManterPrioridadeDesconhecidaNoTitulo', () => {
      const r = interpretar('Urgente !agora')
      expect(r.prioridade).toBeNull()
      expect(r.titulo).toBe('Urgente !agora')
    })
  })

  describe('tags', () => {
    it('deveDeduplicarTagsIgnorandoCaixa', () => {
      const r = interpretar('Ler #estudo #leitura #Estudo')
      expect(r.tags.map((t) => t.nome)).toEqual(['estudo', 'leitura'])
      expect(r.tags[0].tag?.id).toBe('t2')
      expect(r.tags[1].tag).toBeNull()
      expect(r.titulo).toBe('Ler')
    })

    it('naoDeveCasarTagQueSoDifereNoAcento', () => {
      const r = interpretarQuickAdd('Ler #ciencia #Ciência', {
        ref: QUA,
        tagsCatalogo: [{ id: 't9', nome: 'Ciência', cor: '#999999' }],
      })
      expect(r.tags.map((t) => [t.nome, t.tag?.id ?? null])).toEqual([
        ['ciencia', null],
        ['Ciência', 't9'],
      ])
    })

    it('deveMarcarTagPendenteQuandoCatalogoVazio', () => {
      const r = interpretarQuickAdd('Treino #academia', { ref: QUA })
      expect(r.tags[0]).toMatchObject({ nome: 'academia', tag: null })
    })

    it('deveManterCerquilhaSozinhaNoTitulo', () => {
      expect(interpretar('Item # solto').titulo).toBe('Item # solto')
    })
  })

  describe('anomalias', () => {
    it('naoDeveInterpretarTokenDentroDePalavra', () => {
      const r = interpretar('Revisar sexologia2h')
      expect(r.tokens).toEqual([])
      expect(r.titulo).toBe('Revisar sexologia2h')
    })

    it('deveDevolverTituloVazioQuandoTextoTemSoTokens', () => {
      const r = interpretar('2h amanhã')
      expect(r.titulo).toBe('')
      expect(r.tokens).toHaveLength(2)
    })

    it('deveDevolverResultadoVazioParaTextoSoComEspacos', () => {
      expect(interpretar('   ')).toMatchObject({ titulo: '', tokens: [] })
    })

    it('deveNormalizarEspacosMultiplos', () => {
      expect(interpretar('  Ler   o   livro  2h ').titulo).toBe('Ler o livro')
    })

    it('deveUsarOUltimoTokenRepetidoEDevolverOsAnterioresAoTitulo', () => {
      const r = interpretar('Treino 1h 2h')
      expect(r.duracao?.minutos).toBe(120)
      expect(r.titulo).toBe('Treino 1h')
    })

    it('naoDeveInterpretarTokensEscapados', () => {
      const r = interpretar('Ler cap \\2h da apostila \\#1')
      expect(r.tokens).toEqual([])
      expect(r.titulo).toBe('Ler cap 2h da apostila #1')
    })

    it('naoDeveInterpretarTokenRejeitado', () => {
      const r = interpretar('Ler cap 2h amanhã', QUA, new Set(['2h']))
      expect(r.duracao).toBeNull()
      expect(r.prazo).not.toBeNull()
      expect(r.titulo).toBe('Ler cap 2h')
    })

    it('deveUsarTokenAnteriorQuandoOUltimoFoiRejeitado', () => {
      const r = interpretar('Treino 1h 2h', QUA, new Set(['2h']))
      expect(r.duracao?.minutos).toBe(60)
      expect(r.titulo).toBe('Treino 2h')
    })
  })

  it('deveRotularDuracao', () => {
    expect(rotuloDuracao(45)).toBe('45 min')
    expect(rotuloDuracao(120)).toBe('2h')
    expect(rotuloDuracao(90)).toBe('1h30')
  })
})
