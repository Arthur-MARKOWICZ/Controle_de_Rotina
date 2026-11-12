import { CaretLeftIcon, CaretRightIcon, CheckSquareIcon, PlusIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { PageHeader } from '../../layout/PageHeader'
import { useQuickAdd } from '../../layout/QuickAddContext'
import { listarProjetos, type ProjetoResponse } from '../../projeto/api/projetoApi'
import { listarTags, type TagResponse } from '../../tag/api/tagApi'
import { compararOrdem } from '../../lista/listaOrdem'
import {
  excluirTarefa,
  listarTarefas,
  restaurarTarefa,
  reordenarTarefas,
  type PageTarefa,
  type TarefaResponse,
} from '../api/tarefaApi'
import { chipsAtivos, filtrosLimpos } from '../filtrosAtivos'
import { FiltrosBarra } from '../FiltrosBarra'
import { InlineAddTarefa, type InlineAddTarefaHandle } from '../InlineAddTarefa'
import { agruparPorPrazo } from '../listaGrupos'
import {
  escreverFiltrosNaUrl,
  lerFiltrosDaUrl,
  paramsApiDaVisao,
  type FiltrosListaUrl,
} from '../listaUrl'
import { confirmacaoCascataPai } from '../cascataConfirmacao'
import {
  ehVisaoArvore,
  ehVisaoPlana,
  filhosNaPagina,
  tarefasNoNivelDaLista,
} from '../listaArvore'
import { TarefaCard } from '../TarefaCard'
import { TarefaListaArrastavel } from '../TarefaListaArrastavel'
import type { TarefasOutletContext } from '../TarefaDrawer'

function paraInstantOuUndefined(local: string): string | undefined {
  if (!local) {
    return undefined
  }
  const date = new Date(local)
  if (Number.isNaN(date.getTime())) {
    return undefined
  }
  return date.toISOString()
}

function paraNumeroOuUndefined(valor: string): number | undefined {
  if (!valor) {
    return undefined
  }
  const n = Number(valor)
  return Number.isFinite(n) ? n : undefined
}

function paramsApi(filtros: FiltrosListaUrl) {
  return {
    ...paramsApiDaVisao(filtros.visao),
    prioridade: filtros.prioridade.length ? filtros.prioridade : undefined,
    prazoDe: filtros.semPrazo ? undefined : paraInstantOuUndefined(filtros.prazoDe),
    prazoAte: filtros.semPrazo ? undefined : paraInstantOuUndefined(filtros.prazoAte),
    duracaoDe: paraNumeroOuUndefined(filtros.duracaoDe),
    duracaoAte: paraNumeroOuUndefined(filtros.duracaoAte),
    projetoId: filtros.semProjeto ? undefined : filtros.projetoId || undefined,
    tagIds: filtros.semTag ? undefined : filtros.tagIds.length ? filtros.tagIds : undefined,
    semPrazo: filtros.semPrazo || undefined,
    semProjeto: filtros.semProjeto || undefined,
    semTag: filtros.semTag || undefined,
    page: filtros.page,
    size: filtros.size,
    sort: filtros.sort,
    direction: filtros.direction,
    ...(ehVisaoArvore(filtros) ? { somenteRaizes: true } : {}),
  }
}

function ehFiltroBasicoTodas(filtros: FiltrosListaUrl): boolean {
  return (
    filtros.visao === 'todas' &&
    filtros.prioridade.length === 0 &&
    !filtros.prazoDe &&
    !filtros.prazoAte &&
    !filtros.duracaoDe &&
    !filtros.duracaoAte &&
    !filtros.projetoId &&
    filtros.tagIds.length === 0 &&
    !filtros.semPrazo &&
    !filtros.semProjeto &&
    !filtros.semTag &&
    filtros.sort === 'prazo' &&
    filtros.direction === 'asc'
  )
}

function removerDaPagina(pagina: PageTarefa, id: string): PageTarefa {
  return {
    ...pagina,
    content: pagina.content.filter((t) => t.id !== id),
    totalElements: Math.max(0, pagina.totalElements - 1),
  }
}

function inserirNaPagina(pagina: PageTarefa, tarefa: TarefaResponse, indice: number): PageTarefa {
  if (pagina.content.some((t) => t.id === tarefa.id)) {
    return pagina
  }
  const content = [...pagina.content]
  content.splice(Math.min(indice, content.length), 0, tarefa)
  return { ...pagina, content, totalElements: pagina.totalElements + 1 }
}

function inserirNosFilhos(
  filhosPorPai: Record<string, TarefaResponse[]>,
  paiId: string,
  tarefa: TarefaResponse,
  indice: number,
): Record<string, TarefaResponse[]> {
  const lista = [...(filhosPorPai[paiId] ?? [])]
  if (lista.some((item) => item.id === tarefa.id)) {
    return filhosPorPai
  }
  lista.splice(Math.min(indice, lista.length), 0, tarefa)
  return { ...filhosPorPai, [paiId]: lista }
}

function substituirNaPagina(pagina: PageTarefa, tarefa: TarefaResponse): PageTarefa {
  return { ...pagina, content: pagina.content.map((t) => (t.id === tarefa.id ? tarefa : t)) }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function TarefasPage() {
  const toast = useToast()
  const location = useLocation()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const filtrosUrl = useMemo(() => lerFiltrosDaUrl(searchParams), [searchParams])
  const { assinarCriada, registrarFocoInline } = useQuickAdd()

  const chaveAtual = useMemo(() => escreverFiltrosNaUrl(filtrosUrl).toString(), [filtrosUrl])

  const [resultado, setResultado] = useState<PageTarefa | null>(null)
  const [projetosAtivos, setProjetosAtivos] = useState<ProjetoResponse[]>([])
  const [catalogoTags, setCatalogoTags] = useState<TagResponse[]>([])
  /** Filtros (serializados) da última leitura concluída; `null` antes da primeira. */
  const [chaveCarregada, setChaveCarregada] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const filtrosRef = useRef(filtrosUrl)
  const listaRef = useRef<HTMLDivElement>(null)
  const inlineAddRef = useRef<InlineAddTarefaHandle>(null)
  const [expandidoIds, setExpandidoIds] = useState<Set<string>>(() => new Set())
  const [filhosPorPai, setFilhosPorPai] = useState<Record<string, TarefaResponse[]>>({})

  useEffect(() => {
    filtrosRef.current = filtrosUrl
  }, [filtrosUrl])

  useEffect(() => {
    let ativo = true
    async function carregarProjetos() {
      try {
        const page = await listarProjetos({ status: 'ATIVO', page: 0, size: 100 })
        if (ativo) {
          setProjetosAtivos(page.content)
        }
      } catch {
        if (ativo) {
          setProjetosAtivos([])
        }
      }
    }
    void carregarProjetos()
    async function carregarTags() {
      try {
        const lista = await listarTags()
        if (ativo) {
          setCatalogoTags(lista)
        }
      } catch {
        if (ativo) {
          setCatalogoTags([])
        }
      }
    }
    void carregarTags()
    return () => {
      ativo = false
    }
  }, [])

  /** Só a resposta da última leitura é aplicada: a anterior é abortada. */
  const carregar = useCallback(async (filtros: FiltrosListaUrl, silencioso: boolean) => {
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    const chave = escreverFiltrosNaUrl(filtros).toString()
    try {
      const data = await listarTarefas(paramsApi(filtros), controller.signal)
      if (controller.signal.aborted) {
        return
      }
      setResultado(data)
      setErro(null)
      setChaveCarregada(chave)
    } catch {
      if (controller.signal.aborted || silencioso) {
        return
      }
      setErro('Não foi possível carregar as tarefas.')
      setResultado(null)
      setChaveCarregada(chave)
    }
  }, [])

  const lerSilenciosamente = useCallback(() => {
    void carregar(filtrosRef.current, true)
  }, [carregar])

  useEffect(() => {
    void carregar(filtrosUrl, false)
  }, [filtrosUrl, carregar])

  useEffect(() => {
    setExpandidoIds(new Set())
    setFilhosPorPai({})
  }, [chaveAtual])

  useEffect(() => () => controllerRef.current?.abort(), [])

  useEffect(() => assinarCriada(lerSilenciosamente), [assinarCriada, lerSilenciosamente])

  useEffect(() => {
    if (location.pathname !== '/tarefas') {
      return
    }
    return registrarFocoInline(() => {
      inlineAddRef.current?.focus()
    })
  }, [location.pathname, registrarFocoInline])

  function atualizarUrl(proximo: FiltrosListaUrl) {
    setSearchParams(escreverFiltrosNaUrl(proximo), { replace: true })
  }

  function irParaPagina(page: number) {
    atualizarUrl({ ...filtrosUrl, page })
    listaRef.current?.focus()
  }

  function restaurarOtimista(tarefa: TarefaResponse, indice: number) {
    const restaurada = { ...tarefa, deletada: false, deletedAt: null }
    setResultado((r) => (r ? inserirNaPagina(r, restaurada, indice) : r))
    restaurarTarefa(tarefa.id).catch((error) => {
      setResultado((r) => (r ? removerDaPagina(r, tarefa.id) : r))
      toast.error(mensagemErro(error, 'Não foi possível restaurar a tarefa.'))
    })
  }

  async function handleExcluir(tarefa: TarefaResponse) {
    const confirmado = await confirm(
      tarefa.temFilhos
        ? confirmacaoCascataPai('excluir')
        : {
            title: 'Excluir tarefa?',
            message: 'A tarefa será excluída. Você poderá restaurá-la depois.',
            confirmLabel: 'Excluir',
          },
    )
    if (!confirmado || !resultado) {
      return
    }

    const indice = resultado.content.findIndex((t) => t.id === tarefa.id)
    const paiId = tarefa.parentId ?? null
    const indiceFilho = paiId
      ? (filhosPorPai[paiId]?.findIndex((item) => item.id === tarefa.id) ?? -1)
      : -1
    const aninhado = indiceFilho >= 0 && paiId != null

    if (aninhado && paiId) {
      setFilhosPorPai((atual) => ({
        ...atual,
        [paiId]: (atual[paiId] ?? []).filter((item) => item.id !== tarefa.id),
      }))
    } else {
      setResultado((r) => (r ? removerDaPagina(r, tarefa.id) : r))
    }
    try {
      await excluirTarefa(tarefa.id)
      toast.success('Tarefa excluída', {
        action: {
          label: 'Desfazer',
          onClick: () => {
            if (aninhado && paiId) {
              setFilhosPorPai((atual) => inserirNosFilhos(atual, paiId, tarefa, indiceFilho))
              restaurarTarefa(tarefa.id).catch((error) => {
                setFilhosPorPai((atual) => ({
                  ...atual,
                  [paiId]: (atual[paiId] ?? []).filter((item) => item.id !== tarefa.id),
                }))
                toast.error(mensagemErro(error, 'Não foi possível restaurar a tarefa.'))
              })
              return
            }
            restaurarOtimista(tarefa, indice)
          },
        },
      })
    } catch (error) {
      if (aninhado && paiId) {
        setFilhosPorPai((atual) => inserirNosFilhos(atual, paiId, tarefa, indiceFilho))
      } else {
        setResultado((r) => (r ? inserirNaPagina(r, tarefa, indice) : r))
      }
      toast.error(mensagemErro(error, 'Não foi possível excluir a tarefa.'))
    }
  }

  async function handleToggleExpand(pai: TarefaResponse) {
    const id = pai.id
    const jaExpandido = expandidoIds.has(id)
    setExpandidoIds((atual) => {
      const proximo = new Set(atual)
      if (jaExpandido) {
        proximo.delete(id)
      } else {
        proximo.add(id)
      }
      return proximo
    })
    if (jaExpandido || !ehVisaoArvore(filtrosUrl) || filhosPorPai[id]) {
      return
    }
    try {
      const page = await listarTarefas({ parentId: id, size: 100, sort: 'ordem' })
      setFilhosPorPai((atual) => ({ ...atual, [id]: page.content }))
    } catch (error) {
      setExpandidoIds((atual) => {
        const proximo = new Set(atual)
        proximo.delete(id)
        return proximo
      })
      toast.error(mensagemErro(error, 'Não foi possível carregar as subtarefas.'))
    }
  }

  async function handleRestaurar(tarefa: TarefaResponse) {
    setResultado((r) =>
      r ? substituirNaPagina(r, { ...tarefa, deletada: false, deletedAt: null }) : r,
    )
    try {
      await restaurarTarefa(tarefa.id)
      toast.success('Tarefa restaurada')
    } catch (error) {
      setResultado((r) => (r ? substituirNaPagina(r, tarefa) : r))
      toast.error(mensagemErro(error, 'Não foi possível restaurar a tarefa.'))
    }
  }

  function handleTarefaCriada(tarefa: TarefaResponse) {
    setResultado((r) => (r ? inserirNaPagina(r, tarefa, 0) : r))
    lerSilenciosamente()
  }

  async function handleReordenar(ids: string[]) {
    try {
      await reordenarTarefas(ids)
    } catch (error) {
      toast.error(mensagemErro(error, 'Não foi possível reordenar as tarefas.'))
      lerSilenciosamente()
      throw error
    }
  }

  const contextoDrawer: TarefasOutletContext = {
    projetosAtivos,
    aoAtualizar: (tarefa) => {
      setResultado((r) => (r ? substituirNaPagina(r, tarefa) : r))
      lerSilenciosamente()
    },
    aoCriar: (tarefa) => handleTarefaCriada(tarefa),
  }

  const naFicha = location.pathname !== '/tarefas'

  const carregandoPrimeira = chaveCarregada === null
  const recarregando = resultado != null && chaveCarregada !== chaveAtual
  const totalPages = resultado?.totalPages ?? 0
  const visaoArvore = ehVisaoArvore(filtrosUrl)
  const visaoPlana = ehVisaoPlana(filtrosUrl)
  const tarefasTopo =
    resultado == null
      ? []
      : visaoArvore || visaoPlana
        ? resultado.content
        : tarefasNoNivelDaLista(resultado.content)
  const podeArrastar =
    filtrosUrl.sort === 'prazo' &&
    filtrosUrl.visao !== 'excluidas' &&
    filtrosUrl.tagIds.length < 2
  const grupos = agruparPorPrazo(
    podeArrastar ? [...tarefasTopo].sort(compararOrdem) : tarefasTopo,
    new Date(),
    false,
    filtrosUrl.tagIds,
  )
  const listaVazia = resultado != null && resultado.content.length === 0
  const emptyPrimeira = listaVazia && ehFiltroBasicoTodas(filtrosUrl)
  const temChips = chipsAtivos(filtrosUrl, projetosAtivos, catalogoTags).length > 0
  const total = resultado?.totalElements ?? 0

  return (
    <main className={naFicha ? 'tarefas-page tarefas-page--ficha' : 'tarefas-page'}>
      {confirmDialog}
      {naFicha ? (
        <Outlet key={location.pathname} context={contextoDrawer} />
      ) : (
        <>
      <PageHeader
        title="Tarefas"
        description="Liste, filtre e ordene o que precisa ser planejado."
        actions={
          resultado != null && resultado.content.length > 0 ? (
            <Link to="/plano" className="btn btn--secondary btn--sm">
              Organizar meu dia
            </Link>
          ) : null
        }
      />

      <FiltrosBarra
        filtros={filtrosUrl}
        projetosAtivos={projetosAtivos}
        catalogoTags={catalogoTags}
        onAplicar={atualizarUrl}
      />

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregandoPrimeira ? <Skeleton variant="tarefas-lista" /> : null}

      <InlineAddTarefa
        ref={inlineAddRef}
        projetoId={filtrosUrl.projetoId || undefined}
        onCriada={handleTarefaCriada}
      />

      {resultado ? (
        <div
          ref={listaRef}
          className={recarregando ? 'tarefas-lista tarefas-lista--recarregando' : 'tarefas-lista'}
          aria-busy={recarregando}
          aria-label="Lista de tarefas"
          role="region"
          tabIndex={-1}
        >
          {listaVazia ? (
            <div className="tarefas-empty" role="status">
              {emptyPrimeira ? (
                <>
                  <CheckSquareIcon className="tarefas-empty__icone" aria-hidden />
                  <p className="tarefas-empty__titulo">Nenhuma tarefa ainda</p>
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => inlineAddRef.current?.focus()}
                  >
                    <PlusIcon aria-hidden />
                    Nova tarefa
                  </button>
                </>
              ) : (
                <>
                  <p className="tarefas-empty__titulo">Nada com estes filtros</p>
                  {temChips ? (
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => atualizarUrl(filtrosLimpos(filtrosUrl))}
                    >
                      Limpar filtros
                    </button>
                  ) : null}
                </>
              )}
            </div>
          ) : (
            <div className="tarefas-grupos">
              {grupos.map((grupo) => (
                <section
                  key={grupo.id}
                  className="tarefa-grupo"
                  aria-labelledby={`grupo-${grupo.id}`}
                >
                  <h2 id={`grupo-${grupo.id}`} className="tarefa-grupo__titulo">
                    {grupo.label}{' '}
                    <span className="tarefa-grupo__contador">({grupo.tarefas.length})</span>
                  </h2>
                  <TarefaListaArrastavel
                    tarefas={grupo.tarefas}
                    onReordenar={(ids) => handleReordenar(ids)}
                    disabled={!podeArrastar}
                    className="tarefa-cards"
                    renderItem={(tarefa, handle) => {
                      const filhos = visaoArvore
                        ? (filhosPorPai[tarefa.id] ?? [])
                        : visaoPlana || resultado == null
                          ? []
                          : filhosNaPagina(resultado.content, tarefa.id)
                      const podeExpandir =
                        !visaoPlana &&
                        (visaoArvore ? Boolean(tarefa.temFilhos) : filhos.length > 0)
                      const expandido = expandidoIds.has(tarefa.id)
                      const indicadorPai =
                        Boolean(tarefa.parentId) && !visaoPlana && !visaoArvore
                      return (
                        <>
                          <TarefaCard
                            tarefa={tarefa}
                            visao={filtrosUrl.visao}
                            search={location.search}
                            onExcluir={(t) => void handleExcluir(t)}
                            onRestaurar={(t) => void handleRestaurar(t)}
                            onSessaoAlterada={lerSilenciosamente}
                            expandido={expandido}
                            onToggleExpand={
                              podeExpandir ? () => void handleToggleExpand(tarefa) : undefined
                            }
                            indicadorPai={indicadorPai}
                            arrasteHandle={handle}
                          />
                          {expandido && filhos.length > 0 ? (
                            <TarefaListaArrastavel
                              tarefas={filhos}
                              onReordenar={(ids) => handleReordenar(ids)}
                              disabled={!podeArrastar}
                              className="tarefa-cards tarefa-cards--aninhados"
                              ariaLabel={`Subtarefas de ${tarefa.titulo}`}
                              renderItem={(filho, filhoHandle) => (
                                <TarefaCard
                                  tarefa={filho}
                                  visao={filtrosUrl.visao}
                                  search={location.search}
                                  onExcluir={(t) => void handleExcluir(t)}
                                  onRestaurar={(t) => void handleRestaurar(t)}
                                  onSessaoAlterada={lerSilenciosamente}
                                  arrasteHandle={filhoHandle}
                                />
                              )}
                            />
                          ) : null}
                        </>
                      )
                    }}
                  />
                </section>
              ))}
            </div>
          )}

          {totalPages > 0 ? (
            <nav className="tarefas-paginacao" aria-label="Paginação">
              <button
                type="button"
                className="btn btn--secondary"
                disabled={filtrosUrl.page <= 0}
                onClick={() => irParaPagina(Math.max(0, filtrosUrl.page - 1))}
              >
                <CaretLeftIcon aria-hidden />
                Anterior
              </button>
              <span className="tarefas-paginacao__info">
                Página {filtrosUrl.page + 1} de {totalPages} · {total} tarefa
                {total === 1 ? '' : 's'}
              </span>
              <button
                type="button"
                className="btn btn--secondary"
                disabled={filtrosUrl.page >= totalPages - 1}
                onClick={() => irParaPagina(filtrosUrl.page + 1)}
              >
                Próxima
                <CaretRightIcon aria-hidden />
              </button>
            </nav>
          ) : null}
        </div>
      ) : null}

      <Outlet key={location.pathname} context={contextoDrawer} />
        </>
      )}
    </main>
  )
}
