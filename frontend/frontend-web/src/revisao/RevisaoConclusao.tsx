import { Link } from 'react-router-dom'

import type { ResumoSessaoRevisao } from './tipos'

const TEXTO_DESFECHO = {
  APROVADO: 'Plano da semana aprovado.',
  RECUSADO: 'Plano não aprovado — você escolheu não aprovar agora.',
  NAO_TRATADO: 'O plano da próxima semana não foi gerado nesta revisão.',
} as const

function plural(n: number, singular: string, pluralTexto: string): string {
  return `${n} ${n === 1 ? singular : pluralTexto}`
}

export function RevisaoConclusao({ tarefasTratadas, itensDecididos, desfecho }: ResumoSessaoRevisao) {
  return (
    <section className="revisao-conclusao" aria-labelledby="revisao-conclusao-titulo">
      <h2 id="revisao-conclusao-titulo">Revisão concluída</h2>
      <ul className="revisao-conclusao__lista">
        <li>{plural(tarefasTratadas, 'tarefa tratada', 'tarefas tratadas')}</li>
        <li>{plural(itensDecididos, 'item decidido', 'itens decididos')}</li>
        <li data-testid="revisao-desfecho">{TEXTO_DESFECHO[desfecho]}</li>
      </ul>
      <div className="revisao-plano__acoes">
        <Link to="/plano" className="btn btn--primary">
          Ir para o plano
        </Link>
        <Link to="/tarefas" className="btn btn--secondary">
          Ver tarefas
        </Link>
      </div>
    </section>
  )
}
