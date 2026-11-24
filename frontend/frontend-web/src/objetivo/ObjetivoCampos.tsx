import {
  DESCRICAO_MAX,
  TITULO_MAX,
  type ObjetivoFormErros,
  type ObjetivoFormValues,
} from './validation/objetivoForm'

type Props = {
  values: ObjetivoFormValues
  onChange: (values: ObjetivoFormValues) => void
  erros?: ObjetivoFormErros
  /** Menor data aceita pelo seletor; ausente quando o prazo salvo já venceu. */
  prazoMinimo?: string
  desabilitado?: boolean
}

export function ObjetivoCampos({
  values,
  onChange,
  erros = {},
  prazoMinimo,
  desabilitado = false,
}: Props) {
  return (
    <>
      <div className="field">
        <label htmlFor="objetivo-titulo">Título</label>
        <input
          id="objetivo-titulo"
          name="titulo"
          type="text"
          maxLength={TITULO_MAX}
          placeholder="Ex.: Perder 5 kg"
          value={values.titulo}
          disabled={desabilitado}
          aria-invalid={erros.titulo ? true : undefined}
          onChange={(e) => onChange({ ...values, titulo: e.target.value })}
          required
        />
      </div>

      <div className="field">
        <label htmlFor="objetivo-descricao">Descrição (opcional)</label>
        <textarea
          id="objetivo-descricao"
          name="descricao"
          maxLength={DESCRICAO_MAX}
          rows={3}
          value={values.descricao}
          disabled={desabilitado}
          aria-invalid={erros.descricao ? true : undefined}
          onChange={(e) => onChange({ ...values, descricao: e.target.value })}
        />
      </div>

      <div className="field">
        <label htmlFor="objetivo-prazo">Prazo (opcional)</label>
        <div className="objetivo-prazo-row">
          <input
            id="objetivo-prazo"
            name="prazo"
            type="date"
            min={prazoMinimo}
            value={values.prazo}
            disabled={desabilitado}
            aria-invalid={erros.prazo ? true : undefined}
            onChange={(e) => onChange({ ...values, prazo: e.target.value })}
          />
          {values.prazo ? (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={desabilitado}
              onClick={() => onChange({ ...values, prazo: '' })}
            >
              Remover prazo
            </button>
          ) : null}
        </div>
      </div>
    </>
  )
}
