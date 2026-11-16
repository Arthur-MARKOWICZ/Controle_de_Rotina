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
import {
  ERRO_TITULO_LONGO,
  TEXTO_MAXIMO,
  TITULO_MAXIMO,
  type TokenQuickAdd,
} from './quickAdd/interpretarQuickAdd'
import { QuickAddChips } from './quickAdd/QuickAddChips'
import { useQuickAdd } from './quickAdd/useQuickAdd'
import type { Prioridade } from './validation/tarefaForm'

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']
const PRIORIDADE_PADRAO: Prioridade = 'MEDIA'
const DURACOES_MINUTOS = [15, 30, 45, 60, 90, 120, 180, 240]
const DURACAO_PADRAO = 30

export const PLACEHOLDER_INLINE_ADD = 'Adicionar tarefa… (ex.: Estudar 2h sex !alta #faculdade)'

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
  /** Instante de referência para resolver datas (testes). */
  referencia?: Date
}

export const InlineAddTarefa = forwardRef<InlineAddTarefaHandle, Props>(function InlineAddTarefa(
  { projetoId, onCriada, referencia },
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
  const { resultado, confirmadas, rejeitar, alternarTag, resetar, resolverTagIds } = useQuickAdd({
    texto: titulo,
    ativo: true,
    referencia,
  })

  // O chip manda no select: o valor exibido é o do token, senão o escolhido no select.
  const tokenDuracaoValido = resultado.duracao && !resultado.duracao.erro ? resultado.duracao : null
  const prioridadeEfetiva = resultado.prioridade?.prioridade ?? prioridade
  const duracaoEfetiva = tokenDuracaoValido?.minutos ?? duracaoMinutos
  const opcoesDuracao = DURACOES_MINUTOS.includes(duracaoEfetiva)
    ? DURACOES_MINUTOS
    : [...DURACOES_MINUTOS, duracaoEfetiva].sort((a, b) => a - b)

  function removerChip(token: TokenQuickAdd) {
    rejeitar(token)
    if (token.tipo === 'prioridade') {
      setPrioridade(PRIORIDADE_PADRAO)
    } else if (token.tipo === 'duracao') {
      setDuracaoMinutos(DURACAO_PADRAO)
    }
  }

  function limparTudo() {
    setTitulo('')
    setPrioridade(PRIORIDADE_PADRAO)
    setDuracaoMinutos(DURACAO_PADRAO)
    resetar()
  }

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
    limparTudo()
    limparErro()
    inputRef.current?.blur()
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const texto = resultado.titulo.trim()
    if (!texto) {
      setErro('Informe um título.')
      inputRef.current?.focus()
      return
    }
    if (texto.length > TITULO_MAXIMO) {
      setErro(ERRO_TITULO_LONGO)
      inputRef.current?.focus()
      return
    }
    if (resultado.duracao?.erro) {
      return
    }

    setEnviando(true)
    setErro(null)
    try {
      const tagIds = await resolverTagIds()
      const body: CriarTarefaRequest = {
        titulo: texto,
        prioridade: prioridadeEfetiva,
        duracaoMinutos: duracaoEfetiva,
      }
      if (resultado.prazo) {
        body.prazo = resultado.prazo.prazoIso
      }
      if (tagIds.length) {
        body.tagIds = tagIds
      }
      if (projetoId) {
        body.projetoId = projetoId
      }
      const criada = await criarTarefa(body)
      limparTudo()
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
        maxLength={TEXTO_MAXIMO}
        placeholder={PLACEHOLDER_INLINE_ADD}
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
        data-prioridade={prioridadeEfetiva}
        value={prioridadeEfetiva}
        disabled={enviando}
        onChange={(e) => {
          if (resultado.prioridade) {
            rejeitar(resultado.prioridade)
          }
          setPrioridade(e.target.value as Prioridade)
        }}
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
        value={duracaoEfetiva}
        disabled={enviando}
        onChange={(e) => {
          if (tokenDuracaoValido) {
            rejeitar(tokenDuracaoValido)
          }
          setDuracaoMinutos(Number(e.target.value))
        }}
      >
        {opcoesDuracao.map((m) => (
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
      <QuickAddChips
        tokens={resultado.tokens}
        tagsConfirmadas={confirmadas}
        onRemover={removerChip}
        onAlternarTag={alternarTag}
        disabled={enviando}
      />
      {erro ? (
        <p id={erroId} className="field-error inline-add-tarefa__erro" role="alert">
          {erro}
        </p>
      ) : null}
    </form>
  )
})
