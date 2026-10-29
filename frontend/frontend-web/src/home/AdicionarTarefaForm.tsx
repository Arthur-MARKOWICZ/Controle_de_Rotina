import { useMemo, useState, type FormEvent } from 'react'

import { formatarCapacidadeMinutos } from '../planner/data'
import type { TarefaResponse } from '../tarefa/api/tarefaApi'
import { formatarPrazoRelativo } from './justificativa'
import { labelPrioridade } from './labels'

type AdicionarTarefaFormProps = {
  elegiveis: TarefaResponse[]
  tarefaSelecionadaId: string
  carregandoElegiveis: boolean
  adicionando: boolean
  onSelecionar: (tarefaId: string) => void
  onSubmit: (event: FormEvent) => void
}

function textoBusca(tarefa: TarefaResponse): string {
  return `${tarefa.titulo} ${tarefa.prazo} ${tarefa.duracaoMinutos} ${labelPrioridade(tarefa.prioridade)}`.toLowerCase()
}

export function AdicionarTarefaForm({
  elegiveis,
  tarefaSelecionadaId,
  carregandoElegiveis,
  adicionando,
  onSelecionar,
  onSubmit,
}: AdicionarTarefaFormProps) {
  const [filtro, setFiltro] = useState('')

  const filtradas = useMemo(() => {
    const termo = filtro.trim().toLowerCase()
    if (!termo) {
      return elegiveis
    }
    return elegiveis.filter((tarefa) => textoBusca(tarefa).includes(termo))
  }, [elegiveis, filtro])

  return (
    <form className="plano-adicionar" onSubmit={onSubmit}>
      <h3>Adicionar tarefa</h3>
      {carregandoElegiveis ? (
        <p className="plano-dia-meta">Carregando tarefas elegíveis…</p>
      ) : elegiveis.length === 0 ? (
        <p className="plano-dia-meta">Nenhuma tarefa elegível fora do plano.</p>
      ) : (
        <>
          <label className="field" htmlFor="plano-tarefa-busca">
            Buscar tarefa
            <input
              id="plano-tarefa-busca"
              type="search"
              value={filtro}
              onChange={(event) => setFiltro(event.target.value)}
              placeholder="Título, prazo ou duração…"
              autoComplete="off"
            />
          </label>

          {filtradas.length === 0 ? (
            <p className="plano-dia-meta" role="status">
              Nenhuma tarefa corresponde à busca.
            </p>
          ) : (
            <ul className="plano-adicionar-lista" role="listbox" aria-label="Tarefas elegíveis">
              {filtradas.map((tarefa) => {
                const selecionada = tarefaSelecionadaId === tarefa.id
                return (
                  <li key={tarefa.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={selecionada}
                      className={
                        selecionada
                          ? 'plano-adicionar-opcao plano-adicionar-opcao--ativa'
                          : 'plano-adicionar-opcao'
                      }
                      onClick={() => onSelecionar(tarefa.id)}
                    >
                      <span className="plano-adicionar-opcao__titulo">{tarefa.titulo}</span>
                      <span className="plano-adicionar-opcao__meta">
                        {formatarPrazoRelativo(tarefa.prazo)}
                        {' · '}
                        {formatarCapacidadeMinutos(tarefa.duracaoMinutos)}
                        {' · '}
                        {labelPrioridade(tarefa.prioridade)}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}

          <button
            type="submit"
            className="btn btn--primary"
            disabled={adicionando || !tarefaSelecionadaId}
            aria-busy={adicionando}
          >
            {adicionando ? 'Adicionando…' : 'Adicionar ao plano'}
          </button>
        </>
      )}
    </form>
  )
}
