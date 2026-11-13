import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'

import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { esperasIrresolvidas, rotuloEspera } from '../pendencia/api/espera'
import {
  atualizarTarefa,
  excluirEsperaTarefa,
  listarTarefas,
  type TarefaResponse,
} from '../tarefa/api/tarefaApi'
import { mensagemErro } from './mensagemErro'

type Props = {
  onTratou: () => void
}

type Listas = {
  atrasadas: TarefaResponse[]
  emEspera: TarefaResponse[]
}

/** Fim do dia escolhido em BRT (UTC−3 fixo, sem horário de verão). */
function fimDoDiaBrt(dataIso: string): string {
  return new Date(`${dataIso}T23:59:00-03:00`).toISOString()
}

function origemHerdada(tarefa: TarefaResponse): string {
  if (tarefa.parentId) {
    return tarefa.parentTitulo ? `Espera herdada da tarefa-pai “${tarefa.parentTitulo}”` : 'Espera herdada da tarefa-pai'
  }
  return tarefa.projetoNome ? `Espera herdada do projeto “${tarefa.projetoNome}”` : 'Espera herdada do projeto'
}

/** Passo 2: tarefas atrasadas e tarefas bloqueadas por espera, com ações rápidas. */
export function PassoPendencias({ onTratou }: Props) {
  const { confirm, dialog } = useConfirmDialog()
  const [listas, setListas] = useState<Listas | null>(null)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [recarregar, setRecarregar] = useState(0)
  const [reagendando, setReagendando] = useState<string | null>(null)
  const [novaData, setNovaData] = useState('')
  const [ocupado, setOcupado] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true
    setErroCarga(null)
    Promise.all([
      listarTarefas({ status: ['ATRASADA'], size: 50 }),
      listarTarefas({ bloqueada: true, status: ['PENDENTE', 'EM_PROGRESSO', 'ATRASADA'], size: 50 }),
    ])
      .then(([atrasadas, bloqueadas]) => {
        if (!ativo) return
        setListas({
          atrasadas: atrasadas.content.filter((t) => !t.bloqueada),
          emEspera: bloqueadas.content,
        })
      })
      .catch((error: unknown) => {
        if (ativo) setErroCarga(mensagemErro(error, 'Não foi possível carregar as tarefas.'))
      })
    return () => {
      ativo = false
    }
  }, [recarregar])

  async function executar(id: string, acao: () => Promise<unknown>, falha: string) {
    setOcupado(id)
    setErroAcao(null)
    try {
      await acao()
      onTratou()
      setReagendando(null)
      setRecarregar((n) => n + 1)
    } catch (error) {
      setErroAcao(mensagemErro(error, falha))
      if (error instanceof ApiError && error.status === 404) {
        // Removida em outra aba: some da lista sem esperar o usuário navegar de novo.
        setReagendando(null)
        setRecarregar((n) => n + 1)
      }
    } finally {
      setOcupado(null)
    }
  }

  async function descartar(tarefa: TarefaResponse) {
    const ok = await confirm({
      title: 'Descartar tarefa?',
      message: `“${tarefa.titulo}” será cancelada e sai do planejamento.`,
      confirmLabel: 'Descartar',
    })
    if (!ok) return
    await executar(
      tarefa.id,
      () => atualizarTarefa(tarefa.id, { status: 'CANCELADA' }),
      'Não foi possível descartar a tarefa.',
    )
  }

  async function removerEspera(tarefa: TarefaResponse, esperaId: string, alvo: string) {
    const ok = await confirm({
      title: 'Remover espera?',
      message: `“${tarefa.titulo}” deixa de esperar “${alvo}”.`,
      confirmLabel: 'Remover',
    })
    if (!ok) return
    await executar(
      tarefa.id,
      () => excluirEsperaTarefa(tarefa.id, esperaId),
      'Não foi possível remover a espera.',
    )
  }

  if (erroCarga) {
    return (
      <div className="revisao-passo__estado">
        <InlineAlert>{erroCarga}</InlineAlert>
        <button type="button" className="btn btn--secondary" onClick={() => setRecarregar((n) => n + 1)}>
          Tentar de novo
        </button>
      </div>
    )
  }

  if (!listas) {
    return <Skeleton variant="tarefas-lista" />
  }

  if (listas.atrasadas.length === 0 && listas.emEspera.length === 0) {
    return (
      <>
        {erroAcao ? <InlineAlert>{erroAcao}</InlineAlert> : null}
        <p className="revisao-passo__vazio" role="status">
          Nada atrasado nem em espera. Pode seguir.
        </p>
      </>
    )
  }

  return (
    <div className="revisao-grupos">
      {dialog}
      {erroAcao ? <InlineAlert>{erroAcao}</InlineAlert> : null}

      <section aria-labelledby="revisao-atrasadas">
        <h3 id="revisao-atrasadas" className="revisao-grupo__titulo">
          Atrasadas ({listas.atrasadas.length})
        </h3>
        {listas.atrasadas.length === 0 ? (
          <p className="revisao-passo__vazio">Nenhuma tarefa atrasada.</p>
        ) : (
          <ul className="revisao-lista">
            {listas.atrasadas.map((tarefa) => (
              <li key={tarefa.id} className="revisao-item">
                <Link to={`/tarefas/${tarefa.id}`} className="revisao-item__titulo">
                  {tarefa.titulo}
                </Link>
                {reagendando === tarefa.id ? (
                  <form
                    className="revisao-item__acoes"
                    onSubmit={(e) => {
                      e.preventDefault()
                      if (!novaData) return
                      void executar(
                        tarefa.id,
                        () => atualizarTarefa(tarefa.id, { prazo: fimDoDiaBrt(novaData) }),
                        'Não foi possível reagendar a tarefa.',
                      )
                    }}
                  >
                    <label className="visually-hidden" htmlFor={`prazo-${tarefa.id}`}>
                      Novo prazo de {tarefa.titulo}
                    </label>
                    <input
                      id={`prazo-${tarefa.id}`}
                      type="date"
                      value={novaData}
                      onChange={(e) => setNovaData(e.target.value)}
                      required
                    />
                    <button
                      type="submit"
                      className="btn btn--primary"
                      disabled={ocupado === tarefa.id}
                      aria-busy={ocupado === tarefa.id}
                    >
                      Salvar prazo
                    </button>
                    <button type="button" className="btn btn--secondary" onClick={() => setReagendando(null)}>
                      Cancelar
                    </button>
                  </form>
                ) : (
                  <div className="revisao-item__acoes">
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => {
                        setReagendando(tarefa.id)
                        setNovaData('')
                      }}
                      disabled={ocupado === tarefa.id}
                    >
                      Reagendar
                    </button>
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => void descartar(tarefa)}
                      disabled={ocupado === tarefa.id}
                    >
                      Descartar
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="revisao-espera">
        <h3 id="revisao-espera" className="revisao-grupo__titulo">
          Em espera ({listas.emEspera.length})
        </h3>
        {listas.emEspera.length === 0 ? (
          <p className="revisao-passo__vazio">Nenhuma tarefa em espera.</p>
        ) : (
          <ul className="revisao-lista">
            {listas.emEspera.map((tarefa) => {
              const proprias = esperasIrresolvidas(tarefa.esperas)
              return (
                <li key={tarefa.id} className="revisao-item">
                  <Link to={`/tarefas/${tarefa.id}`} className="revisao-item__titulo">
                    {tarefa.titulo}
                  </Link>
                  <span className="revisao-item__meta">
                    {proprias.length > 0 ? rotuloEspera(tarefa.esperas) : origemHerdada(tarefa)}
                  </span>
                  <div className="revisao-item__acoes">
                    {proprias.map((espera) => {
                      const alvo = espera.alvoTitulo ?? 'item'
                      return (
                        <button
                          key={espera.id}
                          type="button"
                          className="btn btn--secondary"
                          onClick={() => void removerEspera(tarefa, espera.id, alvo)}
                          disabled={ocupado === tarefa.id}
                        >
                          Remover espera de “{alvo}”
                        </button>
                      )
                    })}
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => void descartar(tarefa)}
                      disabled={ocupado === tarefa.id}
                    >
                      Descartar
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
