import { useEffect, useId, useState, type FormEvent } from 'react'

import { ApiError } from '../auth/api/authApi'
import { listarProjetos } from '../projeto/api/projetoApi'
import { listarTarefas } from '../tarefa/api/tarefaApi'
import {
  esperasIrresolvidas,
  type CriarEsperaRequest,
  type EsperaResumo,
  type TipoRecursoEspera,
} from './api/espera'

type Alvo = {
  tipo: TipoRecursoEspera
  id: string
  titulo: string
}

type Props = {
  /** Tipo da origem — usado para não oferecer a própria origem como alvo. */
  origemTipo: TipoRecursoEspera
  origemId: string
  esperas: EsperaResumo[]
  /** Sem controle de criação/remoção (tarefa excluída, projeto arquivado). */
  somenteLeitura?: boolean
  onCriar: (body: CriarEsperaRequest) => Promise<void>
  onRemover: (esperaId: string) => Promise<void>
}

const VALOR_VAZIO = ''

function chave(tipo: TipoRecursoEspera, id: string): string {
  return `${tipo}:${id}`
}

function lerChave(valor: string): CriarEsperaRequest | null {
  const [tipo, ...resto] = valor.split(':')
  const id = resto.join(':')
  if ((tipo !== 'TAREFA' && tipo !== 'PROJETO') || !id) {
    return null
  }
  return { alvoTipo: tipo, alvoId: id }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function EsperaSection({
  origemTipo,
  origemId,
  esperas,
  somenteLeitura = false,
  onCriar,
  onRemover,
}: Props) {
  const tituloId = useId()
  const selectId = useId()
  const erroId = useId()
  const [alvos, setAlvos] = useState<Alvo[]>([])
  const [adicionando, setAdicionando] = useState(false)
  const [carregandoAlvos, setCarregandoAlvos] = useState(false)
  const [selecionado, setSelecionado] = useState(VALOR_VAZIO)
  const [enviando, setEnviando] = useState(false)
  const [removendo, setRemovendo] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  const bloqueada = esperasIrresolvidas(esperas).length > 0

  useEffect(() => {
    if (somenteLeitura || !adicionando) {
      return
    }
    let ativo = true
    async function carregar() {
      setCarregandoAlvos(true)
      try {
        const [tarefas, projetos] = await Promise.all([
          listarTarefas({ size: 100 }),
          listarProjetos({ status: 'ATIVO', size: 100 }),
        ])
        if (!ativo) {
          return
        }
        setAlvos([
          ...tarefas.content.map((t) => ({ tipo: 'TAREFA' as const, id: t.id, titulo: t.titulo })),
          ...projetos.content.map((p) => ({ tipo: 'PROJETO' as const, id: p.id, titulo: p.nome })),
        ])
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar os alvos possíveis.')
        }
      } finally {
        if (ativo) {
          setCarregandoAlvos(false)
        }
      }
    }
    void carregar()
    return () => {
      ativo = false
    }
  }, [somenteLeitura, adicionando])

  const jaEsperados = new Set(esperas.map((e) => chave(e.alvoTipo, e.alvoId)))
  const disponiveis = alvos.filter(
    (alvo) =>
      !(alvo.tipo === origemTipo && alvo.id === origemId) &&
      !jaEsperados.has(chave(alvo.tipo, alvo.id)),
  )
  const tarefasDisponiveis = disponiveis.filter((alvo) => alvo.tipo === 'TAREFA')
  const projetosDisponiveis = disponiveis.filter((alvo) => alvo.tipo === 'PROJETO')

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const body = lerChave(selecionado)
    if (!body) {
      setErro('Escolha uma tarefa ou um projeto.')
      return
    }
    setErro(null)
    setEnviando(true)
    try {
      await onCriar(body)
      setSelecionado(VALOR_VAZIO)
      setAdicionando(false)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível criar a espera. Tente novamente.'))
    } finally {
      setEnviando(false)
    }
  }

  async function handleRemover(esperaId: string) {
    setErro(null)
    setRemovendo(esperaId)
    try {
      await onRemover(esperaId)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível remover a espera. Tente novamente.'))
    } finally {
      setRemovendo(null)
    }
  }

  return (
    <section className="espera-secao" aria-labelledby={tituloId}>
      <div className="espera-secao__cabecalho">
        <h2 id={tituloId} className="espera-secao__titulo">
          Espera
        </h2>
        <p className="espera-secao__dica">
          {origemTipo === 'TAREFA'
            ? 'A tarefa só anda quando todos os alvos abaixo terminarem.'
            : 'O projeto só anda quando todos os alvos abaixo terminarem.'}
        </p>
      </div>

      {bloqueada ? (
        <p className="espera-secao__bloqueio" role="status">
          Bloqueada por {esperasIrresolvidas(esperas).length} item(ns) em aberto.
        </p>
      ) : null}

      {esperas.length > 0 ? (
        <ul className="espera-secao__lista">
          {esperas.map((espera) => (
            <li key={espera.id} className="espera-secao__item">
              <span className="espera-secao__alvo">
                {espera.alvoTitulo ?? 'Alvo removido'}
                <span className="espera-secao__tipo">
                  {espera.alvoTipo === 'TAREFA' ? 'tarefa' : 'projeto'}
                </span>
              </span>
              <span
                className={`badge ${espera.resolvida ? 'status-finalizada' : 'status-atrasada'}`}
              >
                {espera.resolvida ? 'Resolvida' : 'Em aberto'}
              </span>
              {somenteLeitura ? null : (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  disabled={removendo === espera.id}
                  onClick={() => void handleRemover(espera.id)}
                >
                  {removendo === espera.id ? 'Removendo…' : 'Remover'}
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="espera-secao__vazio">Nenhuma espera ainda.</p>
      )}

      {somenteLeitura ? null : !adicionando ? (
        <div className="espera-secao__form">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => {
              setErro(null)
              setAdicionando(true)
            }}
          >
            Adicionar espera
          </button>
        </div>
      ) : (
        <form className="espera-secao__form" onSubmit={(e) => void handleSubmit(e)} noValidate>
          <label className="visually-hidden" htmlFor={selectId}>
            Adicionar espera
          </label>
          <select
            id={selectId}
            name="alvoEspera"
            value={selecionado}
            disabled={enviando || carregandoAlvos}
            aria-invalid={Boolean(erro)}
            aria-describedby={erro ? erroId : undefined}
            onChange={(e) => {
              setSelecionado(e.target.value)
              setErro(null)
            }}
          >
            <option value={VALOR_VAZIO}>
              {carregandoAlvos ? 'Carregando alvos…' : 'Escolher alvo…'}
            </option>
            {tarefasDisponiveis.length > 0 ? (
              <optgroup label="Tarefas">
                {tarefasDisponiveis.map((alvo) => (
                  <option key={chave(alvo.tipo, alvo.id)} value={chave(alvo.tipo, alvo.id)}>
                    {alvo.titulo}
                  </option>
                ))}
              </optgroup>
            ) : null}
            {projetosDisponiveis.length > 0 ? (
              <optgroup label="Projetos">
                {projetosDisponiveis.map((alvo) => (
                  <option key={chave(alvo.tipo, alvo.id)} value={chave(alvo.tipo, alvo.id)}>
                    {alvo.titulo}
                  </option>
                ))}
              </optgroup>
            ) : null}
          </select>
          <button
            type="submit"
            className="btn btn--ghost btn--sm"
            disabled={enviando || carregandoAlvos}
            aria-busy={enviando}
          >
            {enviando ? 'Adicionando…' : 'Confirmar espera'}
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={enviando}
            onClick={() => {
              setAdicionando(false)
              setSelecionado(VALOR_VAZIO)
              setErro(null)
            }}
          >
            Cancelar espera
          </button>
          {erro ? (
            <p id={erroId} className="field-error espera-secao__erro" role="alert">
              {erro}
            </p>
          ) : null}
        </form>
      )}
    </section>
  )
}
