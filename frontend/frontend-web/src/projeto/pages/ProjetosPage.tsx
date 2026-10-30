import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

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
  const [searchParams, setSearchParams] = useSearchParams()
  const status = lerStatusDaUrl(searchParams)

  const [resultado, setResultado] = useState<PageProjeto | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoId, setAcaoId] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)

  function aplicarVisao(proximo: StatusProjeto) {
    const params = new URLSearchParams(searchParams)
    if (proximo === 'ATIVO') {
      params.delete('status')
    } else {
      params.set('status', proximo)
    }
    setSearchParams(params, { replace: true })
  }

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarProjetos({ status, page: 0, size: 20 })
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
  }, [status, recarregarEm])

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
          <Link to="/projetos/nova" className="btn btn--primary">
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
                  <Link to="/projetos/nova" className="btn btn--primary">
                    Criar primeiro projeto
                  </Link>
                </>
              ) : (
                <p>Nenhum projeto arquivado.</p>
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
        </>
      ) : null}
    </main>
  )
}
