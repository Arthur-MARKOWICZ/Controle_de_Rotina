import { CheckIcon, PauseIcon, PencilSimpleIcon, PlayIcon } from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { abortarExecucao, concluirExecucao } from '../execucao/api/execucaoApi'
import { useSessaoAtiva } from '../execucao/SessaoAtivaContext'
import { useToast } from '../feedback/ToastProvider'
import { diaSemanaDeDataIso } from '../home/explicacaoReservas'
import { dataHojeIso, formatarCapacidadeMinutos } from '../planner/data'
import { TagChipList } from '../tag/TagChip'
import { mensagensFeedbackXp } from '../xp/feedbackXp'
import type { XpConclusao } from '../xp/types'
import { useXpProgress } from '../xp/XpProgressContext'
import {
  concluirHabitoDireto,
  iniciarHabitoAvulso,
  type HabitoResponse,
  type StatusHabito,
} from './api/habitoApi'
import type { HabitoDrawerState } from './HabitoDrawer'
import { useMinutosHabito } from './useMinutosHabito'
import { rotuloDiasSemana } from './validation/habitoForm'

const DRAWER_STATE: HabitoDrawerState = { daLista: true }

const STATUS_LABEL: Record<StatusHabito, string> = {
  ATIVO: 'Ativo',
  PAUSADO: 'Pausado',
}

type Props = {
  habito: HabitoResponse
  acaoId: string | null
  onPausar: (id: string) => void
  onReativar: (id: string) => void
  onExcluir: (id: string, titulo: string) => void
  onRestaurar: (id: string) => void
  arrasteHandle?: ReactNode
  itemRef?: (el: HTMLLIElement | null) => void
  arrastando?: boolean
}

export function podeSessaoHabito(habito: HabitoResponse, hojeIso = dataHojeIso()): boolean {
  return (
    !habito.deletada &&
    habito.status === 'ATIVO' &&
    habito.diasSemana.includes(diaSemanaDeDataIso(hojeIso))
  )
}

