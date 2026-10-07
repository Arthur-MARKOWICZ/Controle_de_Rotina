import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import {
  excluirTarefa,
  listarTarefas,
  restaurarTarefa,
  type PageTarefa,
  type SortCampo,
  type SortDirection,
} from '../api/tarefaApi'
import { formatarDataHora } from '../datetime'
import type { Prioridade, StatusTarefa } from '../validation/tarefaForm'

type VisibilidadeDelecao = 'ativas' | 'incluir' | 'somente'

const STATUS_OPCOES: StatusTarefa[] = [
  'PENDENTE',
  'EM_PROGRESSO',
  'FINALIZADA',
  'ATRASADA',
  'CANCELADA',
]

const PRIORIDADE_OPCOES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']

const STATUS_LABEL: Record<StatusTarefa, string> = {
  PENDENTE: 'Pendente',
  EM_PROGRESSO: 'Em progresso',
  FINALIZADA: 'Finalizada',
  ATRASADA: 'Atrasada',
  CANCELADA: 'Cancelada',
}

type FiltrosForm = {
  status: StatusTarefa[]
  prioridade: Prioridade[]
  prazoDe: string
  prazoAte: string
  duracaoDe: string
  duracaoAte: string
  criadoDe: string
  criadoAte: string
  atualizadoDe: string
  atualizadoAte: string
  visibilidade: VisibilidadeDelecao
  sort: SortCampo
  direction: SortDirection
  size: number
}

const FILTROS_INICIAIS: FiltrosForm = {
  status: [],
  prioridade: [],
  prazoDe: '',
  prazoAte: '',
  duracaoDe: '',
  duracaoAte: '',
  criadoDe: '',
  criadoAte: '',
  atualizadoDe: '',
  atualizadoAte: '',
  visibilidade: 'ativas',
  sort: 'prazo',
  direction: 'asc',
  size: 20,
}

function paramsDelecao(visibilidade: VisibilidadeDelecao): {
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
} {
  if (visibilidade === 'incluir') {
    return { incluirDeletadas: true, somenteDeletadas: false }
  }
  if (visibilidade === 'somente') {
    return { incluirDeletadas: false, somenteDeletadas: true }
  }
  return {}
}

function toggleValor<T extends string>(lista: T[], valor: T): T[] {
  return lista.includes(valor) ? lista.filter((item) => item !== valor) : [...lista, valor]
}

function paraInstantOuUndefined(local: string): string | undefined {
  if (!local) {
    return undefined
  }
  const date = new Date(local)
  if (Number.isNaN(date.getTime())) {
    return undefined
  }
  return date.toISOString()
}

function paraNumeroOuUndefined(valor: string): number | undefined {
  if (!valor) {
    return undefined
  }
  const n = Number(valor)
  return Number.isFinite(n) ? n : undefined
}

