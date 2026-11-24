import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as objetivoApi from './api/objetivoApi'
import {
  ACOES_POR_STATUS,
  CONFIRMACAO_ACAO,
  EXECUTAR_ACAO,
  FALHA_ACAO,
  ROTULO_ACAO,
  SUCESSO_ACAO,
  type AcaoObjetivo,
} from './acoesObjetivo'

vi.mock('./api/objetivoApi', () => ({
  concluirObjetivo: vi.fn(),
  arquivarObjetivo: vi.fn(),
  reabrirObjetivo: vi.fn(),
}))

const TODAS: AcaoObjetivo[] = ['concluir', 'arquivar', 'reabrir']

describe('acoesObjetivo', () => {
  beforeEach(() => {
    vi.mocked(objetivoApi.concluirObjetivo).mockReset()
    vi.mocked(objetivoApi.arquivarObjetivo).mockReset()
    vi.mocked(objetivoApi.reabrirObjetivo).mockReset()
  })

  it('ativo oferece concluir e arquivar', () => {
    expect(ACOES_POR_STATUS.ATIVO).toEqual(['concluir', 'arquivar'])
  })

  it('concluído oferece arquivar e reabrir, e não concluir', () => {
    expect(ACOES_POR_STATUS.CONCLUIDO).toEqual(['arquivar', 'reabrir'])
    expect(ACOES_POR_STATUS.CONCLUIDO).not.toContain('concluir')
  })

  it('arquivado oferece só reabrir', () => {
    expect(ACOES_POR_STATUS.ARQUIVADO).toEqual(['reabrir'])
  })

  it('toda ação tem rótulo, rótulo de andamento, toast e mensagem de falha', () => {
    for (const acao of TODAS) {
      expect(ROTULO_ACAO[acao].label).not.toBe('')
      expect(ROTULO_ACAO[acao].andamento).toMatch(/…$/)
      expect(SUCESSO_ACAO[acao]).toMatch(/^Objetivo /)
      expect(FALHA_ACAO[acao]).toMatch(/^Não foi possível .* o objetivo\.$/)
    }
  })

  it('concluir e arquivar pedem confirmação com o título; reabrir não', () => {
    expect(CONFIRMACAO_ACAO.concluir?.('Perder 5 kg')).toEqual({
      title: 'Concluir objetivo?',
      message: '“Perder 5 kg” sai dos ativos. Você pode reabrir depois.',
      confirmLabel: 'Concluir',
    })
    expect(CONFIRMACAO_ACAO.arquivar?.('Perder 5 kg')).toMatchObject({
      title: 'Arquivar objetivo?',
      confirmLabel: 'Arquivar',
    })
    expect(CONFIRMACAO_ACAO.reabrir).toBeUndefined()
  })

  it('cada ação chama o endpoint certo e devolve o objetivo atualizado', async () => {
    const atualizado = { id: 'obj-1' } as objetivoApi.ObjetivoResponse
    vi.mocked(objetivoApi.concluirObjetivo).mockResolvedValue(atualizado)
    vi.mocked(objetivoApi.arquivarObjetivo).mockResolvedValue(atualizado)
    vi.mocked(objetivoApi.reabrirObjetivo).mockResolvedValue(atualizado)

    await expect(EXECUTAR_ACAO.concluir('obj-1')).resolves.toBe(atualizado)
    await EXECUTAR_ACAO.arquivar('obj-1')
    await EXECUTAR_ACAO.reabrir('obj-1')

    expect(objetivoApi.concluirObjetivo).toHaveBeenCalledWith('obj-1')
    expect(objetivoApi.arquivarObjetivo).toHaveBeenCalledWith('obj-1')
    expect(objetivoApi.reabrirObjetivo).toHaveBeenCalledWith('obj-1')
  })

  it('propaga o erro da API sem engolir', async () => {
    vi.mocked(objetivoApi.concluirObjetivo).mockRejectedValue(new Error('409'))

    await expect(EXECUTAR_ACAO.concluir('obj-1')).rejects.toThrow('409')
  })
})
