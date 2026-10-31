import { EyeIcon, EyeSlashIcon } from '@phosphor-icons/react'
import { useId, useState, type ChangeEvent, type Ref } from 'react'

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
  erro?: string
  ref?: Ref<HTMLInputElement>
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
  erro,
  ref,
}: Props) {
  const [visivel, setVisivel] = useState(false)
  const checklistId = useId()
  const erroId = `${id}-erro`
  const descritores = [erro ? erroId : null, mostrarChecklist ? checklistId : null]
    .filter(Boolean)
    .join(' ')

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="campo-senha">
        <input
          ref={ref}
          id={id}
          name={name ?? id}
          type={visivel ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
          required={required}
          aria-invalid={erro ? true : undefined}
          aria-describedby={descritores || undefined}
        />
        <button
          type="button"
          className="btn btn--ghost btn--icon campo-senha__toggle"
          onClick={() => setVisivel((v) => !v)}
          aria-pressed={visivel}
          aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
        >
          {visivel ? <EyeSlashIcon aria-hidden /> : <EyeIcon aria-hidden />}
        </button>
      </div>
      {erro ? (
        <p id={erroId} className="field-error" role="alert">
          {erro}
        </p>
      ) : null}
      {mostrarChecklist ? <SenhaChecklist senha={value} id={checklistId} /> : null}
    </div>
  )
}
