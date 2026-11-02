import type { DiaSemana } from '../capacidade/api/capacidadeSemanalApi'
import { DIAS_SEMANA_ORDEM, type HabitoFormValues } from './validation/habitoForm'

type Props = {
  values: HabitoFormValues
  onChange: (values: HabitoFormValues) => void
  desabilitado?: boolean
}

function alternarDia(dias: DiaSemana[], dia: DiaSemana): DiaSemana[] {
  return dias.includes(dia) ? dias.filter((d) => d !== dia) : [...dias, dia]
}

export function HabitoCampos({ values, onChange, desabilitado = false }: Props) {
  return (
    <>
      <div className="field">
        <label htmlFor="habito-titulo">Título</label>
        <input
          id="habito-titulo"
          name="titulo"
          type="text"
          maxLength={120}
          value={values.titulo}
          disabled={desabilitado}
          onChange={(e) => onChange({ ...values, titulo: e.target.value })}
          required
        />
      </div>

      <div className="field">
        <label htmlFor="habito-descricao">Descrição (opcional)</label>
        <textarea
          id="habito-descricao"
          name="descricao"
          maxLength={2000}
          rows={3}
          value={values.descricao}
          disabled={desabilitado}
          onChange={(e) => onChange({ ...values, descricao: e.target.value })}
        />
      </div>

      <div className="field">
        <label htmlFor="habito-duracao">Duração (minutos)</label>
        <input
          id="habito-duracao"
          name="duracaoMinutos"
          type="number"
          min={15}
          step={15}
          value={values.duracaoMinutos}
          disabled={desabilitado}
          onChange={(e) => {
            const raw = e.target.value
            onChange({
              ...values,
              duracaoMinutos: raw === '' ? '' : Number(raw),
            })
          }}
          required
        />
        <p className="field-hint">Múltiplos de 15, no mínimo 15.</p>
      </div>

      <fieldset className="field habito-dias" disabled={desabilitado}>
        <legend>Dias da semana</legend>
        <div className="habito-dias__chips" role="group" aria-label="Dias da semana">
          {DIAS_SEMANA_ORDEM.map((dia) => {
            const ativo = values.diasSemana.includes(dia.id)
            return (
              <label
                key={dia.id}
                className={ativo ? 'habito-dia-chip habito-dia-chip--ativo' : 'habito-dia-chip'}
              >
                <input
                  type="checkbox"
                  checked={ativo}
                  aria-label={dia.label}
                  onChange={() =>
                    onChange({
                      ...values,
                      diasSemana: alternarDia(values.diasSemana, dia.id),
                    })
                  }
                />
                <span aria-hidden>{dia.curto}</span>
              </label>
            )
          })}
        </div>
      </fieldset>

      <div className="field">
        <label htmlFor="habito-horario">Horário preferencial (opcional)</label>
        <div className="habito-horario-row">
          <input
            id="habito-horario"
            name="horarioPreferencial"
            type="time"
            value={values.horarioPreferencial}
            disabled={desabilitado}
            onChange={(e) => onChange({ ...values, horarioPreferencial: e.target.value })}
          />
          {values.horarioPreferencial ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={desabilitado}
              onClick={() => onChange({ ...values, horarioPreferencial: '' })}
            >
              Limpar horário
            </button>
          ) : null}
        </div>
        <p className="field-hint">Dica visual no plano — não fixa o horário na agenda.</p>
      </div>
    </>
  )
}
