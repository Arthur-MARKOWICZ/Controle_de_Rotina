import {
  ArrowCounterClockwiseIcon,
  ClockIcon,
  DotsThreeIcon,
  PencilSimpleIcon,
  TrashIcon,
} from '@phosphor-icons/react'
import { Link } from 'react-router-dom'

import { Popover } from '../feedback/Popover'
import { PRIORIDADE_LABEL, STATUS_TAREFA_LABEL } from '../home/labels'
import type { TarefaResponse } from './api/tarefaApi'
import { formatarDataHora } from './datetime'
import { formatarPrazoLista } from './listaGrupos'
import type { VisaoLista } from './listaUrl'
import type { TarefaDrawerState } from './TarefaDrawer'
import type { StatusTarefa } from './validation/tarefaForm'

const STATUS_PADRAO_DA_VISAO: Record<VisaoLista, StatusTarefa | null> = {
  todas: 'PENDENTE',
  pendentes: 'PENDENTE',
  em_progresso: 'EM_PROGRESSO',
  finalizadas: 'FINALIZADA',
  excluidas: null,
}

const DRAWER_STATE: TarefaDrawerState = { daLista: true }

type Props = {
  tarefa: TarefaResponse
  visao: VisaoLista
  /** Query string atual, preservada ao abrir o drawer. */
  search: string
  onExcluir: (tarefa: TarefaResponse) => void
  onRestaurar: (tarefa: TarefaResponse) => void
}

export function TarefaCard({ tarefa, visao, search, onExcluir, onRestaurar }: Props) {
  const destino = { pathname: `/tarefas/${tarefa.id}`, search }
  const mostrarStatus = tarefa.status !== STATUS_PADRAO_DA_VISAO[visao]
  const prioridade = tarefa.prioridade.toLowerCase()

  return (
    <li className={tarefa.deletada ? 'tarefa-card tarefa-card--deletada' : 'tarefa-card'}>
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
            {formatarPrazoLista(tarefa.prazo)}
          </time>
          <span className="tarefa-card__duracao">
            <ClockIcon aria-hidden />
            {tarefa.duracaoMinutos} min
          </span>
          {tarefa.projetoNome ? (
            <span className="badge badge--projeto">{tarefa.projetoNome}</span>
          ) : null}
          {mostrarStatus ? (
            <span className={`badge status-${tarefa.status.toLowerCase()}`}>
              {STATUS_TAREFA_LABEL[tarefa.status]}
            </span>
          ) : null}
          {tarefa.deletada ? <span className="badge status-deletada">Excluída</span> : null}
        </p>
      </div>

      <div className="tarefa-card__acoes">
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
    </li>
  )
}
