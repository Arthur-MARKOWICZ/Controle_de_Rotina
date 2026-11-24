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

type Visao = 'ativos' | 'encerrados'

const VISOES: { id: Visao; label: string }[] = [
  { id: 'ativos', label: 'Ativos' },
  { id: 'encerrados', label: 'Encerrados' },
]

/** Status pedidos à API por visão. Encerrados reúne concluídos e arquivados numa só lista. */
const STATUS_POR_VISAO: Record<Visao, StatusObjetivo[]> = {
  ativos: ['ATIVO'],
  encerrados: ['CONCLUIDO', 'ARQUIVADO'],
}

/** Links antigos (`?status=CONCLUIDO|ARQUIVADO`) abrem Encerrados; qualquer outro valor cai em Ativos. */
function ehLinkAntigoDeEncerrados(params: URLSearchParams): boolean {
  const status = params.get('status')
  return status === 'CONCLUIDO' || status === 'ARQUIVADO'
}

function lerVisaoDaUrl(params: URLSearchParams): Visao {
  return params.get('visao') === 'encerrados' || ehLinkAntigoDeEncerrados(params) ? 'encerrados' : 'ativos'
}

function lerPageDaUrl(params: URLSearchParams): number {
  return Math.max(0, Number(params.get('page') ?? 0) || 0)
}

export function ObjetivosPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const visao = lerVisaoDaUrl(searchParams)
  const page = lerPageDaUrl(searchParams)
  const hojeIso = dataHojeIso()

  const [resultado, setResultado] = useState<PageObjetivo | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [acao, setAcao] = useState<{ id: string; acao: AcaoObjetivo } | null>(null)
  const acaoRef = useRef(false)
  const [recarregarEm, setRecarregarEm] = useState(0)

  function escreverUrl(proximo: { visao: Visao; page: number }) {
    const params = new URLSearchParams()
    if (proximo.visao === 'encerrados') {
      params.set('visao', 'encerrados')
    }
    if (proximo.page > 0) {
      params.set('page', String(proximo.page))
    }
    setSearchParams(params, { replace: true })
  }

  function aplicarVisao(proximo: Visao) {
    setErroAcao(null)
    escreverUrl({ visao: proximo, page: 0 })
  }

  // Reescreve o link antigo para a URL nova, mantendo a página (o `search` é repassado ao detalhe).
  useEffect(() => {
    if (ehLinkAntigoDeEncerrados(searchParams)) {
      escreverUrl({ visao: 'encerrados', page: lerPageDaUrl(searchParams) })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErroCarga(null)
      try {
        const data = await listarObjetivos({ status: STATUS_POR_VISAO[visao], page, size: 20 })
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
  }, [visao, page, recarregarEm])

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
        {VISOES.map((opcao) => (
          <button
            key={opcao.id}
            type="button"
            className={visao === opcao.id ? 'visao-chip visao-chip--ativo' : 'visao-chip'}
            aria-pressed={visao === opcao.id}
            onClick={() => aplicarVisao(opcao.id)}
          >
            {visao === opcao.id ? <CheckIcon aria-hidden /> : null}
            {opcao.label}
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
              {visao === 'ativos' ? (
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
                  <p className="objetivos-empty__titulo">Nenhum objetivo encerrado.</p>
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => aplicarVisao('ativos')}
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
                onClick={() => escreverUrl({ visao, page: page - 1 })}
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
                onClick={() => escreverUrl({ visao, page: page + 1 })}
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
