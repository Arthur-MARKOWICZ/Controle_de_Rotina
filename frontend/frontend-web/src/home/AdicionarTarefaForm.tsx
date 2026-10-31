import { useMemo, useState, type FormEvent, type KeyboardEvent } from 'react'

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

const LISTBOX_ID = 'plano-tarefa-opcoes'

function idOpcao(tarefaId: string): string {
  return `plano-tarefa-opcao-${tarefaId}`
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

  const selecionadaVisivel = filtradas.some((tarefa) => tarefa.id === tarefaSelecionadaId)

  function handleTecla(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return
    }
    event.preventDefault()
    if (filtradas.length === 0) {
      return
    }
    const atual = filtradas.findIndex((tarefa) => tarefa.id === tarefaSelecionadaId)
    const passo = event.key === 'ArrowDown' ? 1 : -1
    const proximo =
      atual === -1
        ? passo === 1
          ? 0
          : filtradas.length - 1
        : (atual + passo + filtradas.length) % filtradas.length
    onSelecionar(filtradas[proximo].id)
  }

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
              role="combobox"
              aria-expanded={filtradas.length > 0}
              aria-controls={LISTBOX_ID}
              aria-autocomplete="list"
              aria-activedescendant={selecionadaVisivel ? idOpcao(tarefaSelecionadaId) : undefined}
              value={filtro}
              onChange={(event) => setFiltro(event.target.value)}
              onKeyDown={handleTecla}
              placeholder="Título, prazo ou duração…"
              autoComplete="off"
            />
          </label>

          {filtradas.length === 0 ? (
            <p className="plano-dia-meta" role="status">
              Nenhuma tarefa corresponde à busca.
            </p>
          ) : (
            <ul
              id={LISTBOX_ID}
              className="plano-adicionar-lista"
              role="listbox"
              aria-label="Tarefas elegíveis"
            >
              {filtradas.map((tarefa) => {
                const selecionada = tarefaSelecionadaId === tarefa.id
                return (
                  <li key={tarefa.id}>
                    <button
                      id={idOpcao(tarefa.id)}
                      type="button"
                      role="option"
                      aria-selected={selecionada}
                      tabIndex={-1}
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
