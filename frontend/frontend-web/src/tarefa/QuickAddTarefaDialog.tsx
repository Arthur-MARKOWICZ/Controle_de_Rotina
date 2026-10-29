import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { useToast } from '../feedback/ToastProvider'
import { PRIORIDADE_LABEL } from '../home/labels'
import { criarTarefa } from './api/tarefaApi'
import { datetimeLocalParaInstant } from './datetime'
import { atalhosPrazo, type PrazoAtalhoId } from './prazoAtalhos'
import {
  validarTarefaFormCampos,
  type Prioridade,
  type TarefaFormErros,
  type TarefaFormValues,
} from './validation/tarefaForm'

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']

const VAZIO: TarefaFormValues = {
  titulo: '',
  descricao: '',
  prioridade: 'MEDIA',
  prazo: '',
  duracaoMinutos: 60,
}

type Props = {
  open: boolean
  onClose: () => void
  onCriada?: () => void
}

export function QuickAddTarefaDialog({ open, onClose, onCriada }: Props) {
  const navigate = useNavigate()
  const toast = useToast()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const [values, setValues] = useState<TarefaFormValues>(VAZIO)
  const [erros, setErros] = useState<TarefaFormErros>({})
  const [mostrarDescricao, setMostrarDescricao] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [erroApi, setErroApi] = useState<string | null>(null)
  const [atalhoAtivo, setAtalhoAtivo] = useState<PrazoAtalhoId | null>(null)

  const atalhos = atalhosPrazo()

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    if (open) {
      if (!dialog.open) {
        dialog.showModal()
      }
    } else if (dialog.open) {
      dialog.close()
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      return
    }
    setValues(VAZIO)
    setErros({})
    setMostrarDescricao(false)
    setErroApi(null)
    setAtalhoAtivo(null)
    setEnviando(false)
  }, [open])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    function handleCancel(event: Event) {
      event.preventDefault()
      onClose()
    }
    dialog.addEventListener('cancel', handleCancel)
    return () => dialog.removeEventListener('cancel', handleCancel)
  }, [onClose])

  function aplicarAtalho(id: PrazoAtalhoId) {
    const atalho = atalhos.find((item) => item.id === id)
    if (!atalho) {
      return
    }
    setAtalhoAtivo(id)
    setValues((atual) => ({ ...atual, prazo: atalho.prazoLocal }))
    setErros((atual) => ({ ...atual, prazo: undefined }))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErroApi(null)

    const validacao = validarTarefaFormCampos(values, { modo: 'criar' })
    setErros(validacao)
    if (Object.keys(validacao).length > 0) {
      return
    }

    const prioridade = values.prioridade as Prioridade
    const prazo = datetimeLocalParaInstant(values.prazo)
    const duracaoMinutos = values.duracaoMinutos as number

    setEnviando(true)
    try {
      const criada = await criarTarefa({
        titulo: values.titulo.trim(),
        descricao: values.descricao.trim(),
        prioridade,
        prazo,
        duracaoMinutos,
      })
      toast.success('Tarefa criada', {
        action: {
          label: 'Ver',
          onClick: () => navigate(`/tarefas/${criada.id}`),
        },
      })
      onCriada?.()
      onClose()
    } catch (error) {
      if (error instanceof ApiError) {
        setErroApi(error.message)
      } else {
        setErroApi('Não foi possível criar a tarefa. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  if (!open) {
    return null
  }

  return (
    <dialog ref={dialogRef} className="quick-add-dialog" aria-labelledby={titleId}>
      <form className="quick-add-dialog__form" onSubmit={(e) => void handleSubmit(e)} noValidate>
        <h2 id={titleId} className="quick-add-dialog__title">
          Nova tarefa
        </h2>

        <div className="field">
          <label htmlFor="quick-titulo">Título</label>
          <input
            id="quick-titulo"
            name="titulo"
            type="text"
            maxLength={120}
            value={values.titulo}
            onChange={(e) => {
              setValues((a) => ({ ...a, titulo: e.target.value }))
              setErros((a) => ({ ...a, titulo: undefined }))
            }}
            aria-invalid={Boolean(erros.titulo)}
            aria-describedby={erros.titulo ? 'quick-titulo-erro' : undefined}
            autoFocus
          />
          {erros.titulo ? (
            <p id="quick-titulo-erro" className="field-error" role="alert">
              {erros.titulo}
            </p>
          ) : null}
        </div>

        <div className="field">
          <label htmlFor="quick-duracao">Duração (minutos)</label>
          <input
            id="quick-duracao"
            name="duracaoMinutos"
            type="number"
            min={15}
            step={15}
            value={values.duracaoMinutos}
            onChange={(e) => {
              setValues((a) => ({
                ...a,
                duracaoMinutos: e.target.value === '' ? '' : Number(e.target.value),
              }))
              setErros((a) => ({ ...a, duracaoMinutos: undefined }))
            }}
            aria-invalid={Boolean(erros.duracaoMinutos)}
            aria-describedby={erros.duracaoMinutos ? 'quick-duracao-erro' : undefined}
          />
          {erros.duracaoMinutos ? (
            <p id="quick-duracao-erro" className="field-error" role="alert">
              {erros.duracaoMinutos}
            </p>
          ) : null}
        </div>

        <div className="field">
          <label htmlFor="quick-prioridade">Prioridade</label>
          <select
            id="quick-prioridade"
            name="prioridade"
            value={values.prioridade}
            onChange={(e) => {
              setValues((a) => ({ ...a, prioridade: e.target.value as Prioridade | '' }))
              setErros((a) => ({ ...a, prioridade: undefined }))
            }}
            aria-invalid={Boolean(erros.prioridade)}
          >
            {PRIORIDADES.map((p) => (
              <option key={p} value={p}>
                {PRIORIDADE_LABEL[p]}
              </option>
            ))}
          </select>
          {erros.prioridade ? (
            <p className="field-error" role="alert">
              {erros.prioridade}
            </p>
          ) : null}
        </div>

        <div className="field">
          <span className="field-label" id="quick-prazo-atalhos-label">
            Prazo rápido
          </span>
          <div
            className="prazo-chips"
            role="group"
            aria-labelledby="quick-prazo-atalhos-label"
          >
            {atalhos.map((atalho) => (
              <button
                key={atalho.id}
                type="button"
                className={
                  atalhoAtivo === atalho.id
                    ? 'prazo-chip prazo-chip--ativo'
                    : 'prazo-chip'
                }
                onClick={() => aplicarAtalho(atalho.id)}
              >
                {atalho.label}
              </button>
            ))}
          </div>
        </div>

        <div className="field">
          <label htmlFor="quick-prazo">Prazo (opcional)</label>
          <input
            id="quick-prazo"
            name="prazo"
            type="datetime-local"
            value={values.prazo}
            onChange={(e) => {
              setAtalhoAtivo(null)
              setValues((a) => ({ ...a, prazo: e.target.value }))
              setErros((a) => ({ ...a, prazo: undefined }))
            }}
            aria-invalid={Boolean(erros.prazo)}
            aria-describedby={erros.prazo ? 'quick-prazo-erro' : undefined}
          />
          {erros.prazo ? (
            <p id="quick-prazo-erro" className="field-error" role="alert">
              {erros.prazo}
            </p>
          ) : null}
        </div>

        {mostrarDescricao ? (
          <div className="field">
            <label htmlFor="quick-descricao">Descrição (opcional)</label>
            <textarea
              id="quick-descricao"
              name="descricao"
              maxLength={2000}
              rows={3}
              value={values.descricao}
              onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
            />
          </div>
        ) : (
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setMostrarDescricao(true)}
          >
            Adicionar descrição
          </button>
        )}

        {erroApi ? (
          <p className="field-error" role="alert">
            {erroApi}
          </p>
        ) : null}

        <div className="quick-add-dialog__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={enviando}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primary" disabled={enviando} aria-busy={enviando}>
            {enviando ? 'Criando…' : 'Criar tarefa'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
