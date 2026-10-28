import { useId, useState, type ChangeEvent } from 'react'

import { SenhaChecklist } from './SenhaChecklist'

type Props = {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete?: string
  required?: boolean
  mostrarChecklist?: boolean
  name?: string
}

export function CampoSenha({
  id,
  label,
  value,
  onChange,
  autoComplete = 'current-password',
  required = false,
  mostrarChecklist = false,
  name,
}: Props) {
  const [visivel, setVisivel] = useState(false)
  const checklistId = useId()

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="campo-senha">
        <input
          id={id}
          name={name ?? id}
          type={visivel ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
          required={required}
          aria-describedby={mostrarChecklist ? checklistId : undefined}
        />
        <button
          type="button"
          className="btn btn--ghost btn--sm campo-senha__toggle"
          onClick={() => setVisivel((v) => !v)}
          aria-pressed={visivel}
          aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
        >
          {visivel ? 'Ocultar' : 'Mostrar'}
        </button>
      </div>
      {mostrarChecklist ? <SenhaChecklist senha={value} id={checklistId} /> : null}
    </div>
  )
}
