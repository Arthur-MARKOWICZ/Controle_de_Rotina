import { PencilSimpleIcon } from '@phosphor-icons/react'
import { Link, useLocation } from 'react-router-dom'

import { truncarDescricao } from '../lista/truncarDescricao'
import type { ObjetivoResponse } from './api/objetivoApi'
import type { ObjetivoDrawerState } from './ObjetivoDrawer'
import { rotuloConclusao, rotuloPrazo, situacaoPrazo } from './prazoObjetivo'

export type AcaoObjetivo = 'concluir' | 'arquivar' | 'reabrir'

const DRAWER_STATE: ObjetivoDrawerState = { daLista: true }

const ROTULO_ACAO: Record<AcaoObjetivo, { label: string; andamento: string }> = {
  concluir: { label: 'Concluir', andamento: 'Concluindo…' },
  arquivar: { label: 'Arquivar', andamento: 'Arquivando…' },
  reabrir: { label: 'Reabrir', andamento: 'Reabrindo…' },
}

const ACOES_POR_STATUS: Record<ObjetivoResponse['status'], AcaoObjetivo[]> = {
  ATIVO: ['concluir', 'arquivar'],
  CONCLUIDO: ['arquivar', 'reabrir'],
  ARQUIVADO: ['reabrir'],
}

type Props = {
  objetivo: ObjetivoResponse
  hojeIso: string
  /** Ação em andamento neste card (muda o rótulo do botão). */
  acaoEmAndamento: AcaoObjetivo | null
  /** Há ação em andamento em qualquer card da lista. */
  bloqueado: boolean
  onAcao: (acao: AcaoObjetivo, objetivo: ObjetivoResponse) => void
}

export function ObjetivoCard({ objetivo, hojeIso, acaoEmAndamento, bloqueado, onAcao }: Props) {
  const location = useLocation()
  const editar = { pathname: `/objetivos/${objetivo.id}`, search: location.search }

  const ativo = objetivo.status === 'ATIVO'
  const vencido = ativo && situacaoPrazo(objetivo.prazo, hojeIso) === 'vencido'
  const linhaData = ativo
    ? rotuloPrazo(objetivo.prazo, hojeIso)
    : rotuloConclusao(objetivo.concluidoEm, hojeIso)
  const descricao = truncarDescricao(objetivo.descricao)

  const classes = [
    'objetivo-card',
    vencido ? 'objetivo-card--vencido' : '',
    objetivo.status === 'ARQUIVADO' ? 'objetivo-card--arquivado' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <li className={classes} data-vencido={vencido ? 'true' : undefined}>
      <div className="objetivo-card__principal">
        <Link to={editar} state={DRAWER_STATE} className="objetivo-card__titulo">
          {objetivo.titulo}
        </Link>
        {linhaData ? (
          <p
            className={
              vencido ? 'objetivo-card__prazo objetivo-card__prazo--vencido' : 'objetivo-card__prazo'
            }
          >
            {linhaData}
          </p>
        ) : null}
        {descricao ? <p className="objetivo-card__descricao">{descricao}</p> : null}
        <div className="objetivo-card__progresso" data-testid="objetivo-progresso">
          Progresso aparece quando houver itens vinculados
        </div>
      </div>
      <div className="objetivo-card__acoes">
        <Link to={editar} state={DRAWER_STATE} className="btn btn--ghost btn--sm">
          <PencilSimpleIcon aria-hidden />
          Editar
        </Link>
        {ACOES_POR_STATUS[objetivo.status].map((acao) => (
          <button
            key={acao}
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={bloqueado}
            onClick={() => onAcao(acao, objetivo)}
          >
            {acaoEmAndamento === acao ? ROTULO_ACAO[acao].andamento : ROTULO_ACAO[acao].label}
          </button>
        ))}
      </div>
    </li>
  )
}
