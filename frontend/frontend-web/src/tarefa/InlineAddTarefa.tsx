import {
  forwardRef,
  useId,
  useImperativeHandle,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from 'react'
import { useNavigate } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { useToast } from '../feedback/ToastProvider'
import { criarTarefa, type TarefaResponse } from './api/tarefaApi'

export type InlineAddTarefaHandle = {
  focus: () => void
}

type Props = {
  /** Projeto do filtro ativo da lista, se houver. */
  projetoId?: string
  onCriada: (tarefa: TarefaResponse) => void
}

export const InlineAddTarefa = forwardRef<InlineAddTarefaHandle, Props>(function InlineAddTarefa(
  { projetoId, onCriada },
  ref,
) {
  const navigate = useNavigate()
  const toast = useToast()
  const inputId = useId()
  const erroId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [titulo, setTitulo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  useImperativeHandle(ref, () => ({
    focus() {
      inputRef.current?.focus()
    },
  }))

  function limparErro() {
    setErro(null)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Escape') {
      return
    }
    event.preventDefault()
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
      const body = projetoId
        ? { titulo: texto, projetoId }
        : { titulo: texto }
      const criada = await criarTarefa(body)
      setTitulo('')
      onCriada(criada)
      toast.success('Tarefa criada', {
        action: {
          label: 'Abrir',
          onClick: () => navigate(`/tarefas/${criada.id}`),
        },
      })
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível criar a tarefa. Tente novamente.',
      )
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="inline-add-tarefa" onSubmit={(e) => void handleSubmit(e)} noValidate>
      <label className="visually-hidden" htmlFor={inputId}>
        Adicionar tarefa
      </label>
      <input
        ref={inputRef}
        id={inputId}
        type="text"
        name="titulo"
        maxLength={120}
        placeholder="Adicionar tarefa…"
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
  )
})
