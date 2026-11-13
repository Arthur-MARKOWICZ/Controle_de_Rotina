import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from '../auth/api/meApi'
import { useXpProgress, XpProgressProvider } from './XpProgressContext'

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn(),
}))

function Probe() {
  const { progresso, nomeUsuario, aplicarProgresso } = useXpProgress()
  return (
    <div>
      <p>nome:{nomeUsuario ?? 'vazio'}</p>
      <p>xp:{progresso?.xpTotal ?? 'nenhum'}</p>
      <button
        type="button"
        onClick={() =>
          aplicarProgresso({ xpTotal: 99, nivel: 4, xpNoNivel: 39, xpParaProximoNivel: 40 })
        }
      >
        Aplicar
      </button>
    </div>
  )
}

describe('XpProgressContext', () => {
  beforeEach(() => {
    vi.mocked(meApi.buscarMe).mockReset()
  })

  it('carrega progresso e nome de /me', async () => {
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 10,
      nivel: 2,
      xpNoNivel: 0,
      xpParaProximoNivel: 20,
    })
    render(
      <XpProgressProvider>
        <Probe />
      </XpProgressProvider>,
    )
    expect(await screen.findByText('nome:Ana')).toBeInTheDocument()
    expect(screen.getByText('xp:10')).toBeInTheDocument()
  })

  it('segue sem barra quando /me falha (estado vazio)', async () => {
    vi.mocked(meApi.buscarMe).mockRejectedValue(new Error('rede'))
    render(
      <XpProgressProvider>
        <Probe />
      </XpProgressProvider>,
    )
    await waitFor(() => expect(meApi.buscarMe).toHaveBeenCalled())
    expect(screen.getByText('nome:vazio')).toBeInTheDocument()
    expect(screen.getByText('xp:nenhum')).toBeInTheDocument()
  })

  it('aplica progresso local sem recarregar /me', async () => {
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    render(
      <XpProgressProvider>
        <Probe />
      </XpProgressProvider>,
    )
    await screen.findByText('xp:0')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Aplicar' }))
    expect(screen.getByText('xp:99')).toBeInTheDocument()
    expect(vi.mocked(meApi.buscarMe)).toHaveBeenCalledOnce()
  })

  it('useXpProgress fora do provider lança', () => {
    expect(() => render(<Probe />)).toThrow(/dentro de XpProgressProvider/)
  })

  it('não aplica /me se o provider desmontar no meio do fetch', async () => {
    let resolver!: (value: Awaited<ReturnType<typeof meApi.buscarMe>>) => void
    vi.mocked(meApi.buscarMe).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolver = resolve
        }),
    )
    const { unmount } = render(
      <XpProgressProvider>
        <Probe />
      </XpProgressProvider>,
    )
    unmount()
    resolver({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 50,
      nivel: 3,
      xpNoNivel: 0,
      xpParaProximoNivel: 30,
    })
    await Promise.resolve()
    expect(screen.queryByText('nome:Ana')).not.toBeInTheDocument()
  })

  it('mantém nome mesmo depois de aplicar progresso', async () => {
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    render(
      <XpProgressProvider>
        <Probe />
      </XpProgressProvider>,
    )
    await screen.findByText('nome:Ana')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Aplicar' }))
    expect(screen.getByText('nome:Ana')).toBeInTheDocument()
  })

  it('trata /me sem xp como zero visível', async () => {
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    render(
      <XpProgressProvider>
        <Probe />
      </XpProgressProvider>,
    )
    expect(await screen.findByText('xp:0')).toBeInTheDocument()
  })
})
