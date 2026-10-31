import type { Ref } from 'react'

type Props = {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  type?: 'text' | 'email'
  autoComplete?: string
  required?: boolean
  erro?: string
  ref?: Ref<HTMLInputElement>
}

export function CampoTexto({
  id,
  label,
  value,
  onChange,
  type = 'text',
  autoComplete,
  required = false,
  erro,
  ref,
}: Props) {
  const erroId = `${id}-erro`

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        ref={ref}
        id={id}
        name={id}
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? erroId : undefined}
      />
      {erro ? (
        <p id={erroId} className="field-error" role="alert">
          {erro}
        </p>
      ) : null}
    </div>
  )
}
