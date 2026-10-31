import { CaretLeftIcon, CaretRightIcon, CheckSquareIcon, PlusIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { PageHeader } from '../../layout/PageHeader'
import { useQuickAdd } from '../../layout/QuickAddContext'
import { listarProjetos, type ProjetoResponse } from '../../projeto/api/projetoApi'
import {
  excluirTarefa,
  listarTarefas,
  restaurarTarefa,
  type PageTarefa,
  type TarefaResponse,
} from '../api/tarefaApi'
import { chipsAtivos, filtrosLimpos } from '../filtrosAtivos'
import { FiltrosBarra } from '../FiltrosBarra'
import { agruparPorPrazo } from '../listaGrupos'
import {
  escreverFiltrosNaUrl,
  lerFiltrosDaUrl,
  paramsApiDaVisao,
  type FiltrosListaUrl,
} from '../listaUrl'
import { TarefaCard } from '../TarefaCard'
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
    prazoDe: paraInstantOuUndefined(filtros.prazoDe),
    prazoAte: paraInstantOuUndefined(filtros.prazoAte),
    duracaoDe: paraNumeroOuUndefined(filtros.duracaoDe),
    duracaoAte: paraNumeroOuUndefined(filtros.duracaoAte),
    projetoId: filtros.projetoId || undefined,
    page: filtros.page,
    size: filtros.size,
    sort: filtros.sort,
    direction: filtros.direction,
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
  const { abrir: abrirQuickAdd, assinarCriada } = useQuickAdd()

  const chaveAtual = useMemo(() => escreverFiltrosNaUrl(filtrosUrl).toString(), [filtrosUrl])

  const [resultado, setResultado] = useState<PageTarefa | null>(null)
  const [projetosAtivos, setProjetosAtivos] = useState<ProjetoResponse[]>([])
  /** Filtros (serializados) da última leitura concluída; `null` antes da primeira. */
  const [chaveCarregada, setChaveCarregada] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const filtrosRef = useRef(filtrosUrl)
  const listaRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => () => controllerRef.current?.abort(), [])

  useEffect(() => assinarCriada(lerSilenciosamente), [assinarCriada, lerSilenciosamente])

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
    const confirmado = await confirm({
      title: 'Excluir tarefa?',
      message: 'A tarefa será excluída. Você poderá restaurá-la depois.',
      confirmLabel: 'Excluir',
    })
    if (!confirmado || !resultado) {
      return
    }

    const indice = resultado.content.findIndex((t) => t.id === tarefa.id)
    setResultado((r) => (r ? removerDaPagina(r, tarefa.id) : r))
    try {
      await excluirTarefa(tarefa.id)
      toast.success('Tarefa excluída', {
        action: { label: 'Desfazer', onClick: () => restaurarOtimista(tarefa, indice) },
      })
    } catch (error) {
      setResultado((r) => (r ? inserirNaPagina(r, tarefa, indice) : r))
      toast.error(mensagemErro(error, 'Não foi possível excluir a tarefa.'))
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

  const contextoDrawer: TarefasOutletContext = {
    projetosAtivos,
    aoAtualizar: (tarefa) => {
      setResultado((r) => (r ? substituirNaPagina(r, tarefa) : r))
      lerSilenciosamente()
    },
    aoCriar: () => lerSilenciosamente(),
  }

  const carregandoPrimeira = chaveCarregada === null
  const recarregando = resultado != null && chaveCarregada !== chaveAtual
  const totalPages = resultado?.totalPages ?? 0
  const grupos = resultado ? agruparPorPrazo(resultado.content) : []
  const listaVazia = resultado != null && resultado.content.length === 0
  const emptyPrimeira = listaVazia && ehFiltroBasicoTodas(filtrosUrl)
  const temChips = chipsAtivos(filtrosUrl, projetosAtivos).length > 0
  const total = resultado?.totalElements ?? 0

  return (
    <main className="tarefas-page">
      {confirmDialog}
      <PageHeader
        title="Tarefas"
        description="Liste, filtre e ordene o que precisa ser planejado."
      />

      <FiltrosBarra filtros={filtrosUrl} projetosAtivos={projetosAtivos} onAplicar={atualizarUrl} />

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregandoPrimeira ? <Skeleton variant="tarefas-lista" /> : null}

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
                  <button type="button" className="btn btn--primary" onClick={abrirQuickAdd}>
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
                  <ul className="tarefa-cards">
                    {grupo.tarefas.map((tarefa) => (
                      <TarefaCard
                        key={tarefa.id}
                        tarefa={tarefa}
                        visao={filtrosUrl.visao}
                        search={location.search}
                        onExcluir={(t) => void handleExcluir(t)}
                        onRestaurar={(t) => void handleRestaurar(t)}
                      />
                    ))}
                  </ul>
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
    </main>
  )
}
