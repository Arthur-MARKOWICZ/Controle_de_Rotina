import { describe, expect, it } from 'vitest'

import {
  escreverFiltrosNaUrl,
  FILTROS_LISTA_INICIAIS,
  lerFiltrosDaUrl,
  paramsApiDaVisao,
} from './listaUrl'

describe('listaUrl (T1)', () => {
  it('round-trip preserva visão, filtros e página', () => {
    const filtros = {
      ...FILTROS_LISTA_INICIAIS,
      visao: 'pendentes' as const,
      prioridade: ['ALTA' as const],
      sort: 'duracao' as const,
      direction: 'desc' as const,
      page: 2,
      size: 50,
    }
    const params = escreverFiltrosNaUrl(filtros)
    expect(lerFiltrosDaUrl(params)).toEqual(filtros)
  })

  it('T2: round-trip preserva projetoId na query string', () => {
    const filtros = {
      ...FILTROS_LISTA_INICIAIS,
      projetoId: 'proj-abc',
    }
    const params = escreverFiltrosNaUrl(filtros)
    expect(params.get('projetoId')).toBe('proj-abc')
    expect(lerFiltrosDaUrl(params).projetoId).toBe('proj-abc')
  })

  it('paramsApiDaVisao mapeia chips para API', () => {
    expect(paramsApiDaVisao('todas')).toEqual({})
    expect(paramsApiDaVisao('pendentes')).toEqual({ status: ['PENDENTE'] })
    expect(paramsApiDaVisao('em_progresso')).toEqual({ status: ['EM_PROGRESSO'] })
    expect(paramsApiDaVisao('finalizadas')).toEqual({ status: ['FINALIZADA'] })
    expect(paramsApiDaVisao('excluidas')).toEqual({ somenteDeletadas: true })
  })
})
