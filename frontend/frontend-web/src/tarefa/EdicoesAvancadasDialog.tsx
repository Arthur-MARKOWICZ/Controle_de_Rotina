import { useEffect, useId, useRef, type Dispatch, type FormEvent, type SetStateAction } from 'react'

import { STATUS_TAREFA_LABEL } from '../home/labels'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { TagResumo } from '../tag/api/tagApi'
import { TagPicker } from '../tag/TagPicker'
import type { StatusTarefa, TarefaFormValues } from './validation/tarefaForm'

const STATUS: StatusTarefa[] = ['PENDENTE', 'EM_PROGRESSO', 'FINALIZADA', 'ATRASADA', 'CANCELADA']

type Props = {
  open: boolean
  modo: 'criar' | 'editar'
  values: TarefaFormValues
  onValuesChange: Dispatch<SetStateAction<TarefaFormValues>>
  projetoId: string
  projetoNomeVinculado: string | null
  onProjetoChange: (projetoId: string, projetoNome: string | null) => void
  projetosAtivos: ProjetoResponse[]
  tagIds: string[]
  tagsSelecionadas?: TagResumo[]
  onTagsChange: (tagIds: string[], tags: TagResumo[]) => void
  onDesvincular?: () => void
  desvinculando?: boolean
  disabled: boolean
  onClose: () => void
}

export function EdicoesAvancadasDialog({
  open,
  modo,
  values,
  onValuesChange: setValues,
  projetoId,
  projetoNomeVinculado,
  onProjetoChange,
  projetosAtivos,
  tagIds,
  tagsSelecionadas,
  onTagsChange,
  onDesvincular,
  desvinculando = false,
  disabled,
  onClose,
}: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  const opcoesProjeto = [...projetosAtivos]
  if (projetoId && projetoNomeVinculado && !opcoesProjeto.some((p) => p.id === projetoId)) {
    opcoesProjeto.unshift({
      id: projetoId,
      nome: `${projetoNomeVinculado} (arquivado)`,
      descricao: null,
      status: 'ARQUIVADO',
      createdAt: '',
      updatedAt: '',
    })
  }

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

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onClose()
  }

  if (!open) {
    return null
  }

  return (
    <dialog
      ref={dialogRef}
      className="confirm-dialog tarefa-edicoes-dialog"
      aria-labelledby={titleId}
    >
      <form method="dialog" className="confirm-dialog__form" onSubmit={handleSubmit}>
        <h2 id={titleId} className="confirm-dialog__title">
          Edições avançadas
        </h2>
        <p className="confirm-dialog__message">Status, tags e o projeto da tarefa.</p>

        {modo === 'editar' ? (
          <div className="field">
            <label htmlFor="status">Status</label>
            <select
              id="status"
              name="status"
              value={values.status}
              onChange={(e) =>
                setValues((a) => ({ ...a, status: e.target.value as StatusTarefa | '' }))
              }
              disabled={disabled}
              required
            >
              <option value="">Selecione</option>
              {STATUS.map((s) => (
                <option key={s} value={s}>
                  {STATUS_TAREFA_LABEL[s]}
                </option>
              ))}
            </select>
          </div>
        ) : null}

        <TagPicker
          selectedIds={tagIds}
          selectedTags={tagsSelecionadas}
          onChange={onTagsChange}
          disabled={disabled}
        />

        <div className="field">
          <label htmlFor="projetoId">Projeto (opcional)</label>
          <select
            id="projetoId"
            name="projetoId"
            value={projetoId}
            onChange={(e) => {
              const novoId = e.target.value
              onProjetoChange(novoId, opcoesProjeto.find((p) => p.id === novoId)?.nome ?? null)
            }}
            disabled={disabled}
          >
            {!projetoId ? <option value="">Nenhum</option> : null}
            {opcoesProjeto.map((projeto) => (
              <option key={projeto.id} value={projeto.id} disabled={projeto.status === 'ARQUIVADO'}>
                {projeto.nome}
              </option>
            ))}
          </select>
          {modo === 'editar' && projetoId && onDesvincular ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm tarefa-desvincular"
              disabled={disabled || desvinculando}
              onClick={onDesvincular}
            >
              {desvinculando ? 'Desvinculando…' : 'Desvincular'}
            </button>
          ) : null}
        </div>

        <div className="confirm-dialog__actions">
          <button type="submit" className="btn btn--primary">
            Pronto
          </button>
        </div>
      </form>
    </dialog>
  )
}
