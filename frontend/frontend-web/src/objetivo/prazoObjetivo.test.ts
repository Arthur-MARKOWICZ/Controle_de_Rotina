import { describe, expect, it, vi } from 'vitest'

import { rotuloArquivamento, rotuloConclusao, rotuloPrazo, situacaoPrazo } from './prazoObjetivo'

const HOJE = '2026-11-17'

describe('prazoObjetivo', () => {
  it('prazo futuro mostra data local e dias restantes', () => {
    expect(situacaoPrazo('2026-11-20', HOJE)).toBe('futuro')
    expect(rotuloPrazo('2026-11-20', HOJE)).toBe('Até 20 nov · faltam 3 dias')
  })

  it('prazo de amanhã usa singular', () => {
    expect(rotuloPrazo('2026-11-18', HOJE)).toBe('Até 18 nov · falta 1 dia')
  })

  it('prazo igual a hoje é "Vence hoje", não vencido', () => {
    expect(situacaoPrazo(HOJE, HOJE)).toBe('hoje')
    expect(rotuloPrazo(HOJE, HOJE)).toBe('Vence hoje')
  })

  it('às 23:30 BRT (02:30Z do dia seguinte) o prazo do dia ainda vence hoje', () => {
    vi.useFakeTimers()
    try {
      vi.setSystemTime(new Date('2026-11-18T02:30:00Z'))
      expect(situacaoPrazo('2026-11-17')).toBe('hoje')
      expect(situacaoPrazo('2026-11-16')).toBe('vencido')
    } finally {
      vi.useRealTimers()
    }
  })

  it('virada de ano conta os dias e mostra o ano quando difere', () => {
    expect(rotuloPrazo('2027-01-02', '2026-12-31')).toBe('Até 2 jan 2027 · faltam 2 dias')
  })

  it('virada de mês conta os dias corretamente', () => {
    expect(rotuloPrazo('2026-12-01', '2026-11-30')).toBe('Até 1 dez · falta 1 dia')
  })

  it('sem prazo não tem rótulo', () => {
    expect(situacaoPrazo(null, HOJE)).toBe('sem-prazo')
    expect(rotuloPrazo(null, HOJE)).toBeNull()
  })

  it('prazo de ontem é vencido', () => {
    expect(situacaoPrazo('2026-11-16', HOJE)).toBe('vencido')
    expect(rotuloPrazo('2026-11-16', HOJE)).toBe('Prazo vencido · 16 nov')
  })

  it('prazo vazio é tratado como sem prazo', () => {
    expect(situacaoPrazo('', HOJE)).toBe('sem-prazo')
    expect(rotuloPrazo('', HOJE)).toBeNull()
  })

  it('data malformada não lança e não gera rótulo', () => {
    expect(() => rotuloPrazo('31/12/2026', HOJE)).not.toThrow()
    expect(situacaoPrazo('31/12/2026', HOJE)).toBe('sem-prazo')
    expect(rotuloPrazo('31/12/2026', HOJE)).toBeNull()
  })

  it('conclusão às 02:30Z cai no dia anterior em BRT', () => {
    expect(rotuloConclusao('2026-11-13T02:30:00Z', HOJE)).toBe('Concluído em 12 nov')
  })

  it('conclusão ausente ou inválida não gera rótulo', () => {
    expect(rotuloConclusao(null, HOJE)).toBeNull()
    expect(rotuloConclusao('ontem', HOJE)).toBeNull()
  })

  it('arquivamento mostra a data local', () => {
    expect(rotuloArquivamento('2026-11-15T15:00:00Z', HOJE)).toBe('Arquivado em 15 nov')
  })

  it('arquivamento às 02:30Z cai no dia anterior em BRT', () => {
    expect(rotuloArquivamento('2026-11-13T02:30:00Z', HOJE)).toBe('Arquivado em 12 nov')
  })

  it('arquivamento na virada de mês em BRT fica no último dia do mês', () => {
    expect(rotuloArquivamento('2026-12-01T02:30:00Z', '2026-12-05')).toBe('Arquivado em 30 nov')
  })

  it('arquivamento em outro ano mostra o ano', () => {
    expect(rotuloArquivamento('2025-12-20T15:00:00Z', HOJE)).toBe('Arquivado em 20 dez 2025')
  })

  it('arquivamento na virada de ano em BRT fica no último dia do ano', () => {
    expect(rotuloArquivamento('2027-01-01T02:59:00Z', '2027-01-10')).toBe('Arquivado em 31 dez 2026')
  })

  it('arquivamento ausente ou inválido não gera rótulo', () => {
    expect(rotuloArquivamento(null, HOJE)).toBeNull()
    expect(rotuloArquivamento(undefined, HOJE)).toBeNull()
    expect(rotuloArquivamento('ontem', HOJE)).toBeNull()
  })
})
