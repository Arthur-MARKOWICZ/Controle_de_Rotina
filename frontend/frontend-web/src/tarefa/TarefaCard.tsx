import {
  ArrowCounterClockwiseIcon,
  CaretDownIcon,
  CaretRightIcon,
  CheckIcon,
  ClockIcon,
  DotsThreeIcon,
  PencilSimpleIcon,
  PlayIcon,
  TrashIcon,
} from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { Popover } from '../feedback/Popover'
import { useToast } from '../feedback/ToastProvider'
import { useSessaoAtiva } from '../execucao/SessaoAtivaContext'
import { listarExecucoes } from '../execucao/api/execucaoApi'
import { PRIORIDADE_LABEL, STATUS_TAREFA_LABEL } from '../home/labels'
import { rotuloEspera } from '../pendencia/api/espera'
import { dataHojeIso } from '../planner/data'
import { mensagensFeedbackXp } from '../xp/feedbackXp'
import { useXpProgress } from '../xp/XpProgressContext'
import {
  concluirTarefaDireto,
  iniciarTarefaSessao,
  type TarefaResponse,
} from './api/tarefaApi'
import { formatarDataHora } from './datetime'
import { TagChipList } from '../tag/TagChip'
import { formatarPrazoLista } from './listaGrupos'
import type { VisaoLista } from './listaUrl'
import { SessaoDuracaoDialog } from './SessaoDuracaoDialog'
import type { TarefaDrawerState } from './TarefaDrawer'
import type { StatusTarefa } from './validation/tarefaForm'

const STATUS_PADRAO_DA_VISAO: Record<VisaoLista, StatusTarefa | null> = {
  todas: 'PENDENTE',
  pendentes: 'PENDENTE',
  em_progresso: 'EM_PROGRESSO',
  finalizadas: 'FINALIZADA',
  excluidas: null,
}

const STATUS_SESSAO: StatusTarefa[] = ['PENDENTE', 'EM_PROGRESSO', 'ATRASADA']

const DRAWER_STATE: TarefaDrawerState = { daLista: true }

type Props = {
  tarefa: TarefaResponse
  visao: VisaoLista
  /** Query string atual, preservada ao abrir o drawer. */
  search: string
  onExcluir: (tarefa: TarefaResponse) => void
  onRestaurar: (tarefa: TarefaResponse) => void
  onSessaoAlterada?: () => void
  expandido?: boolean
  onToggleExpand?: () => void
  indicadorPai?: boolean
  arrasteHandle?: ReactNode
}

type PickerState = { acao: 'iniciar' | 'concluir' } | null

async function blocoAbertoNoAprovado(tarefaId: string): Promise<string | null> {
  const dia = await listarExecucoes(dataHojeIso())
  const aberto = dia.itens.find(
    (item) =>
      item.tarefaResumo?.id === tarefaId &&
      item.execucao.status !== 'CONCLUIDO',
  )
  return aberto?.bloco.id ?? null
}

