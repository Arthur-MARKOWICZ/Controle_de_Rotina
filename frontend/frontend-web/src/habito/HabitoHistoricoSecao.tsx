import { useCallback, useEffect, useId, useState } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import { formatarDataCurtaPtBr, formatarDiaSemanaCurto } from '../historico/formatacao'
import { buscarHistoricoHabito, type HabitoHistoricoDia } from './api/habitoApi'

/** Concluir direto anterior aos minutos informados: sem tempo gravado. */
function semTempoRegistrado(dia: HabitoHistoricoDia): boolean {
  return dia.origem === 'TIMER' && dia.minutosDedicados === 0
}

function rotuloResultado(dia: HabitoHistoricoDia): string {
  if (semTempoRegistrado(dia)) {
    return 'Tempo não registrado'
  }
  if (dia.suficiente) {
    return 'Meta atingida'
  }
  return `Faltaram ${dia.metaMinutos - dia.minutosDedicados} min`
}

function classeResultado(dia: HabitoHistoricoDia): string {
  if (semTempoRegistrado(dia)) {
    return 'habito-historico__resultado'
  }
  return dia.suficiente
    ? 'habito-historico__resultado habito-historico__resultado--ok'
    : 'habito-historico__resultado habito-historico__resultado--falta'
}

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'ok'; dias: HabitoHistoricoDia[] }

/** Histórico dos últimos 84 dias de um hábito: dia, minutos dedicados vs. meta e se bateu a meta. */
export function HabitoHistoricoSecao({ habitoId }: { habitoId: string }) {
  const tituloId = useId()
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' })
  const [tentativa, setTentativa] = useState(0)

  const recarregar = useCallback(() => setTentativa((n) => n + 1), [])

  useEffect(() => {
    let ativo = true
    setEstado({ tipo: 'carregando' })
    buscarHistoricoHabito(habitoId)
      .then((dias) => {
        if (ativo) {
          setEstado({ tipo: 'ok', dias })
        }
      })
      .catch((error: unknown) => {
        if (ativo) {
          setEstado({
            tipo: 'erro',
            mensagem: error instanceof Error ? error.message : 'Não foi possível carregar o histórico.',
          })
        }
      })
    return () => {
      ativo = false
    }
  }, [habitoId, tentativa])

  return (
    <section className="habito-historico" aria-labelledby={tituloId}>
      <h3 id={tituloId} className="habito-historico__titulo">
        Histórico
      </h3>
      {estado.tipo === 'carregando' ? (
        <p className="plano-dia-meta" role="status">
          Carregando histórico…
        </p>
      ) : null}
      {estado.tipo === 'erro' ? (
        <div className="habito-historico__erro">
          <InlineAlert>
            {estado.mensagem === 'Hábito não encontrado'
              ? estado.mensagem
              : `Não foi possível carregar o histórico. ${mensagemExtra(estado.mensagem)}`.trim()}
          </InlineAlert>
          <button type="button" className="btn btn--secondary btn--sm" onClick={recarregar}>
            Tentar de novo
          </button>
        </div>
      ) : null}
      {estado.tipo === 'ok' && estado.dias.length === 0 ? (
        <p className="habito-historico__vazio">Nenhum cumprimento nos últimos 84 dias.</p>
      ) : null}
      {estado.tipo === 'ok' && estado.dias.length > 0 ? (
        <ul className="habito-historico__lista">
          {estado.dias.map((dia) => (
            <li key={dia.data} className="habito-historico__item">
              <span className="habito-historico__data">
                {formatarDiaSemanaCurto(dia.data)}, {formatarDataCurtaPtBr(dia.data)}
              </span>
              <span className="habito-historico__tempo">
                {semTempoRegistrado(dia) ? '—' : `${dia.minutosDedicados} / ${dia.metaMinutos} min`}
              </span>
              <span className={classeResultado(dia)}>{rotuloResultado(dia)}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  )
}

/** Evita repetir a frase padrão quando o cliente já usou o fallback. */
function mensagemExtra(mensagem: string): string {
  return mensagem === 'Não foi possível carregar o histórico.' ? '' : mensagem
}