export function HabitoCard({
  habito,
  acaoId,
  onPausar,
  onReativar,
  onExcluir,
  onRestaurar,
  arrasteHandle,
  itemRef,
  arrastando = false,
}: Props) {
  const { pedirMinutos, dialog: minutosDialog } = useMinutosHabito()
  const toast = useToast()
  const { progresso, aplicarProgresso } = useXpProgress()
  const { registrarInicio, refrescarDoDia, sessao, limpar } = useSessaoAtiva()
  const [ocupado, setOcupado] = useState(false)
  const [execucaoAtivaId, setExecucaoAtivaId] = useState<string | null>(null)
  const dias = rotuloDiasSemana(habito.diasSemana)
  const podeSessao = podeSessaoHabito(habito)
  const execucaoId =
    execucaoAtivaId ?? (sessao?.habitoId === habito.id ? sessao.execucaoId : null)
  const emAndamento = execucaoId != null
  const pendente = ocupado || acaoId === habito.id
  const destino = `/habitos/${habito.id}`

  function creditarXp(xp: XpConclusao) {
    const nivelAnterior = progresso?.nivel ?? null
    for (const mensagem of mensagensFeedbackXp(xp, nivelAnterior)) {
      toast.success(mensagem)
    }
    aplicarProgresso(xp.progresso)
  }

  async function handleAcao(acao: 'iniciar' | 'concluir') {
    let minutos: number | null = null
    if (acao === 'concluir') {
      minutos = await pedirMinutos(habito.titulo, habito.duracaoMinutos)
      if (minutos === null) {
        return
      }
    }
    setOcupado(true)
    try {
      const hoje = dataHojeIso()
      if (acao === 'iniciar') {
        const execucao = await iniciarHabitoAvulso(habito.id, hoje)
        registrarInicio(habito.titulo, habito.duracaoMinutos, execucao, { habitoId: habito.id })
        setExecucaoAtivaId(execucao.id)
        toast.success('Sessão iniciada')
      } else {
        const execucao = await concluirHabitoDireto(habito.id, hoje, minutos ?? undefined)
        if (execucao.xp) {
          creditarXp(execucao.xp)
        } else {
          toast.success('Hábito concluído')
        }
      }
      await refrescarDoDia()
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : acao === 'iniciar'
            ? 'Não foi possível iniciar o hábito.'
            : 'Não foi possível concluir o hábito.',
      )
    } finally {
      setOcupado(false)
    }
  }

  async function handleFinalizar() {
    if (!execucaoId) {
      toast.error('Execução não encontrada para finalizar.')
      return
    }
    setOcupado(true)
    try {
      const execucao = await concluirExecucao(execucaoId)
      if (execucao.xp) {
        creditarXp(execucao.xp)
      } else {
        toast.success('Sessão concluída')
      }
      setExecucaoAtivaId(null)
      limpar()
      await refrescarDoDia()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível finalizar o hábito.')
    } finally {
      setOcupado(false)
    }
  }

  async function handlePausarSessao() {
    if (!execucaoId) {
      toast.error('Execução não encontrada para pausar.')
      return
    }
    setOcupado(true)
    try {
      await abortarExecucao(execucaoId)
      toast.success('Sessão pausada')
      setExecucaoAtivaId(null)
      limpar()
      await refrescarDoDia()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível pausar a sessão.')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <li
      ref={itemRef}
      className={[
        habito.status === 'PAUSADO' || habito.deletada
          ? 'habito-card habito-card--pausado'
          : 'habito-card',
        arrastando ? 'lista-arraste__item--arrastando' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {arrasteHandle}
      <div className="habito-card__principal">
        <Link to={destino} state={DRAWER_STATE} className="habito-card__titulo">
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
        {podeSessao && emAndamento ? (
          <>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={pendente}
              onClick={() => void handleFinalizar()}
              aria-label={`Finalizar ${habito.titulo}`}
            >
              <CheckIcon aria-hidden />
              Finalizar
            </button>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              disabled={pendente}
              onClick={() => void handlePausarSessao()}
              aria-label={`Pausar sessão de ${habito.titulo}`}
            >
              <PauseIcon aria-hidden />
              Pausar
            </button>
          </>
        ) : null}
        {podeSessao && !emAndamento ? (
          <>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={pendente}
              onClick={() => void handleAcao('iniciar')}
              aria-label={`Iniciar ${habito.titulo}`}
            >
              <PlayIcon aria-hidden />
              Iniciar
            </button>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              disabled={pendente}
              onClick={() => void handleAcao('concluir')}
              aria-label={`Concluir ${habito.titulo}`}
            >
              <CheckIcon aria-hidden />
              Concluir
            </button>
          </>
        ) : null}
        <Link to={destino} state={DRAWER_STATE} className="btn btn--ghost btn--sm">
          <PencilSimpleIcon aria-hidden />
          {habito.deletada ? 'Ver' : 'Editar'}
        </Link>
        {habito.deletada ? (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={pendente}
            onClick={() => onRestaurar(habito.id)}
          >
            {acaoId === habito.id ? 'Restaurando…' : 'Restaurar'}
          </button>
        ) : habito.status === 'PAUSADO' ? (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={pendente}
            onClick={() => onReativar(habito.id)}
          >
            {acaoId === habito.id ? 'Reativando…' : 'Reativar'}
          </button>
        ) : emAndamento ? null : (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={pendente}
            onClick={() => onPausar(habito.id)}
          >
            {acaoId === habito.id ? 'Pausando…' : 'Pausar'}
          </button>
        )}
        {!habito.deletada ? (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={pendente}
            onClick={() => onExcluir(habito.id, habito.titulo)}
          >
            Excluir
          </button>
        ) : null}
      </div>
      {minutosDialog}
    </li>
  )
}
