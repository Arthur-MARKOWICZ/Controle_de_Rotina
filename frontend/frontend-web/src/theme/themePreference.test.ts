import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  THEME_STORAGE_KEY,
  aplicarTemaEfetivo,
  lerPreferenciaTema,
  preferenciaParaTemaEfetivo,
  salvarPreferenciaTema,
  type PreferenciaTema,
  type TemaEfetivo,
} from './themePreference'

function mockMatchMedia(prefersDark: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: query === '(prefers-color-scheme: dark)' ? prefersDark : false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
}

describe('themePreference', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    mockMatchMedia(false)
  })

  afterEach(() => {
    document.documentElement.removeAttribute('data-theme')
  })

  it('default da preferência é system quando storage vazio', () => {
    expect(lerPreferenciaTema()).toBe('system')
  })

  it('persiste e lê preferência válida', () => {
    const preferencias: PreferenciaTema[] = ['system', 'light', 'dark']
    for (const preferencia of preferencias) {
      salvarPreferenciaTema(preferencia)
      expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe(preferencia)
      expect(lerPreferenciaTema()).toBe(preferencia)
    }
  })

  it('valor inválido no storage cai para system', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'neon')
    expect(lerPreferenciaTema()).toBe('system')
  })

  it('resolve light/dark explícitos sem consultar o sistema', () => {
    mockMatchMedia(true)
    expect(preferenciaParaTemaEfetivo('light')).toBe('light')
    expect(preferenciaParaTemaEfetivo('dark')).toBe('dark')
  })

  it('system segue prefers-color-scheme', () => {
    mockMatchMedia(true)
    expect(preferenciaParaTemaEfetivo('system')).toBe('dark')
    mockMatchMedia(false)
    expect(preferenciaParaTemaEfetivo('system')).toBe('light')
  })

  it('aplica data-theme no documentElement', () => {
    const temas: TemaEfetivo[] = ['light', 'dark']
    for (const tema of temas) {
      aplicarTemaEfetivo(tema)
      expect(document.documentElement.getAttribute('data-theme')).toBe(tema)
    }
  })
})
