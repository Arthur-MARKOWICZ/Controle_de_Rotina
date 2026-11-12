import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { useToast } from '../feedback/ToastProvider'
import { STATUS_TAREFA_LABEL } from '../home/labels'
import { buscarTarefa, criarTarefa, listarTarefas, type TarefaResponse } from './api/tarefaApi'

type Props = {
  parent: TarefaResponse
  onPaiAtualizado: (tarefa: TarefaResponse) => void
}

function ordenarPorCriacao(filhos: TarefaResponse[]): TarefaResponse[] {
  return [...filhos].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function TarefaSubtarefasSection({ parent, onPaiAtualizado }: Props) {
  const toast = useToast()
  const location = useLocation()
  const inputId = useId()
  const erroId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [filhos, setFilhos] = useState<TarefaResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [titulo, setTitulo] = useState('')

  useEffect(() => {
    let ativo = true
    async function carregar() {
      setCarregando(true)
      try {
        const page = await listarTarefas({ parentId: parent.id, size: 100 })
        if (ativo) {
          setFilhos(ordenarPorCriacao(page.content))
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar as subtarefas.')
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
  }, [parent.id])

  function limparErro() {
    setErro(null)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Escape') {
      return
    }
    event.preventDefault()
    event.stopPropagation()
    setTitulo('')
    limparErro()
    inputRef.current?.blur()
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const texto = titulo.trim()
    if (!texto) {
      setErro('Informe um título.')
      inputRef.current?.focus()
      return
    }

    setEnviando(true)
    setErro(null)
    try {
      const criada = await criarTarefa({
        titulo: texto,
        parentId: parent.id,
      })
      setFilhos((atual) => ordenarPorCriacao([...atual.filter((item) => item.id !== criada.id), criada]))
      setTitulo('')
      toast.success('Subtarefa criada')
      try {
        onPaiAtualizado(await buscarTarefa(parent.id))
      } catch {
        onPaiAtualizado({ ...parent, temFilhos: true })
      }
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível criar a subtarefa. Tente novamente.'))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="tarefa-subtarefas" aria-labelledby="tarefa-subtarefas-titulo">
      <div className="tarefa-subtarefas__cabecalho">
        <h2 id="tarefa-subtarefas-titulo" className="tarefa-subtarefas__titulo">
          Subtarefas
        </h2>
        <p className="tarefa-subtarefas__dica">Só o título. Abra a subtarefa para o restante.</p>
      </div>

      {carregando ? (
        <p role="status">Carregando subtarefas…</p>
      ) : filhos.length > 0 ? (
        <ul className="tarefa-subtarefas__lista">
          {filhos.map((filho) => (
            <li key={filho.id}>
              <Link
                to={{ pathname: `/tarefas/${filho.id}`, search: location.search }}
                state={{ daLista: true }}
                className="tarefa-subtarefas__item"
              >
                <span className="tarefa-subtarefas__nome">{filho.titulo}</span>
                <span className="tarefa-subtarefas__meta">
                  {filho.duracaoMinutos} min · {STATUS_TAREFA_LABEL[filho.status]}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="tarefa-subtarefas__vazio">Nenhuma subtarefa ainda.</p>
      )}

      <form className="inline-add-tarefa" onSubmit={(e) => void handleSubmit(e)} noValidate>
        <label className="visually-hidden" htmlFor={inputId}>
          Adicionar subtarefa
        </label>
        <input
          ref={inputRef}
          id={inputId}
          type="text"
          name="tituloSubtarefa"
          maxLength={120}
          placeholder="Adicionar subtarefa…"
          value={titulo}
          disabled={enviando}
          aria-invalid={Boolean(erro)}
          aria-describedby={erro ? erroId : undefined}
          onChange={(e) => {
            setTitulo(e.target.value)
            limparErro()
          }}
          onKeyDown={handleKeyDown}
        />
        <button
          type="submit"
          className="btn btn--ghost btn--sm"
          disabled={enviando}
          aria-busy={enviando}
        >
          {enviando ? 'Adicionando…' : 'Adicionar'}
        </button>
        {erro ? (
          <p id={erroId} className="field-error inline-add-tarefa__erro" role="alert">
            {erro}
          </p>
        ) : null}
      </form>
    </section>
  )
}
