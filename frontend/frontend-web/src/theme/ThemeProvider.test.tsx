import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ThemeProvider, useTheme } from './ThemeProvider'
import { THEME_STORAGE_KEY } from './themePreference'

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

function Probe() {
  const { preferencia, temaEfetivo, setPreferencia } = useTheme()
  return (
    <div>
      <span data-testid="preferencia">{preferencia}</span>
      <span data-testid="efetivo">{temaEfetivo}</span>
      <button type="button" onClick={() => setPreferencia('dark')}>
        Escuro
      </button>
      <button type="button" onClick={() => setPreferencia('light')}>
        Claro
      </button>
      <button type="button" onClick={() => setPreferencia('system')}>
        Sistema
      </button>
    </div>
  )
}

describe('ThemeProvider', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    mockMatchMedia(false)
  })

  it('aplica tema efetivo no documentElement a partir da preferência salva', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    )

    expect(screen.getByTestId('preferencia')).toHaveTextContent('dark')
    expect(screen.getByTestId('efetivo')).toHaveTextContent('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('ao trocar preferência, persiste e atualiza data-theme', async () => {
    const user = userEvent.setup()
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Escuro' }))

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
    expect(screen.getByTestId('preferencia')).toHaveTextContent('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('system resolve dark quando o SO prefere escuro', () => {
    mockMatchMedia(true)
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    )

    expect(screen.getByTestId('preferencia')).toHaveTextContent('system')
    expect(screen.getByTestId('efetivo')).toHaveTextContent('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})
