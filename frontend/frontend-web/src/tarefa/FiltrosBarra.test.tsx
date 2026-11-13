import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { FILTROS_LISTA_INICIAIS, type FiltrosListaUrl } from './listaUrl'
import { FiltrosBarra } from './FiltrosBarra'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

function Harness({
  inicial = FILTROS_LISTA_INICIAIS,
  onAplicar = vi.fn(),
}: {
  inicial?: FiltrosListaUrl
  onAplicar?: (proximo: FiltrosListaUrl) => void
}) {
  const [filtros, setFiltros] = useState(inicial)
  return (
    <FiltrosBarra
      filtros={filtros}
      projetosAtivos={[{ id: 'p-1', nome: 'TCC', descricao: null, status: 'ATIVO', createdAt: '', updatedAt: '' }]}
      onAplicar={(proximo) => {
        onAplicar(proximo)
        setFiltros(proximo)
      }}
    />
  )
}

describe('FiltrosBarra', () => {
  beforeEach(() => {
    vi.useRealTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('aplica visão Pendentes e zera a página', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(<Harness onAplicar={onAplicar} />)

    await user.click(screen.getByRole('button', { name: /pendentes/i }))

    expect(onAplicar).toHaveBeenCalledWith(
      expect.objectContaining({ visao: 'pendentes', page: 0 }),
    )
  })

  it('não reenvia a mesma visão já ativa (estado inalterado na prática de clique)', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(<Harness inicial={{ ...FILTROS_LISTA_INICIAIS, visao: 'todas' }} onAplicar={onAplicar} />)

    await user.click(screen.getByRole('button', { name: /^todas$/i }))

    expect(onAplicar).toHaveBeenCalledWith(expect.objectContaining({ visao: 'todas' }))
  })

  it('marca prioridade Alta no popover', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(<Harness onAplicar={onAplicar} />)

    await user.click(screen.getByRole('button', { name: /prioridade/i }))
    await user.click(screen.getByRole('checkbox', { name: /alta/i }))

    expect(onAplicar).toHaveBeenCalledWith(
      expect.objectContaining({ prioridade: ['ALTA'], page: 0 }),
    )
  })

  it('desmarca prioridade já selecionada', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(
      <Harness
        inicial={{ ...FILTROS_LISTA_INICIAIS, prioridade: ['ALTA'] }}
        onAplicar={onAplicar}
      />,
    )

    await user.click(screen.getByRole('button', { name: /^prioridade$/i }))
    await user.click(screen.getByRole('checkbox', { name: /alta/i }))

    expect(onAplicar).toHaveBeenCalledWith(expect.objectContaining({ prioridade: [] }))
  })

  it('não aplica prazo enquanto o debounce não dispara', () => {
    vi.useFakeTimers()
    const onAplicar = vi.fn()
    render(<Harness onAplicar={onAplicar} />)

    fireEvent.click(screen.getByRole('button', { name: /prazo/i }))
    fireEvent.change(screen.getByLabelText(/prazo de/i), { target: { value: '2026-10-20T10:00' } })

    expect(onAplicar).not.toHaveBeenCalled()
    vi.advanceTimersByTime(399)
    expect(onAplicar).not.toHaveBeenCalled()
  })

  it('aplica prazo após o debounce', () => {
    vi.useFakeTimers()
    const onAplicar = vi.fn()
    render(<Harness onAplicar={onAplicar} />)

    fireEvent.click(screen.getByRole('button', { name: /prazo/i }))
    fireEvent.change(screen.getByLabelText(/prazo de/i), { target: { value: '2026-10-20T10:00' } })
    vi.advanceTimersByTime(400)

    expect(onAplicar).toHaveBeenCalledWith(
      expect.objectContaining({ prazoDe: '2026-10-20T10:00', page: 0 }),
    )
  })

  it('limpa filtros ativos e volta ao padrão', async () => {
    const onAplicar = vi.fn()
    const user = userEvent.setup()
    render(
      <Harness
        inicial={{ ...FILTROS_LISTA_INICIAIS, visao: 'pendentes', prioridade: ['ALTA'] }}
        onAplicar={onAplicar}
      />,
    )

    await user.click(screen.getByRole('button', { name: /limpar filtros/i }))

    expect(onAplicar).toHaveBeenCalledWith(
      expect.objectContaining({ visao: 'pendentes', prioridade: [] }),
    )
  })
})
