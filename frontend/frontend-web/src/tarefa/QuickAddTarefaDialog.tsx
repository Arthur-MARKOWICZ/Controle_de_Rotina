import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { useToast } from '../feedback/ToastProvider'
import { criarTarefa, type CriarTarefaRequest } from './api/tarefaApi'
import { ERRO_TITULO_LONGO, TEXTO_MAXIMO, TITULO_MAXIMO } from './quickAdd/interpretarQuickAdd'
import { QuickAddChips } from './quickAdd/QuickAddChips'
import { camposDoResultado, useQuickAdd } from './quickAdd/useQuickAdd'
import type { TarefaDrawerState } from './TarefaDrawer'

type Props = {
  open: boolean
  onClose: () => void
  onCriada?: () => void
  /** Instante de referência para resolver datas (testes). */
  referencia?: Date
}

export function QuickAddTarefaDialog({ open, onClose, onCriada, referencia }: Props) {
  const navigate = useNavigate()
  const toast = useToast()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const [titulo, setTitulo] = useState('')
  const [erroTitulo, setErroTitulo] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [erroApi, setErroApi] = useState<string | null>(null)
  const { resultado, confirmadas, rejeitar, alternarTag, resetar, resolverTagIds, tagsExistentes } =
    useQuickAdd({ texto: titulo, ativo: open, referencia })

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
    setTitulo('')
    setErroTitulo(null)
    setErroApi(null)
    setEnviando(false)
    resetar()
  }, [open, resetar])

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

  function abrirMaisDetalhes() {
    const state: TarefaDrawerState = {
      titulo: resultado.titulo,
      ...camposDoResultado(resultado),
      ...(tagsExistentes.length ? { tags: tagsExistentes } : {}),
    }
    onClose()
    navigate('/tarefas/nova', { state })
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErroApi(null)

    const texto = resultado.titulo.trim()
    if (!texto) {
      setErroTitulo('Informe um título.')
      return
    }
    if (texto.length > TITULO_MAXIMO) {
      setErroTitulo(ERRO_TITULO_LONGO)
      return
    }
    setErroTitulo(null)
    if (resultado.duracao?.erro) {
      return
    }

    setEnviando(true)
    try {
      const tagIds = await resolverTagIds()
      const body: CriarTarefaRequest = { titulo: texto, ...camposDoResultado(resultado) }
      if (tagIds.length) {
        body.tagIds = tagIds
      }
      const criada = await criarTarefa(body)
      toast.success('Tarefa criada', {
        action: {
          label: 'Abrir',
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
            maxLength={TEXTO_MAXIMO}
            value={titulo}
            onChange={(e) => {
              setTitulo(e.target.value)
              setErroTitulo(null)
            }}
            aria-invalid={Boolean(erroTitulo)}
            aria-describedby={erroTitulo ? 'quick-titulo-erro' : undefined}
            placeholder="Ex.: Estudar 2h sex !alta #faculdade"
            autoFocus
          />
          <QuickAddChips
            tokens={resultado.tokens}
            tagsConfirmadas={confirmadas}
            onRemover={rejeitar}
            onAlternarTag={alternarTag}
            disabled={enviando}
          />
          {erroTitulo ? (
            <p id="quick-titulo-erro" className="field-error" role="alert">
              {erroTitulo}
            </p>
          ) : null}
        </div>

        {erroApi ? (
          <p className="field-error" role="alert">
            {erroApi}
          </p>
        ) : null}

        <div className="quick-add-dialog__actions">
          <button
            type="button"
            className="btn btn--ghost quick-add-dialog__detalhes"
            onClick={abrirMaisDetalhes}
            disabled={enviando}
          >
            Mais detalhes
          </button>
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
