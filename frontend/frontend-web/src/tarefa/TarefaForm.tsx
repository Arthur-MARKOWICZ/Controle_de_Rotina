import { useState, type Dispatch, type FormEvent, type SetStateAction } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import { PRIORIDADE_LABEL, STATUS_TAREFA_LABEL } from '../home/labels'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import { atalhosPrazo, type PrazoAtalhoId } from './prazoAtalhos'
import type { Prioridade, StatusTarefa, TarefaFormValues } from './validation/tarefaForm'

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']
const STATUS: StatusTarefa[] = ['PENDENTE', 'EM_PROGRESSO', 'FINALIZADA', 'ATRASADA', 'CANCELADA']

type Props = {
  formId: string
  modo: 'criar' | 'editar'
  values: TarefaFormValues
  onValuesChange: Dispatch<SetStateAction<TarefaFormValues>>
  projetoId: string
  projetoNomeVinculado: string | null
  onProjetoChange: (projetoId: string, projetoNome: string | null) => void
  projetosAtivos: ProjetoResponse[]
  disabled: boolean
  erro: string | null
  onSubmit: (event: FormEvent) => void
  onDesvincular?: () => void
  desvinculando?: boolean
}

export function TarefaForm({
  formId,
  modo,
  values,
  onValuesChange: setValues,
  projetoId,
  projetoNomeVinculado,
  onProjetoChange,
  projetosAtivos,
  disabled,
  erro,
  onSubmit,
  onDesvincular,
  desvinculando = false,
}: Props) {
  const [atalhoAtivo, setAtalhoAtivo] = useState<PrazoAtalhoId | null>(null)
  const atalhos = atalhosPrazo()

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

  function aplicarAtalho(id: PrazoAtalhoId) {
    const atalho = atalhos.find((item) => item.id === id)
    if (!atalho) {
      return
    }
    setAtalhoAtivo(id)
    setValues((a) => ({ ...a, prazo: atalho.prazoLocal }))
  }

  return (
    <form id={formId} className="tarefa-form" onSubmit={onSubmit} noValidate>
      <div className="field">
        <label htmlFor="titulo">Título</label>
        <input
          id="titulo"
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
        <label htmlFor="descricao">Descrição (opcional)</label>
        <textarea
          id="descricao"
          name="descricao"
          maxLength={2000}
          rows={4}
          value={values.descricao}
          onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
          disabled={disabled}
        />
      </div>

      <div className="field">
        <label htmlFor="prioridade">Prioridade</label>
        <select
          id="prioridade"
          name="prioridade"
          value={values.prioridade}
          onChange={(e) =>
            setValues((a) => ({ ...a, prioridade: e.target.value as Prioridade | '' }))
          }
          disabled={disabled}
          required
        >
          <option value="">Selecione</option>
          {PRIORIDADES.map((p) => (
            <option key={p} value={p}>
              {PRIORIDADE_LABEL[p]}
            </option>
          ))}
        </select>
      </div>

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

      <div className="field">
        <span className="field-label" id="tarefa-prazo-atalhos-label">
          Prazo rápido
        </span>
        <div className="prazo-chips" role="group" aria-labelledby="tarefa-prazo-atalhos-label">
          {atalhos.map((atalho) => (
            <button
              key={atalho.id}
              type="button"
              className={atalhoAtivo === atalho.id ? 'prazo-chip prazo-chip--ativo' : 'prazo-chip'}
              disabled={disabled}
              onClick={() => aplicarAtalho(atalho.id)}
            >
              {atalho.label}
            </button>
          ))}
        </div>
      </div>

      <div className="field">
        <label htmlFor="prazo">Prazo (opcional)</label>
        <input
          id="prazo"
          name="prazo"
          type="datetime-local"
          value={values.prazo}
          onChange={(e) => {
            setAtalhoAtivo(null)
            setValues((a) => ({ ...a, prazo: e.target.value }))
          }}
          disabled={disabled}
        />
      </div>

      <div className="field">
        <label htmlFor="duracaoMinutos">Duração (minutos)</label>
        <input
          id="duracaoMinutos"
          name="duracaoMinutos"
          type="number"
          min={15}
          step={15}
          value={values.duracaoMinutos}
          onChange={(e) =>
            setValues((a) => ({
              ...a,
              duracaoMinutos: e.target.value === '' ? '' : Number(e.target.value),
            }))
          }
          disabled={disabled}
          required
        />
      </div>

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

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}
    </form>
  )
}
