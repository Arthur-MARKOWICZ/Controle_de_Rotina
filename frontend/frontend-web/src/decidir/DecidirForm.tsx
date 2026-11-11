import type { Dispatch, FormEvent, SetStateAction } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { TagResumo } from '../tag/api/tagApi'
import { TagPicker } from '../tag/TagPicker'

export type DecidirFormValues = {
  titulo: string
  descricao: string
  decidirAte: string
}

type Props = {
  formId: string
  values: DecidirFormValues
  onValuesChange: Dispatch<SetStateAction<DecidirFormValues>>
  projetoId: string
  onProjetoChange: (projetoId: string) => void
  projetosAtivos: ProjetoResponse[]
  tagIds: string[]
  tagsSelecionadas?: TagResumo[]
  onTagsChange: (tagIds: string[], tags: TagResumo[]) => void
  disabled: boolean
  erro: string | null
  onSubmit: (event: FormEvent) => void
}

export function DecidirForm({
  formId,
  values,
  onValuesChange: setValues,
  projetoId,
  onProjetoChange,
  projetosAtivos,
  tagIds,
  tagsSelecionadas,
  onTagsChange,
  disabled,
  erro,
  onSubmit,
}: Props) {
  return (
    <form id={formId} className="tarefa-form" onSubmit={onSubmit} noValidate>
      <div className="field">
        <label htmlFor="decidir-titulo">Título</label>
        <input
          id="decidir-titulo"
          name="titulo"
          type="text"
          maxLength={120}
          value={values.titulo}
          onChange={(e) => setValues((a) => ({ ...a, titulo: e.target.value }))}
          disabled={disabled}
          required
        />
      </div>

      <div className="field">
        <label htmlFor="decidir-descricao">Descrição (opcional)</label>
        <textarea
          id="decidir-descricao"
          name="descricao"
          rows={5}
          maxLength={2000}
          value={values.descricao}
          onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
          disabled={disabled}
        />
      </div>

      <TagPicker
        selectedIds={tagIds}
        selectedTags={tagsSelecionadas}
        onChange={onTagsChange}
        disabled={disabled}
      />

      <div className="field">
        <label htmlFor="decidir-projeto">Projeto (opcional)</label>
        <select
          id="decidir-projeto"
          name="projetoId"
          value={projetoId}
          onChange={(e) => onProjetoChange(e.target.value)}
          disabled={disabled}
        >
          <option value="">Nenhum</option>
          {projetosAtivos.map((projeto) => (
            <option key={projeto.id} value={projeto.id}>
              {projeto.nome}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="decidir-ate">Decidir até (opcional)</label>
        <input
          id="decidir-ate"
          name="decidirAte"
          type="datetime-local"
          value={values.decidirAte}
          onChange={(e) => setValues((a) => ({ ...a, decidirAte: e.target.value }))}
          disabled={disabled}
        />
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}
    </form>
  )
}
