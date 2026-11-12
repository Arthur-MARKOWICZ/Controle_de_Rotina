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

  it('round-trip preserva tagId repetível na query string', () => {
    const filtros = {
      ...FILTROS_LISTA_INICIAIS,
      tagIds: ['tag-a', 'tag-b'],
    }
    const params = escreverFiltrosNaUrl(filtros)
    expect(params.getAll('tagId')).toEqual(['tag-a', 'tag-b'])
    expect(lerFiltrosDaUrl(params).tagIds).toEqual(['tag-a', 'tag-b'])
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

  it('round-trip preserva filtros de ausência sem prazo, projeto e tag', () => {
    const filtros = {
      ...FILTROS_LISTA_INICIAIS,
      semPrazo: true,
      semProjeto: true,
      semTag: true,
    }
    const params = escreverFiltrosNaUrl(filtros)
    expect(params.get('semPrazo')).toBe('true')
    expect(params.get('semProjeto')).toBe('true')
    expect(params.get('semTag')).toBe('true')
    expect(lerFiltrosDaUrl(params)).toEqual(filtros)
  })

  it('ausência não grava o valor positivo correspondente na URL', () => {
    const params = escreverFiltrosNaUrl({
      ...FILTROS_LISTA_INICIAIS,
      prazoDe: '2026-11-01T10:00',
      projetoId: 'proj-abc',
      tagIds: ['tag-a'],
      semPrazo: true,
      semProjeto: true,
      semTag: true,
    })
    expect(params.get('prazoDe')).toBeNull()
    expect(params.get('projetoId')).toBeNull()
    expect(params.getAll('tagId')).toEqual([])
  })

  it('paramsApiDaVisao mapeia chips para API', () => {
    expect(paramsApiDaVisao('todas')).toEqual({
      status: ['PENDENTE', 'EM_PROGRESSO', 'ATRASADA', 'CANCELADA'],
    })
    expect(paramsApiDaVisao('pendentes')).toEqual({ status: ['PENDENTE'] })
    expect(paramsApiDaVisao('em_progresso')).toEqual({ status: ['EM_PROGRESSO'] })
    expect(paramsApiDaVisao('finalizadas')).toEqual({ status: ['FINALIZADA'] })
    expect(paramsApiDaVisao('excluidas')).toEqual({ somenteDeletadas: true })
  })

  it('visão todas não inclui FINALIZADA (só via filtro finalizadas)', () => {
    const status = paramsApiDaVisao('todas').status ?? []
    expect(status).not.toContain('FINALIZADA')
    expect(paramsApiDaVisao('finalizadas').status).toEqual(['FINALIZADA'])
  })
})
