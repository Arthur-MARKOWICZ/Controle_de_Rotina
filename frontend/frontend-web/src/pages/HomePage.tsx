import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { useAuth } from '../auth/session/AuthContext'
import {
  consultarCapacidadeResolvida,
  type CapacidadeResolvidaResponse,
} from '../capacidade/api/capacidadeResolvidaApi'
import {
  adicionarBloco,
  aprovarPlano,
  buscarPlano,
  gerarPlano,
  removerBloco,
  type PlanoResponse,
  type StatusPlano,
} from '../planner/api/planoApi'
import {
  dataHojeIso,
  filtrarElegiveisForaDoPlano,
  formatarCapacidadeMinutos,
  minutosDisponiveis,
  minutosUsados,
  statusElegiveisParaPlano,
} from '../planner/data'
import { listarTarefas, type TarefaResponse } from '../tarefa/api/tarefaApi'

const STATUS_PLANO_LABEL: Record<StatusPlano, string> = {
  RASCUNHO: 'Rascunho',
  APROVADO: 'Aprovado',
}

function capacidadeDoDia(
  resolvidas: CapacidadeResolvidaResponse[],
  data: string,
): CapacidadeResolvidaResponse | undefined {
  return resolvidas.find((item) => item.data === data)
}

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

export function HomePage() {
  const { logout } = useAuth()
  const [plano, setPlano] = useState<PlanoResponse | null>(null)
  const [capacidade, setCapacidade] = useState<CapacidadeResolvidaResponse | undefined>()
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoErro, setAcaoErro] = useState<string | null>(null)
  const [aprovando, setAprovando] = useState(false)
  const [removendoBlocoId, setRemovendoBlocoId] = useState<string | null>(null)
  const [elegiveis, setElegiveis] = useState<TarefaResponse[]>([])
  const [tarefaSelecionadaId, setTarefaSelecionadaId] = useState('')
  const [adicionando, setAdicionando] = useState(false)
  const [carregandoElegiveis, setCarregandoElegiveis] = useState(false)
  const [gerando, setGerando] = useState(false)

  const ehRascunho = plano?.status === 'RASCUNHO'
  const podeGerar = !plano || ehRascunho

  useEffect(() => {
    let cancelado = false
    const data = dataHojeIso()

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const [planoDia, resolvidas] = await Promise.all([
          buscarPlano({ data }),
          consultarCapacidadeResolvida({ dataDe: data, dataAte: data }),
        ])
        if (cancelado) {
          return
        }
        setPlano(planoDia)
        setCapacidade(capacidadeDoDia(resolvidas, data))
      } catch (error) {
        if (cancelado) {
          return
        }
        setErro(mensagemErro(error, 'Não foi possível carregar o plano.'))
        setPlano(null)
      } finally {
        if (!cancelado) {
          setCarregando(false)
        }
      }
    }

    void carregar()
    return () => {
      cancelado = true
    }
  }, [])

  useEffect(() => {
    if (!ehRascunho || !plano) {
      setElegiveis([])
      setTarefaSelecionadaId('')
      return
    }

    let cancelado = false
    const idsNoPlano = plano.blocos.map((bloco) => bloco.tarefa.id)

    async function carregarElegiveis() {
      setCarregandoElegiveis(true)
      try {
        const page = await listarTarefas({
          status: statusElegiveisParaPlano(),
          page: 0,
          size: 100,
          sort: 'prazo',
          direction: 'asc',
        })
        if (cancelado) {
          return
        }
        const disponiveis = filtrarElegiveisForaDoPlano(page.content, idsNoPlano)
        setElegiveis(disponiveis)
        setTarefaSelecionadaId((atual) =>
          disponiveis.some((tarefa) => tarefa.id === atual) ? atual : '',
        )
      } catch {
        if (!cancelado) {
          setElegiveis([])
        }
      } finally {
        if (!cancelado) {
          setCarregandoElegiveis(false)
        }
      }
    }

    void carregarElegiveis()
    return () => {
      cancelado = true
    }
  }, [ehRascunho, plano])

  async function handleGerar() {
    if (!podeGerar) {
      return
    }
    setGerando(true)
    setAcaoErro(null)
    setErro(null)
    try {
      const gerado = await gerarPlano(dataHojeIso())
      setPlano(gerado)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível gerar o plano.'))
    } finally {
      setGerando(false)
    }
  }

  async function handleAprovar() {
    if (!plano || !ehRascunho) {
      return
    }
    setAprovando(true)
    setAcaoErro(null)
    try {
      const atualizado = await aprovarPlano(plano.id)
      setPlano(atualizado)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível aprovar o plano.'))
    } finally {
      setAprovando(false)
    }
  }

  async function handleRemover(blocoId: string) {
    if (!plano || !ehRascunho) {
      return
    }
    setRemovendoBlocoId(blocoId)
    setAcaoErro(null)
    try {
      await removerBloco(plano.id, blocoId)
      const atualizado = await buscarPlano({ data: plano.data })
      setPlano(atualizado)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível remover o bloco.'))
    } finally {
      setRemovendoBlocoId(null)
    }
  }

  async function handleAdicionar(event: FormEvent) {
    event.preventDefault()
    if (!plano || !ehRascunho || !tarefaSelecionadaId) {
      return
    }
    setAdicionando(true)
    setAcaoErro(null)
    try {
      const atualizado = await adicionarBloco(plano.id, tarefaSelecionadaId)
      setPlano(atualizado)
      setTarefaSelecionadaId('')
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível adicionar a tarefa ao plano.'))
    } finally {
      setAdicionando(false)
    }
  }

  const usados = plano ? minutosUsados(plano.blocos) : 0
  const disponiveis = minutosDisponiveis(capacidade?.horas, capacidade?.origem)
  const resumoCapacidade = `${formatarCapacidadeMinutos(usados)} / ${formatarCapacidadeMinutos(disponiveis)}`

  return (
    <main className="home-page">
      <header className="home-header">
        <h1>Controle de Rotina</h1>
        <div className="home-actions">
          <Link to="/tarefas">Tarefas</Link>
          <Link to="/capacidade">Capacidade</Link>
          <Link to="/perfil">Meu perfil</Link>
          <button type="button" onClick={() => void logout()}>
            Sair
          </button>
        </div>
      </header>

      <section className="plano-dia" aria-labelledby="plano-dia-titulo">
        <div className="plano-dia-cabecalho">
          <h2 id="plano-dia-titulo">Plano de hoje</h2>
          {plano ? (
            <span
              className={`status-badge status-plano-${plano.status.toLowerCase()}`}
            >
              {STATUS_PLANO_LABEL[plano.status]}
            </span>
          ) : null}
        </div>

        {carregando ? (
          <p className="plano-dia-meta" aria-busy="true">
            Carregando plano…
          </p>
        ) : null}

        {erro ? (
          <p className="plano-dia-erro" role="alert">
            {erro}
          </p>
        ) : null}

        {acaoErro ? (
          <p className="plano-dia-erro" role="alert">
            {acaoErro}
          </p>
        ) : null}

        {!carregando && !erro && !plano ? (
          <div className="plano-dia-vazio-bloco">
            <p className="plano-dia-vazio" role="status">
              Ainda não há plano para hoje.
            </p>
            <div className="plano-dia-acoes">
              <button
                type="button"
                className="button-link"
                onClick={() => void handleGerar()}
                disabled={gerando}
              >
                {gerando ? 'Gerando…' : 'Gerar plano'}
              </button>
            </div>
          </div>
        ) : null}

        {!carregando && !erro && plano ? (
          <>
            <p className="plano-dia-capacidade">
              Capacidade: <strong>{resumoCapacidade}</strong>
              {plano.versao > 0 ? (
                <span className="plano-dia-versao"> · versão {plano.versao}</span>
              ) : null}
            </p>

            {plano.mensagem ? (
              <p
                className={
                  plano.temConflito ? 'plano-dia-banner conflito' : 'plano-dia-banner'
                }
                role="status"
                aria-label={plano.temConflito ? 'Conflito no plano' : 'Mensagem do plano'}
              >
                {plano.mensagem}
              </p>
            ) : null}

            {podeGerar || ehRascunho ? (
              <div className="plano-dia-acoes">
                {podeGerar ? (
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={() => void handleGerar()}
                    disabled={gerando || aprovando}
                  >
                    {gerando ? 'Gerando…' : plano ? 'Gerar nova versão' : 'Gerar plano'}
                  </button>
                ) : null}
                {ehRascunho ? (
                  <button
                    type="button"
                    className="button-link"
                    onClick={() => void handleAprovar()}
                    disabled={aprovando || gerando}
                  >
                    {aprovando ? 'Aprovando…' : 'Aprovar plano'}
                  </button>
                ) : null}
              </div>
            ) : null}

            {plano.blocos.length === 0 ? (
              <p className="plano-dia-vazio" role="status">
                Nenhum bloco neste plano.
              </p>
            ) : (
              <ol className="plano-blocos">
                {plano.blocos.map((bloco) => (
                  <li key={bloco.id} className="plano-bloco">
                    <span className="plano-bloco-ordem">{bloco.ordem}</span>
                    <div className="plano-bloco-corpo">
                      <p className="plano-bloco-titulo">
                        <Link to={`/tarefas/${bloco.tarefa.id}`}>
                          {bloco.tarefa.titulo}
                        </Link>
                      </p>
                      <p className="plano-bloco-meta">
                        {formatarCapacidadeMinutos(bloco.duracaoMinutos)}
                        {' · '}
                        {bloco.tarefa.prioridade}
                      </p>
                    </div>
                    {ehRascunho ? (
                      <button
                        type="button"
                        className="button-secondary plano-bloco-remover"
                        onClick={() => void handleRemover(bloco.id)}
                        disabled={removendoBlocoId === bloco.id}
                        aria-label={`Remover ${bloco.tarefa.titulo}`}
                      >
                        {removendoBlocoId === bloco.id ? 'Removendo…' : 'Remover'}
                      </button>
                    ) : null}
                  </li>
                ))}
              </ol>
            )}

            {ehRascunho ? (
              <form className="plano-adicionar" onSubmit={(event) => void handleAdicionar(event)}>
                <h3>Adicionar tarefa</h3>
                {carregandoElegiveis ? (
                  <p className="plano-dia-meta">Carregando tarefas elegíveis…</p>
                ) : elegiveis.length === 0 ? (
                  <p className="plano-dia-meta">Nenhuma tarefa elegível fora do plano.</p>
                ) : (
                  <>
                    <label htmlFor="plano-tarefa-elegivel">
                      Tarefa
                      <select
                        id="plano-tarefa-elegivel"
                        value={tarefaSelecionadaId}
                        onChange={(event) => setTarefaSelecionadaId(event.target.value)}
                        required
                      >
                        <option value="">Selecione…</option>
                        {elegiveis.map((tarefa) => (
                          <option key={tarefa.id} value={tarefa.id}>
                            {tarefa.titulo} ({tarefa.duracaoHoras}h)
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="submit"
                      className="button-link"
                      disabled={adicionando || !tarefaSelecionadaId}
                    >
                      {adicionando ? 'Adicionando…' : 'Adicionar ao plano'}
                    </button>
                  </>
                )}
              </form>
            ) : null}
          </>
        ) : null}
      </section>
    </main>
  )
}
