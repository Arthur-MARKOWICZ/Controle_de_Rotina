import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { PRIORIDADE_LABEL, STATUS_TAREFA_LABEL } from '../../home/labels'
import {
  excluirTarefa,
  listarTarefas,
  restaurarTarefa,
  type PageTarefa,
  type SortCampo,
  type SortDirection,
} from '../api/tarefaApi'
import { formatarDataHora } from '../datetime'
import { agruparPorPrazo, formatarPrazoLista } from '../listaGrupos'
import {
  escreverFiltrosNaUrl,
  FILTROS_LISTA_INICIAIS,
  lerFiltrosDaUrl,
  paramsApiDaVisao,
  type FiltrosListaUrl,
  type VisaoLista,
} from '../listaUrl'
import { QuickAddTarefaDialog } from '../QuickAddTarefaDialog'
import type { Prioridade } from '../validation/tarefaForm'

const PRIORIDADE_OPCOES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']

const VISOES: { id: VisaoLista; label: string }[] = [
  { id: 'todas', label: 'Todas' },
  { id: 'pendentes', label: 'Pendentes' },
  { id: 'em_progresso', label: 'Em progresso' },
  { id: 'finalizadas', label: 'Finalizadas' },
  { id: 'excluidas', label: 'Excluídas' },
]

function toggleValor<T extends string>(lista: T[], valor: T): T[] {
  return lista.includes(valor) ? lista.filter((item) => item !== valor) : [...lista, valor]
}

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

function ehFiltroBasicoTodas(filtros: FiltrosListaUrl): boolean {
  return (
    filtros.visao === 'todas' &&
    filtros.prioridade.length === 0 &&
    !filtros.prazoDe &&
    !filtros.prazoAte &&
    !filtros.duracaoDe &&
    !filtros.duracaoAte &&
    filtros.sort === 'prazo' &&
    filtros.direction === 'asc'
  )
}

