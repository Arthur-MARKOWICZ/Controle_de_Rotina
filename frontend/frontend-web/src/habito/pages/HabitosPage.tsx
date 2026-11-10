import { CheckIcon, PencilSimpleIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { formatarCapacidadeMinutos } from '../../planner/data'
import {
  excluirHabito,
  listarHabitos,
  pausarHabito,
  reativarHabito,
  restaurarHabito,
  type HabitoResponse,
  type PageHabito,
  type StatusHabito,
} from '../api/habitoApi'
import type { HabitoDrawerState, HabitosOutletContext } from '../HabitoDrawer'
import { TagChipList } from '../../tag/TagChip'
import { TagFiltroPopover } from '../../tag/TagFiltroPopover'
import { lerTagIdsDaUrl, escreverTagIdsNaUrl } from '../../tag/listaTagUrl'
import { ordenarPorTagMatch } from '../../tag/tagMatch'
import { rotuloDiasSemana } from '../validation/habitoForm'

const DRAWER_STATE: HabitoDrawerState = { daLista: true }

type Visao = 'ATIVO' | 'PAUSADO' | 'EXCLUIDOS'

const STATUS_LABEL: Record<StatusHabito, string> = {
  ATIVO: 'Ativo',
  PAUSADO: 'Pausado',
}

const VISOES: { id: Visao; label: string }[] = [
  { id: 'ATIVO', label: 'Ativos' },
  { id: 'PAUSADO', label: 'Pausados' },
  { id: 'EXCLUIDOS', label: 'Excluídos' },
]

function lerVisaoDaUrl(params: URLSearchParams): Visao {
  const status = params.get('status')
  if (params.get('somenteDeletadas') === 'true' || status === 'EXCLUIDOS') {
    return 'EXCLUIDOS'
  }
  return status === 'PAUSADO' ? 'PAUSADO' : 'ATIVO'
}

function lerPageDaUrl(params: URLSearchParams): number {
  return Math.max(0, Number(params.get('page') ?? 0) || 0)
}

export function HabitosPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const visao = lerVisaoDaUrl(searchParams)
  const page = lerPageDaUrl(searchParams)
  const tagIds = useMemo(() => lerTagIdsDaUrl(searchParams), [searchParams])

  const [resultado, setResultado] = useState<PageHabito | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoId, setAcaoId] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)

  function escreverUrl(proximo: { visao: Visao; page: number; tagIds: string[] }) {
    let params = new URLSearchParams()
    if (proximo.visao === 'PAUSADO') {
      params.set('status', 'PAUSADO')
    } else if (proximo.visao === 'EXCLUIDOS') {
      params.set('somenteDeletadas', 'true')
    }
    if (proximo.page > 0) {
      params.set('page', String(proximo.page))
    }
    params = escreverTagIdsNaUrl(params, proximo.tagIds)
    setSearchParams(params, { replace: true })
  }

  function aplicarVisao(proximo: Visao) {
    escreverUrl({ visao: proximo, page: 0, tagIds })
  }

  function irParaPagina(proximo: number) {
    escreverUrl({ visao, page: Math.max(0, proximo), tagIds })
  }

  function aplicarFiltroTags(proximos: string[]) {
    escreverUrl({ visao, page: 0, tagIds: proximos })
  }

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarHabitos(
          visao === 'EXCLUIDOS'
            ? { somenteDeletadas: true, page, size: 20, tagIds: tagIds.length ? tagIds : undefined }
            : {
                status: visao,
                page,
                size: 20,
                tagIds: tagIds.length ? tagIds : undefined,
              },
        )
        if (ativo) {
          setResultado(data)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar os hábitos.')
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
  }, [visao, page, tagIds, recarregarEm])

  const aoCriar = useCallback((_habito: HabitoResponse) => {
    setRecarregarEm((n) => n + 1)
  }, [])

  const aoAtualizar = useCallback((habito: HabitoResponse) => {
    setResultado((atual) => {
      if (!atual) {
        return atual
      }
      return {
        ...atual,
        content: atual.content.map((item) => (item.id === habito.id ? habito : item)),
      }
    })
    setRecarregarEm((n) => n + 1)
  }, [])

  const outletContext: HabitosOutletContext = { aoCriar, aoAtualizar }

  async function handlePausar(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await pausarHabito(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Hábito pausado')
    } catch (error) {
      setErro(error instanceof ApiError ? error.message : 'Não foi possível pausar o hábito.')
    } finally {
      setAcaoId(null)
    }
  }

  async function handleReativar(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await reativarHabito(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Hábito reativado')
    } catch (error) {
      setErro(error instanceof ApiError ? error.message : 'Não foi possível reativar o hábito.')
    } finally {
      setAcaoId(null)
    }
  }

  async function handleExcluir(id: string, titulo: string) {
    const confirmado = await confirm({
      title: 'Excluir hábito?',
      message: `“${titulo}” deixará de entrar no plano. Você pode restaurá-lo na visão Excluídos.`,
      confirmLabel: 'Excluir',
    })
    if (!confirmado) {
      return
    }
    setErro(null)
    setAcaoId(id)
    try {
      await excluirHabito(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Hábito excluído')
    } catch (error) {
      setErro(error instanceof ApiError ? error.message : 'Não foi possível excluir o hábito.')
    } finally {
      setAcaoId(null)
    }
  }

  async function handleRestaurar(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await restaurarHabito(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Hábito restaurado')
    } catch (error) {
      setErro(error instanceof ApiError ? error.message : 'Não foi possível restaurar o hábito.')
    } finally {
      setAcaoId(null)
    }
  }

  const listaVazia = !carregando && resultado != null && resultado.content.length === 0
  const emptyPrimeira = listaVazia && visao === 'ATIVO'
  const totalPages = resultado?.totalPages ?? 0
  const novoHabito = { pathname: '/habitos/nova', search: location.search }

  return (
    <main className="habitos-page">
      {confirmDialog}
      <header className="habitos-header">
        <div>
          <h1>Hábitos</h1>
          <p className="habitos-subtitle">
            Recorrências do dia a dia que disputam capacidade com as tarefas.
          </p>
        </div>
        <div className="habitos-header-actions">
          <Link to={novoHabito} state={DRAWER_STATE} className="btn btn--primary">
            Novo hábito
          </Link>
        </div>
      </header>

      <div className="visao-chips" role="group" aria-label="Visão da lista">
        {VISOES.map((item) => (
          <button
            key={item.id}
            type="button"
            className={visao === item.id ? 'visao-chip visao-chip--ativo' : 'visao-chip'}
            aria-pressed={visao === item.id}
            onClick={() => aplicarVisao(item.id)}
          >
            {visao === item.id ? <CheckIcon aria-hidden /> : null}
            {item.label}
          </button>
        ))}
      </div>

      <div className="habitos-filtros">
        <TagFiltroPopover tagIds={tagIds} onAplicar={aplicarFiltroTags} />
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregando ? <Skeleton variant="tarefas-lista" /> : null}

      {!carregando && resultado ? (
        <>
          <p className="habitos-meta">
            {resultado.totalElements} hábito{resultado.totalElements === 1 ? '' : 's'}
          </p>

          {listaVazia ? (
            <div className="habitos-empty" role="status">
              {emptyPrimeira ? (
                <>
                  <p>Nenhum hábito ainda.</p>
                  <Link to={novoHabito} state={DRAWER_STATE} className="btn btn--primary">
                    Criar primeiro hábito
                  </Link>
                </>
              ) : visao === 'PAUSADO' ? (
                <>
                  <p>Nenhum hábito pausado.</p>
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => aplicarVisao('ATIVO')}
                  >
                    Ver ativos
                  </button>
                </>
              ) : (
                <>
                  <p>Nenhum hábito excluído.</p>
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => aplicarVisao('ATIVO')}
                  >
                    Ver ativos
                  </button>
                </>
              )}
            </div>
          ) : (
            <ul className="habito-cards">
              {ordenarPorTagMatch(resultado.content, tagIds).map((habito) => {
                const dias = rotuloDiasSemana(habito.diasSemana)
                return (
                  <li
                    key={habito.id}
                    className={
                      habito.status === 'PAUSADO' || habito.deletada
                        ? 'habito-card habito-card--pausado'
                        : 'habito-card'
                    }
                  >
                    <div className="habito-card__principal">
                      <Link to={`/habitos/${habito.id}`} state={DRAWER_STATE} className="habito-card__titulo">
                        {habito.titulo}
                      </Link>
                      <div className="habito-card__meta">
                        <span
                          className={
                            habito.deletada
                              ? 'badge status-deletada'
                              : `badge status-${habito.status.toLowerCase()}`
                          }
                        >
                          {habito.deletada ? 'Excluído' : STATUS_LABEL[habito.status]}
                        </span>
                        {dias ? (
                          <span className="habito-card__dias" aria-label={`Dias: ${dias}`}>
                            {dias}
                          </span>
                        ) : null}
                        <span>{formatarCapacidadeMinutos(habito.duracaoMinutos)}</span>
                        {habito.horarioPreferencial ? (
                          <span aria-label="Horário preferencial">
                            Pref. {habito.horarioPreferencial.slice(0, 5)}
                          </span>
                        ) : null}
                        {!habito.deletada ? (
                          <span className="habito-card__streak" aria-label={`Sequência atual ${habito.streakAtual}`}>
                            Sequência {habito.streakAtual}
                          </span>
                        ) : null}
                        <TagChipList tags={habito.tags} />
                      </div>
                    </div>
                    <div className="habito-card__acoes">
                      <Link
                        to={`/habitos/${habito.id}`}
                        state={DRAWER_STATE}
                        className="btn btn--ghost btn--sm"
                      >
                        <PencilSimpleIcon aria-hidden />
                        {habito.deletada ? 'Ver' : 'Editar'}
                      </Link>
                      {habito.deletada ? (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={acaoId === habito.id}
                          onClick={() => void handleRestaurar(habito.id)}
                        >
                          {acaoId === habito.id ? 'Restaurando…' : 'Restaurar'}
                        </button>
                      ) : habito.status === 'PAUSADO' ? (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={acaoId === habito.id}
                          onClick={() => void handleReativar(habito.id)}
                        >
                          {acaoId === habito.id ? 'Reativando…' : 'Reativar'}
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={acaoId === habito.id}
                          onClick={() => void handlePausar(habito.id)}
                        >
                          {acaoId === habito.id ? 'Pausando…' : 'Pausar'}
                        </button>
                      )}
                      {!habito.deletada ? (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={acaoId === habito.id}
                          onClick={() => void handleExcluir(habito.id, habito.titulo)}
                        >
                          Excluir
                        </button>
                      ) : null}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}

          {totalPages > 1 ? (
            <div className="habitos-paginacao">
              <button
                type="button"
                className="btn btn--primary"
                disabled={page <= 0}
                onClick={() => irParaPagina(page - 1)}
              >
                Anterior
              </button>
              <span>
                Página {page + 1} de {totalPages}
              </span>
              <button
                type="button"
                className="btn btn--primary"
                disabled={page >= totalPages - 1}
                onClick={() => irParaPagina(page + 1)}
              >
                Próxima
              </button>
            </div>
          ) : null}
        </>
      ) : null}

      <Outlet context={outletContext} />
    </main>
  )
}
