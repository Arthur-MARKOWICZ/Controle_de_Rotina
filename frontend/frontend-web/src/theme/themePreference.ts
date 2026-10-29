export type PreferenciaTema = 'system' | 'light' | 'dark'
export type TemaEfetivo = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'controle-rotina:theme'

const PREFERENCIAS: ReadonlySet<string> = new Set(['system', 'light', 'dark'])

export function isPreferenciaTema(valor: string | null): valor is PreferenciaTema {
  return valor !== null && PREFERENCIAS.has(valor)
}

export function lerPreferenciaTema(): PreferenciaTema {
  try {
    const bruto = localStorage.getItem(THEME_STORAGE_KEY)
    return isPreferenciaTema(bruto) ? bruto : 'system'
  } catch {
    return 'system'
  }
}

export function salvarPreferenciaTema(preferencia: PreferenciaTema): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, preferencia)
  } catch {
    /* storage indisponível: ignore */
  }
}

function sistemaPrefereEscuro(): boolean {
  if (typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function preferenciaParaTemaEfetivo(
  preferencia: PreferenciaTema,
  prefersDark = sistemaPrefereEscuro(),
): TemaEfetivo {
  if (preferencia === 'light' || preferencia === 'dark') {
    return preferencia
  }
  return prefersDark ? 'dark' : 'light'
}

export function aplicarTemaEfetivo(tema: TemaEfetivo): void {
  document.documentElement.setAttribute('data-theme', tema)
}
