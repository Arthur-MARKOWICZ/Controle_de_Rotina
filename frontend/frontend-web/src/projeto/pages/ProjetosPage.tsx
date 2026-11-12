import { CheckIcon, PencilSimpleIcon } from '@phosphor-icons/react'
import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { ArrasteHandle } from '../../lista/ArrasteHandle'
import { compararOrdem } from '../../lista/listaOrdem'
import { useListaArrastavel } from '../../lista/useListaArrastavel'
import {
  arquivarProjeto,
  listarProjetos,
  reativarProjeto,
  reordenarProjetos,
  type PageProjeto,
  type ProjetoResponse,
  type StatusProjeto,
} from '../api/projetoApi'
import { TagChipList } from '../../tag/TagChip'
import { TagFiltroPopover } from '../../tag/TagFiltroPopover'
import { escreverTagIdsNaUrl, lerTagIdsDaUrl } from '../../tag/listaTagUrl'
import { ordenarPorTagMatch } from '../../tag/tagMatch'
import type { ProjetoDrawerState } from '../ProjetoDrawer'

const DRAWER_STATE: ProjetoDrawerState = { daLista: true }

const STATUS_LABEL: Record<StatusProjeto, string> = {
  ATIVO: 'Ativo',
  ARQUIVADO: 'Arquivado',
}

const VISOES: { id: StatusProjeto; label: string }[] = [
  { id: 'ATIVO', label: 'Ativos' },
  { id: 'ARQUIVADO', label: 'Arquivados' },
]

function lerStatusDaUrl(params: URLSearchParams): StatusProjeto {
  return params.get('status') === 'ARQUIVADO' ? 'ARQUIVADO' : 'ATIVO'
}

function lerPageDaUrl(params: URLSearchParams): number {
  return Math.max(0, Number(params.get('page') ?? 0) || 0)
}

function truncarDescricao(descricao: string | null, max = 120): string | null {
  if (!descricao) {
    return null
  }
  const texto = descricao.trim()
  if (texto.length <= max) {
    return texto
  }
  return `${texto.slice(0, max - 1)}…`
}

