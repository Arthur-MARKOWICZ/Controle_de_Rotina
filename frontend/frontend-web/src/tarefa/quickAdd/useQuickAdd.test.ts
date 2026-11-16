import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as tagApi from '../../tag/api/tagApi'
import { interpretarQuickAdd, type TokenTag } from './interpretarQuickAdd'
import { camposDoResultado, useQuickAdd } from './useQuickAdd'

vi.mock('../../tag/api/tagApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../tag/api/tagApi')>()),
  listarTags: vi.fn(),
  criarTag: vi.fn(),
}))

/** Quarta-feira 18/11/2026 10:00 BRT. */
const REF = new Date('2026-11-18T13:00:00.000Z')
const FACULDADE = { id: 'tag-fac', nome: 'Faculdade', cor: '#123456' }

type Props = { texto: string; ativo: boolean }

function renderUseQuickAdd(inicial: Props) {
  return renderHook((props: Props) => useQuickAdd({ ...props, referencia: REF }), {
    initialProps: inicial,
  })
}

function tagPendente(result: { current: ReturnType<typeof useQuickAdd> }, nome: string): TokenTag {
  const token = result.current.resultado.tags.find((t) => t.nome === nome)
  if (!token) {
    throw new Error(`tag ${nome} não interpretada`)
  }
  return token
}

describe('useQuickAdd', () => {
  beforeEach(() => {
    vi.mocked(tagApi.listarTags).mockReset()
    vi.mocked(tagApi.listarTags).mockResolvedValue([FACULDADE])
    vi.mocked(tagApi.criarTag).mockReset()
  })

  it('deveCarregarCatalogoEResolverTagsExistentesQuandoAtivo', async () => {
    const { result } = renderUseQuickAdd({ texto: 'Prova 2h #faculdade', ativo: true })
    await waitFor(() => expect(result.current.tagsExistentes).toEqual([FACULDADE]))
    expect(result.current.resultado.duracao?.minutos).toBe(120)
    await expect(result.current.resolverTagIds()).resolves.toEqual(['tag-fac'])
    expect(tagApi.criarTag).not.toHaveBeenCalled()
  })

  it('naoDeveCarregarCatalogoQuandoInativo', () => {
    const { result } = renderUseQuickAdd({ texto: 'Prova #faculdade', ativo: false })
    expect(tagApi.listarTags).not.toHaveBeenCalled()
    expect(result.current.resultado.tags[0].tag).toBeNull()
  })

  it('deveDeixarTagsPendentesQuandoCatalogoFalha', async () => {
    vi.mocked(tagApi.listarTags).mockRejectedValue(new ApiError(500, 'Falha'))
    const { result } = renderUseQuickAdd({ texto: 'Prova #faculdade', ativo: true })
    await waitFor(() => expect(tagApi.listarTags).toHaveBeenCalled())
    expect(result.current.resultado.tags[0].tag).toBeNull()
    expect(result.current.tagsExistentes).toEqual([])
  })

  it('deveDesfazerConfirmacaoAoAlternarATagDuasVezes', () => {
    const { result } = renderUseQuickAdd({ texto: 'Treino #Academia', ativo: false })
    act(() => result.current.alternarTag(tagPendente(result, 'Academia')))
    expect(result.current.confirmadas.has('academia')).toBe(true)
    act(() => result.current.alternarTag(tagPendente(result, 'Academia')))
    expect(result.current.confirmadas.size).toBe(0)
  })

  it('naoDeveIncluirTagPendenteSemConfirmacao', async () => {
    const { result } = renderUseQuickAdd({ texto: 'Treino #academia', ativo: false })
    await expect(result.current.resolverTagIds()).resolves.toEqual([])
    expect(tagApi.criarTag).not.toHaveBeenCalled()
  })

  it('naoDeveCriarATagDuasVezesQuandoReenviaDepoisDeCriar', async () => {
    vi.mocked(tagApi.criarTag).mockResolvedValue({ id: 'tag-nova', nome: 'academia', cor: '#6B7280' })
    const { result } = renderUseQuickAdd({ texto: 'Treino #academia', ativo: false })
    act(() => result.current.alternarTag(tagPendente(result, 'academia')))

    let ids: string[] = []
    await act(async () => {
      ids = await result.current.resolverTagIds()
    })
    expect(ids).toEqual(['tag-nova'])
    expect(tagApi.criarTag).toHaveBeenCalledWith({ nome: 'academia', cor: tagApi.TAG_COR_PADRAO })

    // Reenvio (ex.: criarTarefa falhou): a tag já está no catálogo.
    await act(async () => {
      ids = await result.current.resolverTagIds()
    })
    expect(ids).toEqual(['tag-nova'])
    expect(tagApi.criarTag).toHaveBeenCalledTimes(1)
  })

  it('devePropagarErroQuandoCriarTagFalha', async () => {
    vi.mocked(tagApi.criarTag).mockRejectedValue(new ApiError(409, 'Já existe uma tag com esse nome.'))
    const { result } = renderUseQuickAdd({ texto: 'Treino #academia', ativo: false })
    act(() => result.current.alternarTag(tagPendente(result, 'academia')))
    await expect(result.current.resolverTagIds()).rejects.toMatchObject({
      status: 409,
      message: 'Já existe uma tag com esse nome.',
    })
  })

  it('deveIgnorarTokenRejeitadoAteResetar', () => {
    const { result } = renderUseQuickAdd({ texto: 'Ler 2h', ativo: false })
    const token = result.current.resultado.duracao!
    act(() => result.current.rejeitar(token))
    expect(result.current.resultado.duracao).toBeNull()
    expect(result.current.resultado.titulo).toBe('Ler 2h')
    act(() => result.current.resetar())
    expect(result.current.resultado.duracao?.minutos).toBe(120)
  })

  it('naoDeveAplicarCatalogoDeChamadaCanceladaAoDesativar', async () => {
    let resolver: (tags: tagApi.TagResponse[]) => void = () => {}
    vi.mocked(tagApi.listarTags).mockReturnValue(new Promise((r) => (resolver = r)))
    const { result, rerender } = renderUseQuickAdd({ texto: '#faculdade', ativo: true })
    rerender({ texto: '#faculdade', ativo: false })
    await act(async () => resolver([FACULDADE]))
    expect(result.current.resultado.tags[0].tag).toBeNull()
  })

  it('deveDevolverCamposVaziosParaResultadoSemTokens', () => {
    expect(camposDoResultado(interpretarQuickAdd('Comprar pão', { ref: REF }))).toEqual({})
  })
})
