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
import { PRIORIDADE_LABEL } from '../home/labels'
import { criarTarefa, type CriarTarefaRequest, type TarefaResponse } from './api/tarefaApi'
import type { Prioridade } from './validation/tarefaForm'

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']
const PRIORIDADE_PADRAO: Prioridade = 'MEDIA'
const DURACOES_MINUTOS = [15, 30, 45, 60, 90, 120, 180, 240]
const DURACAO_PADRAO = 30

function formatarDuracao(minutos: number): string {
  if (minutos < 60) {
    return `${minutos} min`
  }
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return resto ? `${horas}h${resto}` : `${horas}h`
}

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
  const prioridadeId = useId()
  const duracaoId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [titulo, setTitulo] = useState('')
  const [prioridade, setPrioridade] = useState<Prioridade>(PRIORIDADE_PADRAO)
  const [duracaoMinutos, setDuracaoMinutos] = useState(DURACAO_PADRAO)
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
      const body: CriarTarefaRequest = { titulo: texto, prioridade, duracaoMinutos }
      if (projetoId) {
        body.projetoId = projetoId
      }
      const criada = await criarTarefa(body)
      setTitulo('')
      setPrioridade(PRIORIDADE_PADRAO)
      setDuracaoMinutos(DURACAO_PADRAO)
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
    <form className="inline-add-tarefa inline-add-tarefa--completa" onSubmit={(e) => void handleSubmit(e)} noValidate>
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
      <label className="visually-hidden" htmlFor={prioridadeId}>
        Prioridade
      </label>
      <select
        id={prioridadeId}
        name="prioridade"
        className="inline-add-tarefa__select"
        data-prioridade={prioridade}
        value={prioridade}
        disabled={enviando}
        onChange={(e) => setPrioridade(e.target.value as Prioridade)}
      >
        {PRIORIDADES.map((p) => (
          <option key={p} value={p}>
            {PRIORIDADE_LABEL[p]}
          </option>
        ))}
      </select>
      <label className="visually-hidden" htmlFor={duracaoId}>
        Duração
      </label>
      <select
        id={duracaoId}
        name="duracaoMinutos"
        className="inline-add-tarefa__select"
        value={duracaoMinutos}
        disabled={enviando}
        onChange={(e) => setDuracaoMinutos(Number(e.target.value))}
      >
        {DURACOES_MINUTOS.map((m) => (
          <option key={m} value={m}>
            {formatarDuracao(m)}
          </option>
        ))}
      </select>
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