function ProjetosLista({
  projetos,
  tagIds,
  acaoId,
  onArquivar,
  onReativar,
  onReordenar,
}: {
  projetos: ProjetoResponse[]
  tagIds: string[]
  acaoId: string | null
  onArquivar: (id: string, nome: string) => void
  onReativar: (id: string) => void
  onReordenar: (ids: string[]) => void | Promise<void>
}) {
  const podeArrastar = tagIds.length < 2
  const ordenados = useMemo(
    () =>
      podeArrastar ? [...projetos].sort(compararOrdem) : ordenarPorTagMatch(projetos, tagIds),
    [projetos, tagIds, podeArrastar],
  )
  const ids = useMemo(() => ordenados.map((projeto) => projeto.id), [ordenados])
  const { idsVisiveis, arrastandoId, registrarItem, handleProps } = useListaArrastavel({
    ids,
    onReordenar,
    disabled: !podeArrastar,
  })
  const porId = useMemo(
    () => new Map(ordenados.map((projeto) => [projeto.id, projeto])),
    [ordenados],
  )

  return (
    <ul className="projeto-cards">
      {idsVisiveis.map((id) => {
        const projeto = porId.get(id)
        if (!projeto) {
          return null
        }
        const descricaoCurta = truncarDescricao(projeto.descricao)
        return (
          <li
            key={projeto.id}
            ref={(el) => registrarItem(projeto.id, el)}
            className={[
              projeto.status === 'ARQUIVADO' ? 'projeto-card projeto-card--arquivado' : 'projeto-card',
              arrastandoId === projeto.id ? 'lista-arraste__item--arrastando' : '',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <ArrasteHandle
              label={`Reordenar ${projeto.nome}`}
              disabled={!podeArrastar}
              {...handleProps(projeto.id)}
            />
            <div className="projeto-card__principal">
              <Link to={`/tarefas?projetoId=${projeto.id}`} className="projeto-card__titulo">
                {projeto.nome}
              </Link>
              <div className="projeto-card__meta">
                <span className={`badge status-${projeto.status.toLowerCase()}`}>
                  {STATUS_LABEL[projeto.status]}
                </span>
                <TagChipList tags={projeto.tags} />
              </div>
              {descricaoCurta ? (
                <p className="projeto-card__descricao">{descricaoCurta}</p>
              ) : null}
            </div>
            <div className="projeto-card__acoes">
              <Link to={`/projetos/${projeto.id}`} className="btn btn--ghost btn--sm">
                <PencilSimpleIcon aria-hidden />
                Editar
              </Link>
              <Link to={`/tarefas?projetoId=${projeto.id}`} className="btn btn--ghost btn--sm">
                Ver tarefas
              </Link>
              <Link to="/capacidade" className="btn btn--ghost btn--sm">
                Reservar tempo
              </Link>
              {projeto.status === 'ARQUIVADO' ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  disabled={acaoId === projeto.id}
                  onClick={() => onReativar(projeto.id)}
                >
                  {acaoId === projeto.id ? 'Reativando…' : 'Reativar'}
                </button>
              ) : (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  disabled={acaoId === projeto.id}
                  onClick={() => onArquivar(projeto.id, projeto.nome)}
                >
                  {acaoId === projeto.id ? 'Arquivando…' : 'Arquivar'}
                </button>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}

export function ProjetosPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = lerStatusDaUrl(searchParams)
  const page = lerPageDaUrl(searchParams)
  const tagIds = useMemo(() => lerTagIdsDaUrl(searchParams), [searchParams])

  const [resultado, setResultado] = useState<PageProjeto | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoId, setAcaoId] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)

  function escreverUrl(proximo: { status: StatusProjeto; page: number; tagIds: string[] }) {
    let params = new URLSearchParams()
    if (proximo.status === 'ARQUIVADO') {
      params.set('status', 'ARQUIVADO')
    }
    if (proximo.page > 0) {
      params.set('page', String(proximo.page))
    }
    params = escreverTagIdsNaUrl(params, proximo.tagIds)
    setSearchParams(params, { replace: true })
  }

  function aplicarVisao(proximo: StatusProjeto) {
    escreverUrl({ status: proximo, page: 0, tagIds })
  }

  function irParaPagina(proximo: number) {
    escreverUrl({ status, page: Math.max(0, proximo), tagIds })
  }

  function aplicarFiltroTags(proximos: string[]) {
    escreverUrl({ status, page: 0, tagIds: proximos })
  }

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarProjetos({
          status,
          page,
          size: 20,
          tagIds: tagIds.length ? tagIds : undefined,
        })
        if (ativo) {
          setResultado(data)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar os projetos.')
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
  }, [status, page, tagIds, recarregarEm])

  async function handleArquivar(id: string, nome: string) {
    const confirmado = await confirm({
      title: 'Arquivar projeto?',
      message: `“${nome}” ficará arquivado. As tarefas vinculadas permanecem; novas associações só a projetos ativos.`,
      confirmLabel: 'Arquivar',
    })
    if (!confirmado) {
      return
    }

    setErro(null)
    setAcaoId(id)
    try {
      await arquivarProjeto(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Projeto arquivado')
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível arquivar o projeto.',
      )
    } finally {
      setAcaoId(null)
    }
  }

  async function handleReativar(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await reativarProjeto(id)
      setRecarregarEm((n) => n + 1)
      toast.success('Projeto reativado')
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível reativar o projeto.',
      )
    } finally {
      setAcaoId(null)
    }
  }

  const listaVazia = !carregando && resultado != null && resultado.content.length === 0
  const emptyPrimeira = listaVazia && status === 'ATIVO'
  const totalPages = resultado?.totalPages ?? 0
  const novoProjeto = { pathname: '/projetos/nova', search: location.search }

  return (
    <main className="projetos-page">
      {confirmDialog}
      <header className="projetos-header">
        <div>
          <h1>Projetos</h1>
          <p className="projetos-subtitle">
            Agrupe tarefas do mesmo esforço e reserve tempo na capacidade.
          </p>
        </div>
        <div className="projetos-header-actions">
          <Link to="/capacidade" className="btn btn--secondary">
            Capacidade
          </Link>
          <Link to={novoProjeto} state={DRAWER_STATE} className="btn btn--primary">
            Novo projeto
          </Link>
        </div>
      </header>

      <div className="visao-chips" role="group" aria-label="Visão da lista">
        {VISOES.map((visao) => (
          <button
            key={visao.id}
            type="button"
            className={status === visao.id ? 'visao-chip visao-chip--ativo' : 'visao-chip'}
            aria-pressed={status === visao.id}
            onClick={() => aplicarVisao(visao.id)}
          >
            {status === visao.id ? <CheckIcon aria-hidden /> : null}
            {visao.label}
          </button>
        ))}
      </div>

      <div className="projetos-filtros">
        <TagFiltroPopover tagIds={tagIds} onAplicar={aplicarFiltroTags} />
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregando ? <Skeleton variant="tarefas-lista" /> : null}

      {!carregando && resultado ? (
        <>
          <p className="projetos-meta">
            {resultado.totalElements} projeto{resultado.totalElements === 1 ? '' : 's'}
          </p>

          {listaVazia ? (
            <div className="projetos-empty" role="status">
              {emptyPrimeira ? (
                <>
                  <p>Nenhum projeto ainda.</p>
                  <Link to={novoProjeto} state={DRAWER_STATE} className="btn btn--primary">
                    Criar primeiro projeto
                  </Link>
                </>
              ) : (
                <>
                  <p>Nenhum projeto arquivado.</p>
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
            <ProjetosLista
              projetos={resultado.content}
              tagIds={tagIds}
              acaoId={acaoId}
              onArquivar={(id, nome) => void handleArquivar(id, nome)}
              onReativar={(id) => void handleReativar(id)}
              onReordenar={async (ids) => {
                try {
                  await reordenarProjetos(ids)
                } catch (error) {
                  setErro(
                    error instanceof ApiError
                      ? error.message
                      : 'Não foi possível reordenar os projetos.',
                  )
                  setRecarregarEm((n) => n + 1)
                  throw error
                }
              }}
            />
          )}

          {totalPages > 1 ? (
            <div className="projetos-paginacao">
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

      <Outlet />
    </main>
  )
}
