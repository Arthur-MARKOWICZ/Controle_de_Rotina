import { ArrowLeftIcon, PencilSimpleIcon } from '@phosphor-icons/react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, Outlet, useLocation, useParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { dataHojeIso } from '../../planner/data'
import {
  ACOES_POR_STATUS,
  CONFIRMACAO_ACAO,
  EXECUTAR_ACAO,
  FALHA_ACAO,
  ROTULO_ACAO,
  SUCESSO_ACAO,
  type AcaoObjetivo,
} from '../acoesObjetivo'
import {
  buscarObjetivo,
  listarItensObjetivo,
  obterEvolucaoObjetivo,
  type ObjetivoEvolucao as EvolucaoApi,
  type ObjetivoItens,
  type ObjetivoResponse,
  type StatusObjetivo,
} from '../api/objetivoApi'
import { montarSerie } from '../evolucaoObjetivo'
import type { ObjetivoDrawerState, ObjetivosOutletContext } from '../ObjetivoDrawer'
import { ObjetivoEvolucao } from '../ObjetivoEvolucao'
import { ObjetivoItensSecao, totalItens } from '../ObjetivoItensSecao'
import { ObjetivoProgresso } from '../ObjetivoProgresso'
import { rotuloConclusao, rotuloPrazo, situacaoPrazo } from '../prazoObjetivo'

const STATUS_LABEL: Record<StatusObjetivo, string> = {
  ATIVO: 'Ativo',
  CONCLUIDO: 'Concluído',
  ARQUIVADO: 'Arquivado',
}

const ERRO_ITENS = 'Não foi possível carregar os itens vinculados.'

const ERRO_EVOLUCAO = 'Não foi possível carregar a evolução.'

const TEXTO_NAO_SOMAM =
  'Os itens vinculados ainda não somam tempo. Tarefas canceladas e hábitos pausados não contam.'

/** O estado é keyed por `id`: trocar de objetivo remonta e recomeça do carregando. */
export function ObjetivoDetalhePage() {
  const { id } = useParams<{ id: string }>()
  if (!id) {
    return null
  }
  return <ObjetivoDetalhe key={id} id={id} />
}

