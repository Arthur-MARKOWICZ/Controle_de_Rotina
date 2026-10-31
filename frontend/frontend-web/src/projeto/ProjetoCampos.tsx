import type { ProjetoFormValues } from './validation/projetoForm'

type Props = {
  values: ProjetoFormValues
  onChange: (values: ProjetoFormValues) => void
}

export function ProjetoCampos({ values, onChange }: Props) {
  return (
    <>
      <div className="field">
        <label htmlFor="nome">Nome</label>
        <input
          id="nome"
          name="nome"
          type="text"
          maxLength={80}
          value={values.nome}
          onChange={(e) => onChange({ ...values, nome: e.target.value })}
          required
        />
      </div>

      <div className="field">
        <label htmlFor="descricao">Descrição (opcional)</label>
        <textarea
          id="descricao"
          name="descricao"
          maxLength={500}
          rows={4}
          value={values.descricao}
          onChange={(e) => onChange({ ...values, descricao: e.target.value })}
        />
      </div>
    </>
  )
}
