import { CheckIcon, PencilSimpleIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import {
  arquivarProjeto,
  listarProjetos,
  reativarProjeto,
  type PageProjeto,
  type StatusProjeto,
} from '../api/projetoApi'
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

export function ProjetosPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = lerStatusDaUrl(searchParams)
  const page = lerPageDaUrl(searchParams)

  const [resultado, setResultado] = useState<PageProjeto | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoId, setAcaoId] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)

  function escreverUrl(proximo: { status: StatusProjeto; page: number }) {
    const params = new URLSearchParams()
    if (proximo.status === 'ARQUIVADO') {
      params.set('status', 'ARQUIVADO')
    }
    if (proximo.page > 0) {
      params.set('page', String(proximo.page))
    }
    setSearchParams(params, { replace: true })
  }

  function aplicarVisao(proximo: StatusProjeto) {
    escreverUrl({ status: proximo, page: 0 })
  }

  function irParaPagina(proximo: number) {
    escreverUrl({ status, page: Math.max(0, proximo) })
  }

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarProjetos({ status, page, size: 20 })
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
  }, [status, page, recarregarEm])

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
            <ul className="projeto-cards">
              {resultado.content.map((projeto) => {
                const descricaoCurta = truncarDescricao(projeto.descricao)
                return (
                  <li
                    key={projeto.id}
                    className={
                      projeto.status === 'ARQUIVADO'
                        ? 'projeto-card projeto-card--arquivado'
                        : 'projeto-card'
                    }
                  >
                    <div className="projeto-card__principal">
                      <Link to={`/projetos/${projeto.id}`} className="projeto-card__titulo">
                        {projeto.nome}
                      </Link>
                      <div className="projeto-card__meta">
                        <span
                          className={`badge status-${projeto.status.toLowerCase()}`}
                        >
                          {STATUS_LABEL[projeto.status]}
                        </span>
                      </div>
                      {descricaoCurta ? (
                        <p className="projeto-card__descricao">{descricaoCurta}</p>
                      ) : null}
                    </div>
                    <div className="projeto-card__acoes">
                      <Link
                        to={`/projetos/${projeto.id}`}
                        className="btn btn--ghost btn--sm"
                      >
                        <PencilSimpleIcon aria-hidden />
                        Editar
                      </Link>
                      <Link
                        to={`/tarefas?projetoId=${projeto.id}`}
                        className="btn btn--ghost btn--sm"
                      >
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
                          onClick={() => void handleReativar(projeto.id)}
                        >
                          {acaoId === projeto.id ? 'Reativando…' : 'Reativar'}
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          disabled={acaoId === projeto.id}
                          onClick={() => void handleArquivar(projeto.id, projeto.nome)}
                        >
                          {acaoId === projeto.id ? 'Arquivando…' : 'Arquivar'}
                        </button>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
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