export function TarefaCard({
  tarefa,
  visao,
  search,
  onExcluir,
  onRestaurar,
  onSessaoAlterada,
  expandido = false,
  onToggleExpand,
  indicadorPai = false,
  arrasteHandle,
}: Props) {
  const toast = useToast()
  const { progresso, aplicarProgresso } = useXpProgress()
  const { registrarInicio, refrescarDoDia } = useSessaoAtiva()
  const destino = { pathname: `/tarefas/${tarefa.id}`, search }
  const destinoPai = tarefa.parentId
    ? { pathname: `/tarefas/${tarefa.parentId}`, search }
    : null
  const mostrarStatus = tarefa.status !== STATUS_PADRAO_DA_VISAO[visao]
  const prioridade = tarefa.prioridade.toLowerCase()
  const restante = tarefa.restanteMinutos ?? tarefa.duracaoMinutos
  const bloqueada = tarefa.bloqueada ?? false
  const espera = rotuloEspera(tarefa.esperas)
  const podeSessao =
    !tarefa.deletada &&
    restante > 0 &&
    STATUS_SESSAO.includes(tarefa.status) &&
    !tarefa.temFilhos &&
    !bloqueada

  const [picker, setPicker] = useState<PickerState>(null)
  const [ocupado, setOcupado] = useState(false)

  async function executarSessao(
    acao: 'iniciar' | 'concluir',
    duracaoMinutos?: number,
    blocoId?: string | null,
  ) {
    setOcupado(true)
    try {
      const body = {
        data: dataHojeIso(),
        duracaoMinutos: duracaoMinutos ?? null,
        blocoId: blocoId ?? null,
      }
      if (acao === 'iniciar') {
        const execucao = await iniciarTarefaSessao(tarefa.id, body)
        registrarInicio(tarefa.titulo, duracaoMinutos ?? restante, execucao)
        toast.success('Sessão iniciada')
      } else {
        const execucao = await concluirTarefaDireto(tarefa.id, body)
        const xp = execucao.xp
        if (xp) {
          const nivelAnterior = progresso?.nivel ?? null
          for (const mensagem of mensagensFeedbackXp(xp, nivelAnterior)) {
            toast.success(mensagem)
          }
          aplicarProgresso(xp.progresso)
        } else {
          toast.success('Sessão concluída')
        }
      }
      await refrescarDoDia()
      onSessaoAlterada?.()
      setPicker(null)
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : acao === 'iniciar'
            ? 'Não foi possível iniciar a sessão.'
            : 'Não foi possível concluir a sessão.',
      )
    } finally {
      setOcupado(false)
    }
  }

  async function handleAcao(acao: 'iniciar' | 'concluir') {
    setOcupado(true)
    try {
      const blocoId = await blocoAbertoNoAprovado(tarefa.id)
      if (blocoId) {
        await executarSessao(acao, undefined, blocoId)
        return
      }
      setPicker({ acao })
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : 'Não foi possível verificar o plano de hoje.',
      )
    } finally {
      setOcupado(false)
    }
  }

  return (
    <article className={tarefa.deletada ? 'tarefa-card tarefa-card--deletada' : 'tarefa-card'}>
      {arrasteHandle}
      {onToggleExpand ? (
        <button
          type="button"
          className="btn btn--ghost btn--sm btn--icon tarefa-card__expandir"
          aria-expanded={expandido}
          aria-label={
            expandido
              ? `Recolher subtarefas de ${tarefa.titulo}`
              : `Expandir subtarefas de ${tarefa.titulo}`
          }
          onClick={onToggleExpand}
        >
          {expandido ? <CaretDownIcon aria-hidden /> : <CaretRightIcon aria-hidden />}
        </button>
      ) : null}
      <div className="tarefa-card__principal">
        <Link to={destino} state={DRAWER_STATE} className="tarefa-card__titulo">
          <span
            className={`prioridade-ponto prioridade-ponto--${prioridade}`}
            title={`Prioridade ${PRIORIDADE_LABEL[tarefa.prioridade]}`}
            aria-hidden
          />
          <span className="visually-hidden">Prioridade {PRIORIDADE_LABEL[tarefa.prioridade]}: </span>
          <span>{tarefa.titulo}</span>
        </Link>
        <p className="tarefa-card__meta">
          <time dateTime={tarefa.prazo ?? undefined} title={formatarDataHora(tarefa.prazo)}>
            {formatarPrazoLista(tarefa.prazo, undefined, tarefa.status)}
          </time>
          <span className="tarefa-card__duracao">
            <ClockIcon aria-hidden />
            {tarefa.duracaoMinutos} min
            {restante < tarefa.duracaoMinutos ? ` · ${restante} rest.` : null}
          </span>
          {tarefa.projetoNome && tarefa.projetoId ? (
            <Link to={`/tarefas?projetoId=${tarefa.projetoId}`} className="badge badge--projeto">
              {tarefa.projetoNome}
            </Link>
          ) : tarefa.projetoNome ? (
            <span className="badge badge--projeto">{tarefa.projetoNome}</span>
          ) : null}
          <TagChipList tags={tarefa.tags} />
          {mostrarStatus ? (
            <span className={`badge status-${tarefa.status.toLowerCase()}`}>
              {STATUS_TAREFA_LABEL[tarefa.status]}
            </span>
          ) : null}
          {tarefa.deletada ? <span className="badge status-deletada">Excluída</span> : null}
          {bloqueada ? (
            <span className="badge status-atrasada tarefa-card__espera" title={espera ?? undefined}>
              {espera ?? 'Bloqueada por espera'}
            </span>
          ) : null}
          {indicadorPai && destinoPai && tarefa.parentTitulo ? (
            <Link to={destinoPai} state={DRAWER_STATE} className="tarefa-card__pai">
              Subtarefa de {tarefa.parentTitulo}
            </Link>
          ) : null}
        </p>
      </div>

      <div className="tarefa-card__acoes">
        {podeSessao ? (
          <>
            <button
              type="button"
              className="btn btn--primary btn--sm"
              disabled={ocupado}
              onClick={() => void handleAcao('iniciar')}
              aria-label={`Iniciar ${tarefa.titulo}`}
            >
              <PlayIcon aria-hidden />
              Iniciar
            </button>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              disabled={ocupado}
              onClick={() => void handleAcao('concluir')}
              aria-label={`Concluir ${tarefa.titulo}`}
            >
              <CheckIcon aria-hidden />
              Concluir
            </button>
          </>
        ) : null}
        {tarefa.deletada ? (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => onRestaurar(tarefa)}
          >
            <ArrowCounterClockwiseIcon aria-hidden />
            Restaurar
          </button>
        ) : (
          <button
            type="button"
            className="btn btn--ghost btn--danger btn--sm btn--icon tarefa-card__excluir-rapido"
            aria-label={`Excluir ${tarefa.titulo}`}
            onClick={() => onExcluir(tarefa)}
          >
            <TrashIcon aria-hidden />
          </button>
        )}
        <Popover
          label={<DotsThreeIcon aria-hidden />}
          ariaLabel={`Ações de ${tarefa.titulo}`}
          triggerClassName="btn btn--ghost btn--sm btn--icon"
          align="end"
          fecharAoEscolher
        >
          <div className="menu-acoes" role="group" aria-label={`Ações de ${tarefa.titulo}`}>
            <Link to={destino} state={DRAWER_STATE} className="menu-acoes__item">
              <PencilSimpleIcon aria-hidden />
              Editar
            </Link>
            {tarefa.deletada ? (
              <button
                type="button"
                className="menu-acoes__item"
                onClick={() => onRestaurar(tarefa)}
              >
                <ArrowCounterClockwiseIcon aria-hidden />
                Restaurar
              </button>
            ) : (
              <button
                type="button"
                className="menu-acoes__item menu-acoes__item--perigo"
                onClick={() => onExcluir(tarefa)}
              >
                <TrashIcon aria-hidden />
                Excluir
              </button>
            )}
          </div>
        </Popover>
      </div>

      <SessaoDuracaoDialog
        open={picker != null}
        restanteMinutos={restante}
        tituloAcao={picker?.acao === 'concluir' ? 'Concluir sessão' : 'Iniciar sessão'}
        ocupado={ocupado}
        onClose={() => setPicker(null)}
        onConfirm={(duracao) => {
          if (!picker) {
            return
          }
          void executarSessao(picker.acao, duracao)
        }}
      />
    </article>
  )
}
