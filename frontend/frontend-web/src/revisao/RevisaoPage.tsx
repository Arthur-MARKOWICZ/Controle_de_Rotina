import { useState } from 'react'

import { PageHeader } from '../layout/PageHeader'
import { PassoDecidir } from './PassoDecidir'
import { PassoPendencias } from './PassoPendencias'
import { PassoPlano } from './PassoPlano'
import { PassoSemana } from './PassoSemana'
import { RevisaoConclusao } from './RevisaoConclusao'
import type { DesfechoPlano } from './tipos'

const PASSOS = ['Sua semana', 'Atrasadas e em espera', 'Decidir', 'Plano da próxima semana'] as const
const ULTIMO = PASSOS.length - 1

type Props = {
  /** Data civil de hoje em BRT; injetável para teste. */
  hojeIso?: string
}

export function RevisaoPage({ hojeIso }: Props) {
  const [passo, setPasso] = useState(0)
  const [concluida, setConcluida] = useState(false)
  const [tarefasTratadas, setTarefasTratadas] = useState(0)
  const [itensDecididos, setItensDecididos] = useState(0)
  const [desfecho, setDesfecho] = useState<DesfechoPlano>('NAO_TRATADO')

  function concluir(final: DesfechoPlano) {
    setDesfecho(final)
    setConcluida(true)
  }

  function avancar() {
    if (passo === ULTIMO) {
      concluir('NAO_TRATADO')
      return
    }
    setPasso((p) => p + 1)
  }

  return (
    <main className="revisao-page">
      <PageHeader
        title="Revisão da semana"
        description="Uns minutos para fechar a semana e sair com o plano da próxima."
      />

      {concluida ? (
        <RevisaoConclusao
          tarefasTratadas={tarefasTratadas}
          itensDecididos={itensDecididos}
          desfecho={desfecho}
        />
      ) : (
        <section className="revisao-passo" aria-labelledby="revisao-passo-titulo">
          <p className="revisao-passo__progresso">
            Passo {passo + 1} de {PASSOS.length}
          </p>
          <h2 id="revisao-passo-titulo" className="revisao-passo__titulo">
            {PASSOS[passo]}
          </h2>

          <div className="revisao-passo__corpo">
            {passo === 0 ? <PassoSemana key="semana" /> : null}
            {passo === 1 ? (
              <PassoPendencias key="pendencias" onTratou={() => setTarefasTratadas((n) => n + 1)} />
            ) : null}
            {passo === 2 ? (
              <PassoDecidir key="decidir" onTratou={() => setItensDecididos((n) => n + 1)} />
            ) : null}
            {passo === 3 ? <PassoPlano key="plano" hojeIso={hojeIso} onDesfecho={concluir} /> : null}
          </div>

          <nav className="revisao-passo__nav" aria-label="Navegação da revisão">
            {passo > 0 ? (
              <button type="button" className="btn btn--secondary" onClick={() => setPasso((p) => p - 1)}>
                Voltar
              </button>
            ) : null}
            <button type="button" className="btn btn--ghost" onClick={avancar}>
              Pular
            </button>
            {passo < ULTIMO ? (
              <button type="button" className="btn btn--primary" onClick={avancar}>
                Continuar
              </button>
            ) : null}
          </nav>
        </section>
      )}
    </main>
  )
}