export function TarefasPage() {
  const [filtros, setFiltros] = useState<FiltrosForm>(FILTROS_INICIAIS)
  const [aplicados, setAplicados] = useState<FiltrosForm>(FILTROS_INICIAIS)
  const [page, setPage] = useState(0)
  const [resultado, setResultado] = useState<PageTarefa | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoId, setAcaoId] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)
  const [mostrarMaisFiltros, setMostrarMaisFiltros] = useState(false)

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarTarefas({
          status: aplicados.status.length ? aplicados.status : undefined,
          prioridade: aplicados.prioridade.length ? aplicados.prioridade : undefined,
          prazoDe: paraInstantOuUndefined(aplicados.prazoDe),
          prazoAte: paraInstantOuUndefined(aplicados.prazoAte),
          duracaoDe: paraNumeroOuUndefined(aplicados.duracaoDe),
          duracaoAte: paraNumeroOuUndefined(aplicados.duracaoAte),
          criadoDe: paraInstantOuUndefined(aplicados.criadoDe),
          criadoAte: paraInstantOuUndefined(aplicados.criadoAte),
          atualizadoDe: paraInstantOuUndefined(aplicados.atualizadoDe),
          atualizadoAte: paraInstantOuUndefined(aplicados.atualizadoAte),
          ...paramsDelecao(aplicados.visibilidade),
          page,
          size: aplicados.size,
          sort: aplicados.sort,
          direction: aplicados.direction,
        })
        if (ativo) {
          setResultado(data)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar as tarefas.')
          setResultado(null)
        }
      } finally {
        if (ativo) {
          setCarregando(false)
        }
      }
    }

    void carregar()
    return () => {
      ativo = false
    }
  }, [aplicados, page, recarregarEm])

  function handleFiltrar(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setAplicados(filtros)
  }

  function handleLimpar() {
    setFiltros(FILTROS_INICIAIS)
    setAplicados(FILTROS_INICIAIS)
    setPage(0)
  }

  async function handleExcluir(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await excluirTarefa(id)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível excluir a tarefa.',
      )
    } finally {
      setAcaoId(null)
    }
  }

  async function handleRestaurar(id: string) {
    setErro(null)
    setAcaoId(id)
    try {
      await restaurarTarefa(id)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível restaurar a tarefa.',
      )
    } finally {
      setAcaoId(null)
    }
  }

  const totalPages = resultado?.totalPages ?? 0

  return (
    <main className="tarefas-page">
      <header className="tarefas-header">
        <div>
          <h1>Tarefas</h1>
          <p className="tarefas-subtitle">Liste, filtre e ordene o que precisa ser planejado.</p>
        </div>
        <div className="tarefas-header-actions">
          <Link to="/tarefas/nova" className="button-link">
            Nova tarefa
          </Link>
          <Link to="/">Início</Link>
        </div>
      </header>

      <form className="tarefas-filtros" onSubmit={handleFiltrar}>
        <fieldset>
          <legend>Status</legend>
          <div className="chip-group">
            {STATUS_OPCOES.map((status) => (
              <label key={status} className="chip">
                <input
                  type="checkbox"
                  checked={filtros.status.includes(status)}
                  onChange={() =>
                    setFiltros((atual) => ({
                      ...atual,
                      status: toggleValor(atual.status, status),
                    }))
                  }
                />
                {STATUS_LABEL[status]}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend>Prioridade</legend>
          <div className="chip-group">
            {PRIORIDADE_OPCOES.map((prioridade) => (
              <label key={prioridade} className="chip">
                <input
                  type="checkbox"
                  checked={filtros.prioridade.includes(prioridade)}
                  onChange={() =>
                    setFiltros((atual) => ({
                      ...atual,
                      prioridade: toggleValor(atual.prioridade, prioridade),
                    }))
                  }
                />
                {prioridade}
              </label>
            ))}
          </div>
        </fieldset>

        <div className="filtros-actions">
          <button type="submit">Aplicar filtros</button>
          <button type="button" className="button-secondary" onClick={handleLimpar}>
            Limpar
          </button>
          <button
            type="button"
            className="button-secondary"
            aria-expanded={mostrarMaisFiltros}
            onClick={() => setMostrarMaisFiltros((atual) => !atual)}
          >
            {mostrarMaisFiltros ? 'Ocultar mais filtros' : 'Mostrar mais filtros'}
          </button>
        </div>

        {mostrarMaisFiltros ? (
          <div className="filtros-grid filtros-avancados">
            <label>
              Prazo de
              <input
                type="datetime-local"
                value={filtros.prazoDe}
                onChange={(e) => setFiltros((a) => ({ ...a, prazoDe: e.target.value }))}
              />
            </label>
            <label>
              Prazo até
              <input
                type="datetime-local"
                value={filtros.prazoAte}
                onChange={(e) => setFiltros((a) => ({ ...a, prazoAte: e.target.value }))}
              />
            </label>
            <label>
              Duração de (h)
              <input
                type="number"
                min={1}
                value={filtros.duracaoDe}
                onChange={(e) => setFiltros((a) => ({ ...a, duracaoDe: e.target.value }))}
              />
            </label>
            <label>
              Duração até (h)
              <input
                type="number"
                min={1}
                value={filtros.duracaoAte}
                onChange={(e) => setFiltros((a) => ({ ...a, duracaoAte: e.target.value }))}
              />
            </label>
            <label>
              Criado de
              <input
                type="datetime-local"
                value={filtros.criadoDe}
                onChange={(e) => setFiltros((a) => ({ ...a, criadoDe: e.target.value }))}
              />
            </label>
            <label>
              Criado até
              <input
                type="datetime-local"
                value={filtros.criadoAte}
                onChange={(e) => setFiltros((a) => ({ ...a, criadoAte: e.target.value }))}
              />
            </label>
            <label>
              Atualizado de
              <input
                type="datetime-local"
                value={filtros.atualizadoDe}
                onChange={(e) => setFiltros((a) => ({ ...a, atualizadoDe: e.target.value }))}
              />
            </label>
            <label>
              Atualizado até
              <input
                type="datetime-local"
                value={filtros.atualizadoAte}
                onChange={(e) => setFiltros((a) => ({ ...a, atualizadoAte: e.target.value }))}
              />
            </label>
            <label>
              Ordenar por
              <select
                value={filtros.sort}
                onChange={(e) =>
                  setFiltros((a) => ({ ...a, sort: e.target.value as SortCampo }))
                }
              >
                <option value="prazo">Prazo</option>
                <option value="duracao">Duração</option>
                <option value="prioridade">Prioridade</option>
              </select>
            </label>
            <label>
              Direção
              <select
                value={filtros.direction}
                onChange={(e) =>
                  setFiltros((a) => ({ ...a, direction: e.target.value as SortDirection }))
                }
              >
                <option value="asc">Ascendente</option>
                <option value="desc">Descendente</option>
              </select>
            </label>
            <label>
              Por página
              <select
                value={filtros.size}
                onChange={(e) => setFiltros((a) => ({ ...a, size: Number(e.target.value) }))}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
            </label>
            <label>
              Excluídas
              <select
                value={filtros.visibilidade}
                onChange={(e) =>
                  setFiltros((a) => ({
                    ...a,
                    visibilidade: e.target.value as VisibilidadeDelecao,
                  }))
                }
              >
                <option value="ativas">Somente ativas</option>
                <option value="incluir">Incluir excluídas</option>
                <option value="somente">Somente excluídas</option>
              </select>
            </label>
          </div>
        ) : null}
      </form>

      {erro ? (
        <p className="auth-error" role="alert">
          {erro}
        </p>
      ) : null}

      {carregando ? <p>Carregando tarefas…</p> : null}

      {!carregando && resultado ? (
        <>
          <p className="tarefas-meta">
            {resultado.totalElements} tarefa{resultado.totalElements === 1 ? '' : 's'}
          </p>

          {resultado.content.length === 0 ? (
            <p>Nenhuma tarefa encontrada.</p>
          ) : (
            <div className="tarefas-table-wrap">
              <table className="tarefas-table">
                <thead>
                  <tr>
                    <th>Título</th>
                    <th>Status</th>
                    <th>Prioridade</th>
                    <th>Prazo</th>
                    <th>Duração</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {resultado.content.map((tarefa) => (
                    <tr key={tarefa.id} className={tarefa.deletada ? 'tarefa-linha-deletada' : undefined}>
                      <td>
                        <Link to={`/tarefas/${tarefa.id}`}>{tarefa.titulo}</Link>
                        {tarefa.deletada ? (
                          <span className="status-badge status-deletada">Excluída</span>
                        ) : null}
                      </td>
                      <td>
                        <span className={`status-badge status-${tarefa.status.toLowerCase()}`}>
                          {STATUS_LABEL[tarefa.status]}
                        </span>
                      </td>
                      <td>{tarefa.prioridade}</td>
                      <td>{formatarDataHora(tarefa.prazo)}</td>
                      <td>{tarefa.duracaoHoras}h</td>
                      <td>
                        {tarefa.deletada ? (
                          <button
                            type="button"
                            className="button-table"
                            disabled={acaoId === tarefa.id}
                            onClick={() => void handleRestaurar(tarefa.id)}
                          >
                            {acaoId === tarefa.id ? 'Restaurando…' : 'Restaurar'}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="button-table button-table-danger"
                            disabled={acaoId === tarefa.id}
                            onClick={() => void handleExcluir(tarefa.id)}
                          >
                            {acaoId === tarefa.id ? 'Excluindo…' : 'Excluir'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="tarefas-paginacao">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((atual) => Math.max(0, atual - 1))}
            >
              Anterior
            </button>
            <span>
              Página {totalPages === 0 ? 0 : page + 1} de {totalPages}
            </span>
            <button
              type="button"
              disabled={totalPages === 0 || page >= totalPages - 1}
              onClick={() => setPage((atual) => atual + 1)}
            >
              Próxima
            </button>
          </div>
        </>
      ) : null}
    </main>
  )
}
