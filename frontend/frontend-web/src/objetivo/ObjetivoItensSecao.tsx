import { useId } from 'react'
import { Link } from 'react-router-dom'

import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { STATUS_TAREFA_LABEL } from '../home/labels'
import type { ObjetivoItens } from './api/objetivoApi'

type Habito = ObjetivoItens['habitos'][number]
type Projeto = ObjetivoItens['projetos'][number]

const STATUS_HABITO_LABEL: Record<Habito['status'], string> = {
  ATIVO: 'Ativo',
  PAUSADO: 'Pausado',
}

const STATUS_PROJETO_LABEL: Record<Projeto['status'], string> = {
  ATIVO: 'Ativo',
  ARQUIVADO: 'Arquivado',
}

type Props = {
  itens: ObjetivoItens | null
  carregando: boolean
  erro: string | null
}

type LinhaProps = {
  to: string
  titulo: string
  status: string
  viaProjeto?: string | null
}

function Linha({ to, titulo, status, viaProjeto }: LinhaProps) {
  return (
    <li className="objetivo-item">
      <Link to={to} className="objetivo-item__titulo">
        {titulo}
      </Link>
      <span className="objetivo-item__status">{status}</span>
      {viaProjeto !== undefined ? (
        <span className="badge objetivo-item__via">
          {viaProjeto ? `via projeto ${viaProjeto}` : 'via projeto'}
        </span>
      ) : null}
    </li>
  )
}

/** Quantos itens o objetivo tem, somando tarefas, hábitos e projetos. */
export function totalItens(itens: ObjetivoItens | null): number {
  return itens ? itens.tarefas.length + itens.habitos.length + itens.projetos.length : 0
}

/** Itens ligados ao objetivo, agrupados em tarefas, hábitos e projetos. */
export function ObjetivoItensSecao({ itens, carregando, erro }: Props) {
  const tituloId = useId()
  const total = totalItens(itens)
  const vazio = !carregando && erro == null && itens != null && total === 0

  return (
    <section className="objetivo-itens" aria-labelledby={tituloId}>
      <h2 id={tituloId} className="objetivo-itens__titulo">
        Itens vinculados
      </h2>

      {carregando ? <Skeleton variant="tarefas-lista" /> : null}
      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {vazio ? (
        <div className="objetivos-empty" role="status">
          <p className="objetivos-empty__titulo">Nenhum item vinculado</p>
          <p>Vincule pelo formulário de uma tarefa, de um hábito ou de um projeto.</p>
        </div>
      ) : null}

      {!carregando && erro == null && itens != null && total > 0 ? (
        <>
          {itens.tarefas.length > 0 ? (
            <div className="objetivo-itens__grupo">
              <h3>Tarefas ({itens.tarefas.length})</h3>
              <ul className="objetivo-itens__lista">
                {itens.tarefas.map((tarefa) => (
                  <Linha
                    key={tarefa.id}
                    to={`/tarefas/${tarefa.id}`}
                    titulo={tarefa.titulo}
                    status={STATUS_TAREFA_LABEL[tarefa.status]}
                    viaProjeto={tarefa.origem === 'VIA_PROJETO' ? tarefa.projetoNome : undefined}
                  />
                ))}
              </ul>
            </div>
          ) : null}

          {itens.habitos.length > 0 ? (
            <div className="objetivo-itens__grupo">
              <h3>Hábitos ({itens.habitos.length})</h3>
              <ul className="objetivo-itens__lista">
                {itens.habitos.map((habito) => (
                  <Linha
                    key={habito.id}
                    to={`/habitos/${habito.id}`}
                    titulo={habito.titulo}
                    status={STATUS_HABITO_LABEL[habito.status]}
                  />
                ))}
              </ul>
            </div>
          ) : null}

          {itens.projetos.length > 0 ? (
            <div className="objetivo-itens__grupo">
              <h3>Projetos ({itens.projetos.length})</h3>
              <ul className="objetivo-itens__lista">
                {itens.projetos.map((projeto) => (
                  <Linha
                    key={projeto.id}
                    to={`/projetos/${projeto.id}`}
                    titulo={projeto.nome}
                    status={STATUS_PROJETO_LABEL[projeto.status]}
                  />
                ))}
              </ul>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
