import { useEffect, useState } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import {
  consultarHistoricoSemana,
  type HistoricoResumoResponse,
} from '../historico/api/historicoApi'
import { formatarMinutosTrabalhados } from '../historico/formatacao'
import { mensagemErro } from './mensagemErro'

function semAtividade(resumo: HistoricoResumoResponse): boolean {
  return (
    resumo.tarefasFinalizadas === 0 &&
    resumo.blocosConcluidos === 0 &&
    resumo.minutosTrabalhados === 0 &&
    resumo.xpGanho === 0
  )
}

/** Passo 1: o que foi feito na semana corrente (BRT, resolvida pelo backend). */
export function PassoSemana() {
  const [resumo, setResumo] = useState<HistoricoResumoResponse | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    let ativo = true
    setErro(null)
    setResumo(null)
    consultarHistoricoSemana()
      .then((data) => {
        if (ativo) setResumo(data)
      })
      .catch((error: unknown) => {
        if (ativo) setErro(mensagemErro(error, 'Não foi possível carregar o resumo da semana.'))
      })
    return () => {
      ativo = false
    }
  }, [tentativa])

  if (erro) {
    return (
      <div className="revisao-passo__estado">
        <InlineAlert>{erro}</InlineAlert>
        <button type="button" className="btn btn--secondary" onClick={() => setTentativa((n) => n + 1)}>
          Tentar de novo
        </button>
      </div>
    )
  }

  if (!resumo) {
    return <Skeleton variant="historico-metricas" />
  }

  if (semAtividade(resumo)) {
    return (
      <p className="revisao-passo__vazio" role="status">
        Nenhuma atividade registrada nesta semana. Tudo bem — a revisão serve justamente para
        recomeçar.
      </p>
    )
  }

  return (
    <dl className="revisao-numeros">
      <div>
        <dt>Tarefas finalizadas</dt>
        <dd data-testid="semana-tarefas">{resumo.tarefasFinalizadas}</dd>
      </div>
      <div>
        <dt>Blocos concluídos</dt>
        <dd>{resumo.blocosConcluidos}</dd>
      </div>
      <div>
        <dt>Tempo trabalhado</dt>
        <dd>{formatarMinutosTrabalhados(resumo.minutosTrabalhados)}</dd>
      </div>
      <div>
        <dt>XP ganho</dt>
        <dd>{resumo.xpGanho}</dd>
      </div>
    </dl>
  )
}
