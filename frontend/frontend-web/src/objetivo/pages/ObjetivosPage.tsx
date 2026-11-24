import { CheckIcon, FlagIcon } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { dataHojeIso } from '../../planner/data'
import {
  listarObjetivos,
  type ObjetivoResponse,
  type PageObjetivo,
  type StatusObjetivo,
} from '../api/objetivoApi'
import {
  CONFIRMACAO_ACAO,
  EXECUTAR_ACAO,
  FALHA_ACAO,
  SUCESSO_ACAO,
  type AcaoObjetivo,
} from '../acoesObjetivo'
import { ObjetivoCard } from '../ObjetivoCard'
import type { ObjetivoDrawerState, ObjetivosOutletContext } from '../ObjetivoDrawer'

const DRAWER_STATE: ObjetivoDrawerState = { daLista: true }

const VISOES: { id: StatusObjetivo; label: string }[] = [
  { id: 'ATIVO', label: 'Ativos' },
  { id: 'CONCLUIDO', label: 'Concluídos' },
  { id: 'ARQUIVADO', label: 'Arquivados' },
]

const VAZIO_POR_VISAO: Record<Exclude<StatusObjetivo, 'ATIVO'>, string> = {
  CONCLUIDO: 'Nenhum objetivo concluído.',
  ARQUIVADO: 'Nenhum objetivo arquivado.',
}

function lerStatusDaUrl(params: URLSearchParams): StatusObjetivo {
  const status = params.get('status')
  return status === 'CONCLUIDO' || status === 'ARQUIVADO' ? status : 'ATIVO'
}

function lerPageDaUrl(params: URLSearchParams): number {
  return Math.max(0, Number(params.get('page') ?? 0) || 0)
}

export function ObjetivosPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = lerStatusDaUrl(searchParams)
  const page = lerPageDaUrl(searchParams)
  const hojeIso = dataHojeIso()

  const [resultado, setResultado] = useState<PageObjetivo | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [acao, setAcao] = useState<{ id: string; acao: AcaoObjetivo } | null>(null)
  const acaoRef = useRef(false)
  const [recarregarEm, setRecarregarEm] = useState(0)

  function escreverUrl(proximo: { status: StatusObjetivo; page: number }) {
    const params = new URLSearchParams()
    if (proximo.status !== 'ATIVO') {
      params.set('status', proximo.status)
    }
    if (proximo.page > 0) {
      params.set('page', String(proximo.page))
    }
    setSearchParams(params, { replace: true })
  }

  function aplicarVisao(proximo: StatusObjetivo) {
    setErroAcao(null)
    escreverUrl({ status: proximo, page: 0 })
  }

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErroCarga(null)
      try {
        const data = await listarObjetivos({ status, page, size: 20 })
        if (ativo) {
          setResultado(data)
        }
      } catch {
        if (ativo) {
          setErroCarga('Não foi possível carregar os objetivos.')
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

  async function executarAcao(tipo: AcaoObjetivo, objetivo: ObjetivoResponse) {
    if (acaoRef.current) {
      return
    }
    acaoRef.current = true
    try {
      const pedirConfirmacao = CONFIRMACAO_ACAO[tipo]
      if (pedirConfirmacao && !(await confirm(pedirConfirmacao(objetivo.titulo)))) {
        return
      }
      setErroAcao(null)
      setAcao({ id: objetivo.id, acao: tipo })
      try {
        await EXECUTAR_ACAO[tipo](objetivo.id)
        toast.success(SUCESSO_ACAO[tipo])
      } catch (error) {
        setErroAcao(
          error instanceof ApiError ? error.message : FALHA_ACAO[tipo],
        )
      }
      setRecarregarEm((n) => n + 1)
    } finally {
      setAcao(null)
      acaoRef.current = false
    }
  }

  const outletContext: ObjetivosOutletContext = {
    aoSalvar: () => setRecarregarEm((n) => n + 1),
  }

  const totalPages = resultado?.page.totalPages ?? 0
  const listaVazia = !carregando && resultado != null && resultado.content.length === 0
  const novoObjetivo = { pathname: '/objetivos/nova', search: location.search }
  const total = resultado?.page.totalElements ?? 0

  return (
    <main className="objetivos-page">
      {confirmDialog}
      <header className="objetivos-header">
        <div>
          <h1>Objetivos</h1>
          <p className="objetivos-subtitle">O que você quer alcançar. Mantenha à vista.</p>
        </div>
        <Link to={novoObjetivo} state={DRAWER_STATE} className="btn btn--primary">
          Novo objetivo
        </Link>
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

      {erroCarga ? <InlineAlert>{erroCarga}</InlineAlert> : null}
      {erroAcao ? <InlineAlert>{erroAcao}</InlineAlert> : null}

      {carregando ? <Skeleton variant="tarefas-lista" /> : null}

      {!carregando && resultado ? (
        <>
          <p className="objetivos-meta">
            {total} objetivo{total === 1 ? '' : 's'}
          </p>

          {listaVazia ? (
            <div className="objetivos-empty" role="status">
              {status === 'ATIVO' ? (
                <>
                  <FlagIcon className="objetivos-empty__icone" aria-hidden />
                  <p className="objetivos-empty__titulo">Nenhum objetivo ainda</p>
                  <p>Registre o que você quer alcançar — por exemplo, perder 5 kg.</p>
                  <Link to={novoObjetivo} state={DRAWER_STATE} className="btn btn--primary">
                    Criar primeiro objetivo
                  </Link>
                </>
              ) : (
                <>
                  <p className="objetivos-empty__titulo">{VAZIO_POR_VISAO[status]}</p>
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
            <ul className="objetivo-cards">
              {resultado.content.map((objetivo) => (
                <ObjetivoCard
                  key={objetivo.id}
                  objetivo={objetivo}
                  hojeIso={hojeIso}
                  acaoEmAndamento={acao?.id === objetivo.id ? acao.acao : null}
                  bloqueado={acao != null}
                  onAcao={(tipo, alvo) => void executarAcao(tipo, alvo)}
                />
              ))}
            </ul>
          )}

          {totalPages > 1 ? (
            <div className="objetivos-paginacao">
              <button
                type="button"
                className="btn btn--primary"
                disabled={page <= 0}
                onClick={() => escreverUrl({ status, page: page - 1 })}
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
                onClick={() => escreverUrl({ status, page: page + 1 })}
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