function ObjetivoDetalhe({ id }: { id: string }) {
  const location = useLocation()
  const estado = (location.state ?? {}) as ObjetivoDrawerState
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const hojeIso = dataHojeIso()

  const [objetivo, setObjetivo] = useState<ObjetivoResponse | null>(null)
  const [carregandoObjetivo, setCarregandoObjetivo] = useState(true)
  const [erroObjetivo, setErroObjetivo] = useState<string | null>(null)
  const [recarregarObjetivoEm, setRecarregarObjetivoEm] = useState(0)

  const [itens, setItens] = useState<ObjetivoItens | null>(null)
  const [carregandoItens, setCarregandoItens] = useState(true)
  const [erroItens, setErroItens] = useState<string | null>(null)
  const [recarregarItensEm, setRecarregarItensEm] = useState(0)

  const [evolucao, setEvolucao] = useState<EvolucaoApi | null>(null)
  const [carregandoEvolucao, setCarregandoEvolucao] = useState(true)
  const [erroEvolucao, setErroEvolucao] = useState<string | null>(null)
  const [recarregarEvolucaoEm, setRecarregarEvolucaoEm] = useState(0)

  const [acao, setAcao] = useState<AcaoObjetivo | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const acaoRef = useRef(false)

  useEffect(() => {
    let ativo = true
    async function carregar() {
      try {
        const data = await buscarObjetivo(id)
        if (ativo) {
          setObjetivo(data)
          setErroObjetivo(null)
        }
      } catch (error) {
        if (ativo) {
          setObjetivo(null)
          setErroObjetivo(
            error instanceof ApiError ? error.message : 'Não foi possível carregar o objetivo.',
          )
        }
      } finally {
        if (ativo) {
          setCarregandoObjetivo(false)
        }
      }
    }
    void carregar()
    return () => {
      ativo = false
    }
  }, [id, recarregarObjetivoEm])

  useEffect(() => {
    let ativo = true
    async function carregar() {
      try {
        const data = await listarItensObjetivo(id)
        if (ativo) {
          setItens(data)
          setErroItens(null)
        }
      } catch {
        if (ativo) {
          setItens(null)
          setErroItens(ERRO_ITENS)
        }
      } finally {
        if (ativo) {
          setCarregandoItens(false)
        }
      }
    }
    void carregar()
    return () => {
      ativo = false
    }
  }, [id, recarregarItensEm])

  // A evolução falha e recarrega por conta própria: um erro nela não derruba o resto da página.
  useEffect(() => {
    let ativo = true
    async function carregar() {
      setCarregandoEvolucao(true)
      try {
        const data = await obterEvolucaoObjetivo(id)
        if (ativo) {
          setEvolucao(data)
          setErroEvolucao(null)
        }
      } catch {
        if (ativo) {
          setEvolucao(null)
          setErroEvolucao(ERRO_EVOLUCAO)
        }
      } finally {
        if (ativo) {
          setCarregandoEvolucao(false)
        }
      }
    }
    void carregar()
    return () => {
      ativo = false
    }
  }, [id, recarregarEvolucaoEm])

  async function executarAcao(tipo: AcaoObjetivo) {
    if (!objetivo || acaoRef.current) {
      return
    }
    acaoRef.current = true
    try {
      const pedirConfirmacao = CONFIRMACAO_ACAO[tipo]
      if (pedirConfirmacao && !(await confirm(pedirConfirmacao(objetivo.titulo)))) {
        return
      }
      setErroAcao(null)
      setAcao(tipo)
      try {
        const atualizado = await EXECUTAR_ACAO[tipo](objetivo.id)
        setObjetivo(atualizado)
        // Concluir, arquivar e reabrir mudam a janela dos hábitos: a curva pode mudar.
        setRecarregarEvolucaoEm((n) => n + 1)
        toast.success(SUCESSO_ACAO[tipo])
      } catch (error) {
        setErroAcao(error instanceof ApiError ? error.message : FALHA_ACAO[tipo])
        // Pode ter mudado em outra aba (409/404): relê o estado real.
        setRecarregarObjetivoEm((n) => n + 1)
      }
    } finally {
      setAcao(null)
      acaoRef.current = false
    }
  }

  const outletContext: ObjetivosOutletContext = {
    aoSalvar: () => {
      setRecarregarObjetivoEm((n) => n + 1)
      setRecarregarItensEm((n) => n + 1)
      setRecarregarEvolucaoEm((n) => n + 1)
    },
  }

  const voltar = { pathname: '/objetivos', search: estado.listaSearch ?? '' }
  const ativo = objetivo?.status === 'ATIVO'
  const vencido = objetivo != null && ativo && situacaoPrazo(objetivo.prazo, hojeIso) === 'vencido'
  // Progresso nulo também acontece com itens que não somam tempo. Só dá para explicar isso
  // com os itens carregados; sem itens a seção abaixo já convida a vincular.
  const itensCarregados = !carregandoItens && erroItens == null && itens != null
  const semProgresso = itensCarregados && totalItens(itens) > 0 ? TEXTO_NAO_SOMAM : null
  const serieEvolucao = useMemo(
    () => (objetivo && evolucao ? montarSerie(evolucao, objetivo, hojeIso) : null),
    [objetivo, evolucao, hojeIso],
  )
  const linhaData = objetivo
    ? ativo
      ? rotuloPrazo(objetivo.prazo, hojeIso)
      : rotuloConclusao(objetivo.concluidoEm, hojeIso)
    : null

  return (
    <main className="objetivos-page">
      {confirmDialog}
      <Link to={voltar} className="objetivo-detalhe__voltar">
        <ArrowLeftIcon aria-hidden /> Voltar a Objetivos
      </Link>

      {carregandoObjetivo ? <Skeleton variant="tarefas-lista" /> : null}
      {erroObjetivo ? <InlineAlert>{erroObjetivo}</InlineAlert> : null}

      {objetivo ? (
        <>
          <header className="objetivos-header">
            <div>
              <h1>{objetivo.titulo}</h1>
              <div className="objetivo-detalhe__meta">
                <span className={`badge status-${objetivo.status.toLowerCase()}`}>
                  {STATUS_LABEL[objetivo.status]}
                </span>
                {linhaData ? (
                  <span className={vencido ? 'objetivo-detalhe__prazo--vencido' : undefined}>
                    {linhaData}
                  </span>
                ) : null}
              </div>
              {objetivo.descricao ? (
                <p className="objetivos-subtitle">{objetivo.descricao}</p>
              ) : null}
            </div>
            <div className="objetivo-detalhe__acoes">
              <Link
                to={`/objetivos/${objetivo.id}/editar`}
                state={{ listaSearch: estado.listaSearch } satisfies ObjetivoDrawerState}
                className="btn btn--ghost"
              >
                <PencilSimpleIcon aria-hidden />
                Editar
              </Link>
              {ACOES_POR_STATUS[objetivo.status].map((tipo) => (
                <button
                  key={tipo}
                  type="button"
                  className="btn btn--ghost"
                  disabled={acao != null}
                  onClick={() => void executarAcao(tipo)}
                >
                  {acao === tipo ? ROTULO_ACAO[tipo].andamento : ROTULO_ACAO[tipo].label}
                </button>
              ))}
            </div>
          </header>

          {erroAcao ? <InlineAlert>{erroAcao}</InlineAlert> : null}

          <ObjetivoProgresso
            titulo={objetivo.titulo}
            progresso={objetivo.progresso}
            tamanho="destaque"
            semProgresso={semProgresso}
          />

          <ObjetivoEvolucao
            serie={serieEvolucao}
            carregando={carregandoEvolucao}
            erro={erroEvolucao}
            onTentarDeNovo={() => setRecarregarEvolucaoEm((n) => n + 1)}
          />

          <ObjetivoItensSecao itens={itens} carregando={carregandoItens} erro={erroItens} />
        </>
      ) : (
        <h1>Objetivo</h1>
      )}

      <Outlet context={outletContext} />
    </main>
  )
}
