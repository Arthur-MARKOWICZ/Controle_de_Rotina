import { CalendarBlankIcon, FadersHorizontalIcon } from '@phosphor-icons/react'
import { useState, type Dispatch, type FormEvent, type Ref, type SetStateAction } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import { PRIORIDADE_LABEL } from '../home/labels'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { TagResumo } from '../tag/api/tagApi'
import { datetimeLocalParaInstant, formatarDataHora } from './datetime'
import { EdicoesAvancadasDialog } from './EdicoesAvancadasDialog'
import { PrazoDialog } from './PrazoDialog'
import type { Prioridade, TarefaFormValues } from './validation/tarefaForm'

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']

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
  /** Pai com filhos: duração é a soma e não pode ser editada. */
  duracaoSomenteLeitura?: boolean
  erro: string | null
  onSubmit: (event: FormEvent) => void
  onAbrirDescricao: () => void
  descricaoEntradaRef?: Ref<HTMLButtonElement>
  onDesvincular?: () => void
  desvinculando?: boolean
  tagIds: string[]
  tagsSelecionadas?: TagResumo[]
  onTagsChange: (tagIds: string[], tags: TagResumo[]) => void
}

function rotuloPrazo(prazoLocal: string): { aria: string; texto: string } {
  if (!prazoLocal.trim()) {
    return { aria: 'Definir prazo', texto: 'Definir prazo' }
  }
  const texto = formatarDataHora(datetimeLocalParaInstant(prazoLocal))
  return { aria: `Editar prazo, ${texto}`, texto }
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
  duracaoSomenteLeitura = false,
  erro,
  onSubmit,
  onAbrirDescricao,
  descricaoEntradaRef,
  onDesvincular,
  desvinculando = false,
  tagIds,
  tagsSelecionadas,
  onTagsChange,
}: Props) {
  const [prazoAberto, setPrazoAberto] = useState(false)
  const [avancadasAberto, setAvancadasAberto] = useState(false)
  const descricaoTrim = values.descricao.trim()
  const rotuloDescricao = descricaoTrim ? values.descricao : 'Adicionar descrição'
  const prazo = rotuloPrazo(values.prazo)

  return (
    <>
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
          <span className="field-label">Descrição</span>
          <button
            ref={descricaoEntradaRef}
            type="button"
            id="tarefa-descricao-entrada"
            className={
              descricaoTrim
                ? 'tarefa-descricao-entrada tarefa-descricao-entrada--preenchida'
                : 'tarefa-descricao-entrada'
            }
            disabled={disabled}
            onClick={onAbrirDescricao}
          >
            <span className="tarefa-descricao-entrada__texto">{rotuloDescricao}</span>
          </button>
        </div>

        <div className="tarefa-form__linha">
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
              disabled={disabled || duracaoSomenteLeitura}
              aria-describedby={duracaoSomenteLeitura ? 'tarefa-duracao-hint' : undefined}
              required
            />
            {duracaoSomenteLeitura ? (
              <p id="tarefa-duracao-hint" className="field-hint">
                Soma das subtarefas — não é editável.
              </p>
            ) : null}
          </div>
        </div>

        <div className="tarefa-form__acoes">
          <button
            type="button"
            className={
              values.prazo
                ? 'tarefa-descricao-entrada tarefa-descricao-entrada--preenchida'
                : 'tarefa-descricao-entrada'
            }
            aria-label={prazo.aria}
            aria-haspopup="dialog"
            aria-expanded={prazoAberto}
            disabled={disabled}
            onClick={() => setPrazoAberto(true)}
          >
            <CalendarBlankIcon aria-hidden />
            <span className="tarefa-descricao-entrada__texto">{prazo.texto}</span>
          </button>
          <button
            type="button"
            className="tarefa-descricao-entrada"
            aria-haspopup="dialog"
            aria-expanded={avancadasAberto}
            disabled={disabled}
            onClick={() => setAvancadasAberto(true)}
          >
            <FadersHorizontalIcon aria-hidden />
            <span className="tarefa-descricao-entrada__texto">Edições avançadas</span>
          </button>
        </div>

        {erro ? <InlineAlert>{erro}</InlineAlert> : null}
      </form>

      <PrazoDialog
        open={prazoAberto}
        value={values.prazo}
        disabled={disabled}
        onApply={(prazoLocal) => {
          setValues((a) => ({ ...a, prazo: prazoLocal }))
          setPrazoAberto(false)
        }}
        onClose={() => setPrazoAberto(false)}
      />
      <EdicoesAvancadasDialog
        open={avancadasAberto}
        modo={modo}
        values={values}
        onValuesChange={setValues}
        projetoId={projetoId}
        projetoNomeVinculado={projetoNomeVinculado}
        onProjetoChange={onProjetoChange}
        projetosAtivos={projetosAtivos}
        tagIds={tagIds}
        tagsSelecionadas={tagsSelecionadas}
        onTagsChange={onTagsChange}
        onDesvincular={onDesvincular}
        desvinculando={desvinculando}
        disabled={disabled}
        onClose={() => setAvancadasAberto(false)}
      />
    </>
  )
}