export function TarefasPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const filtrosUrl = useMemo(() => lerFiltrosDaUrl(searchParams), [searchParams])

  const [filtrosAvancados, setFiltrosAvancados] = useState(() => ({
    prioridade: filtrosUrl.prioridade,
    prazoDe: filtrosUrl.prazoDe,
    prazoAte: filtrosUrl.prazoAte,
    duracaoDe: filtrosUrl.duracaoDe,
    duracaoAte: filtrosUrl.duracaoAte,
    sort: filtrosUrl.sort,
    direction: filtrosUrl.direction,
    size: filtrosUrl.size,
  }))
  const [resultado, setResultado] = useState<PageTarefa | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoId, setAcaoId] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)
  const [mostrarFiltros, setMostrarFiltros] = useState(false)
  const [quickAddAberto, setQuickAddAberto] = useState(false)

  useEffect(() => {
    setFiltrosAvancados({
      prioridade: filtrosUrl.prioridade,
      prazoDe: filtrosUrl.prazoDe,
      prazoAte: filtrosUrl.prazoAte,
      duracaoDe: filtrosUrl.duracaoDe,
      duracaoAte: filtrosUrl.duracaoAte,
      sort: filtrosUrl.sort,
      direction: filtrosUrl.direction,
      size: filtrosUrl.size,
    })
  }, [filtrosUrl])

  function atualizarUrl(proximo: FiltrosListaUrl) {
    const params = escreverFiltrosNaUrl(proximo)
    setSearchParams(params, { replace: true })
  }

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarTarefas({
          ...paramsApiDaVisao(filtrosUrl.visao),
          prioridade: filtrosUrl.prioridade.length ? filtrosUrl.prioridade : undefined,
          prazoDe: paraInstantOuUndefined(filtrosUrl.prazoDe),
          prazoAte: paraInstantOuUndefined(filtrosUrl.prazoAte),
          duracaoDe: paraNumeroOuUndefined(filtrosUrl.duracaoDe),
          duracaoAte: paraNumeroOuUndefined(filtrosUrl.duracaoAte),
          page: filtrosUrl.page,
          size: filtrosUrl.size,
          sort: filtrosUrl.sort,
          direction: filtrosUrl.direction,
        })
        if (ativo) {
          setResultado(data)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar as tarefas.')
          setResultado(null)
        }
      } finally {
        if (ativo) {
          setCarregando(false)
        }
      }
    }

    void carregar()
    return () => {
      ativo = false
    }
  }, [filtrosUrl, recarregarEm])

  function aplicarVisao(visao: VisaoLista) {
    atualizarUrl({ ...filtrosUrl, visao, page: 0 })
  }

  function handleAplicarFiltros(event: FormEvent) {
    event.preventDefault()
    atualizarUrl({
      ...filtrosUrl,
      ...filtrosAvancados,
      page: 0,
    })
  }

  function handleLimparFiltros() {
    atualizarUrl({
      ...FILTROS_LISTA_INICIAIS,
      visao: filtrosUrl.visao,
    })
  }

  async function handleExcluir(id: string) {
    const confirmado = await confirm({
      title: 'Excluir tarefa?',
      message: 'A tarefa será excluída. Você poderá restaurá-la depois.',
      confirmLabel: 'Excluir',
    })
    if (!confirmado) {
      return
    }

    setErro(null)
    setAcaoId(id)
    try {
      await excluirTarefa(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Tarefa excluída', {
        action: {
          label: 'Desfazer',
          onClick: () => {
            void restaurarTarefa(id)
              .then(() => setRecarregarEm((n) => n + 1))
              .catch((error) => {
                setErro(
                  error instanceof ApiError
                    ? error.message
                    : 'Não foi possível restaurar a tarefa.',
                )
              })
          },
        },
      })
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível excluir a tarefa.',
      )
    } finally {
      setAcaoId(null)
    }
  }

  async function handleRestaurar(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await restaurarTarefa(id)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível restaurar a tarefa.',
      )
    } finally {
      setAcaoId(null)
    }
  }

  const totalPages = resultado?.totalPages ?? 0
  const grupos = resultado ? agruparPorPrazo(resultado.content) : []
  const listaVazia = !carregando && resultado != null && resultado.content.length === 0
  const emptyPrimeira = listaVazia && ehFiltroBasicoTodas(filtrosUrl)

  return (
    <main className="tarefas-page">
      {confirmDialog}
      <QuickAddTarefaDialog
        open={quickAddAberto}
        onClose={() => setQuickAddAberto(false)}
        onCriada={() => setRecarregarEm((n) => n + 1)}
      />
      <header className="tarefas-header">
        <div>
          <h1>Tarefas</h1>
          <p className="tarefas-subtitle">Liste, filtre e ordene o que precisa ser planejado.</p>
        </div>
        <div className="tarefas-header-actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => setQuickAddAberto(true)}
          >
            Nova tarefa
          </button>
        </div>
      </header>

      <div className="visao-chips" role="group" aria-label="Visão da lista">
        {VISOES.map((visao) => (
          <button
            key={visao.id}
            type="button"
            className={
              filtrosUrl.visao === visao.id ? 'visao-chip visao-chip--ativo' : 'visao-chip'
            }
            aria-pressed={filtrosUrl.visao === visao.id}
            onClick={() => aplicarVisao(visao.id)}
          >
            {visao.label}
          </button>
        ))}
      </div>

      <form className="tarefas-filtros card" onSubmit={handleAplicarFiltros}>
        <div className="filtros-actions">
          <button
            type="button"
            className="btn btn--secondary"
            aria-expanded={mostrarFiltros}
            onClick={() => setMostrarFiltros((atual) => !atual)}
          >
            {mostrarFiltros ? 'Ocultar filtros' : 'Filtros'}
          </button>
          {mostrarFiltros ? (
            <>
              <button type="submit" className="btn btn--primary">
                Aplicar filtros
              </button>
              <button type="button" className="btn btn--secondary" onClick={handleLimparFiltros}>
                Limpar
              </button>
            </>
          ) : null}
        </div>

        {mostrarFiltros ? (
          <div className="filtros-grid filtros-avancados">
            <fieldset className="filtros-fieldset">
              <legend>Prioridade</legend>
              <div className="chip-group">
                {PRIORIDADE_OPCOES.map((prioridade) => (
                  <label key={prioridade} className="chip">
                    <input
                      type="checkbox"
                      checked={filtrosAvancados.prioridade.includes(prioridade)}
                      onChange={() =>
                        setFiltrosAvancados((atual) => ({
                          ...atual,
                          prioridade: toggleValor(atual.prioridade, prioridade),
                        }))
                      }
                    />
                    {PRIORIDADE_LABEL[prioridade]}
                  </label>
                ))}
              </div>
            </fieldset>
            <label className="field">
              Prazo de
              <input
                type="datetime-local"
                value={filtrosAvancados.prazoDe}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({ ...a, prazoDe: e.target.value }))
                }
              />
            </label>
            <label className="field">
              Prazo até
              <input
                type="datetime-local"
                value={filtrosAvancados.prazoAte}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({ ...a, prazoAte: e.target.value }))
                }
              />
            </label>
            <label className="field">
              Duração de (min)
              <input
                type="number"
                min={1}
                value={filtrosAvancados.duracaoDe}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({ ...a, duracaoDe: e.target.value }))
                }
              />
            </label>
            <label className="field">
              Duração até (min)
              <input
                type="number"
                min={1}
                value={filtrosAvancados.duracaoAte}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({ ...a, duracaoAte: e.target.value }))
                }
              />
            </label>
            <label className="field">
              Ordenar por
              <select
                value={filtrosAvancados.sort}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({
                    ...a,
                    sort: e.target.value as SortCampo,
                  }))
                }
              >
                <option value="prazo">Prazo</option>
                <option value="duracao">Duração</option>
                <option value="prioridade">Prioridade</option>
              </select>
            </label>
            <label className="field">
              Direção
              <select
                value={filtrosAvancados.direction}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({
                    ...a,
                    direction: e.target.value as SortDirection,
                  }))
                }
              >
                <option value="asc">Ascendente</option>
                <option value="desc">Descendente</option>
              </select>
            </label>
            <label className="field">
              Por página
              <select
                value={filtrosAvancados.size}
                onChange={(e) =>
                  setFiltrosAvancados((a) => ({ ...a, size: Number(e.target.value) }))
                }
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
            </label>
          </div>
        ) : null}
      </form>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregando ? <Skeleton variant="tarefas-lista" /> : null}

      {!carregando && resultado ? (
        <>
          <p className="tarefas-meta">
            {resultado.totalElements} tarefa{resultado.totalElements === 1 ? '' : 's'}
          </p>

          {listaVazia ? (
            <div className="tarefas-empty" role="status">
              {emptyPrimeira ? (
                <>
                  <p>Nenhuma tarefa ainda.</p>
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => setQuickAddAberto(true)}
                  >
                    Criar primeira tarefa
                  </button>
                </>
              ) : (
                <p>Nenhuma tarefa encontrada com estes filtros.</p>
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
                    {grupo.tarefas.map((tarefa) => {
                      const prazoRelativo = formatarPrazoLista(tarefa.prazo)
                      const prazoAbsoluto = formatarDataHora(tarefa.prazo)
                      return (
                        <li
                          key={tarefa.id}
                          className={
                            tarefa.deletada
                              ? 'tarefa-card tarefa-card--deletada'
                              : 'tarefa-card'
                          }
                        >
                          <div className="tarefa-card__principal">
                            <Link to={`/tarefas/${tarefa.id}`} className="tarefa-card__titulo">
                              {tarefa.titulo}
                            </Link>
                            <div className="tarefa-card__meta">
                              <span
                                className={`badge status-${tarefa.status.toLowerCase()}`}
                              >
                                {STATUS_TAREFA_LABEL[tarefa.status]}
                              </span>
                              <span
                                className={`badge priority-${tarefa.prioridade.toLowerCase()}`}
                              >
                                {PRIORIDADE_LABEL[tarefa.prioridade]}
                              </span>
                              {tarefa.deletada ? (
                                <span className="badge status-deletada">Excluída</span>
                              ) : null}
                            </div>
                            <p className="tarefa-card__prazo">
                              <time
                                dateTime={tarefa.prazo ?? undefined}
                                title={prazoAbsoluto}
                              >
                                {prazoRelativo}
                              </time>
                              <span className="tarefa-card__duracao">
                                · {tarefa.duracaoMinutos} min
                              </span>
                            </p>
                          </div>
                          <div className="tarefa-card__acoes">
                            {tarefa.deletada ? (
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                disabled={acaoId === tarefa.id}
                                onClick={() => void handleRestaurar(tarefa.id)}
                              >
                                {acaoId === tarefa.id ? 'Restaurando…' : 'Restaurar'}
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="btn btn--ghost btn--danger btn--sm"
                                disabled={acaoId === tarefa.id}
                                onClick={() => void handleExcluir(tarefa.id)}
                              >
                                {acaoId === tarefa.id ? 'Excluindo…' : 'Excluir'}
                              </button>
                            )}
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                </section>
              ))}
            </div>
          )}

          <div className="tarefas-paginacao">
            <button
              type="button"
              className="btn btn--primary"
              disabled={filtrosUrl.page <= 0}
              onClick={() =>
                atualizarUrl({ ...filtrosUrl, page: Math.max(0, filtrosUrl.page - 1) })
              }
            >
              Anterior
            </button>
            <span>
              Página {totalPages === 0 ? 0 : filtrosUrl.page + 1} de {totalPages}
            </span>
            <button
              type="button"
              className="btn btn--primary"
              disabled={totalPages === 0 || filtrosUrl.page >= totalPages - 1}
              onClick={() => atualizarUrl({ ...filtrosUrl, page: filtrosUrl.page + 1 })}
            >
              Próxima
            </button>
          </div>
        </>
      ) : null}
    </main>
  )
}
