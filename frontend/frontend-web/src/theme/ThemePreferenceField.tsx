import { useId } from 'react'

import { useTheme } from './ThemeProvider'
import type { PreferenciaTema } from './themePreference'

const OPCOES: { value: PreferenciaTema; label: string; hint: string }[] = [
  { value: 'system', label: 'Sistema', hint: 'Segue a preferência do sistema operacional' },
  { value: 'light', label: 'Claro', hint: 'Tema claro fixo' },
  { value: 'dark', label: 'Escuro', hint: 'Tema escuro fixo' },
]

export function ThemePreferenceField() {
  const { preferencia, setPreferencia } = useTheme()
  const grupo = useId()

  return (
    <fieldset className="perfil-secao">
      <legend>Aparência</legend>
      <div className="theme-options" role="radiogroup" aria-label="Tema">
        {OPCOES.map((opcao) => (
          <label key={opcao.value} className="theme-option">
            <input
              type="radio"
              name={grupo}
              value={opcao.value}
              checked={preferencia === opcao.value}
              onChange={() => setPreferencia(opcao.value)}
            />
            <span>
              <strong>{opcao.label}</strong>
              <span className="theme-option__hint">{opcao.hint}</span>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
