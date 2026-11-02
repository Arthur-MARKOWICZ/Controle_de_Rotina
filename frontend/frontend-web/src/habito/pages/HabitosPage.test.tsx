import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../../feedback/ToastProvider'
import * as habitoApi from '../api/habitoApi'
import { HabitoDrawer } from '../HabitoDrawer'
import { HabitosPage } from './HabitosPage'

vi.mock('../api/habitoApi', () => ({
  listarHabitos: vi.fn(),
  pausarHabito: vi.fn(),
  reativarHabito: vi.fn(),
  excluirHabito: vi.fn(),
  restaurarHabito: vi.fn(),
  criarHabito: vi.fn(),
  buscarHabito: vi.fn(),
  atualizarHabito: vi.fn(),
}))

const pagina = {
  content: [
    {
      id: 'h-correr',
      titulo: 'Correr',
      descricao: null,
      duracaoMinutos: 30,
      diasSemana: ['SEGUNDA', 'QUARTA'] as const,
      horarioPreferencial: '07:00:00',
      status: 'ATIVO' as const,
      streakAtual: 4,
      melhorStreak: 10,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

function renderLista(path = '/habitos') {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/habitos" element={<HabitosPage />}>
            <Route path="nova" element={<HabitoDrawer />} />
            <Route path=":id" element={<HabitoDrawer />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('HabitosPage', () => {
  beforeEach(() => {
    vi.mocked(habitoApi.listarHabitos).mockReset()
    vi.mocked(habitoApi.pausarHabito).mockReset()
    vi.mocked(habitoApi.reativarHabito).mockReset()
    vi.mocked(habitoApi.excluirHabito).mockReset()
    vi.mocked(habitoApi.listarHabitos).mockResolvedValue({
      ...pagina,
      content: pagina.content.map((h) => ({ ...h, diasSemana: [...h.diasSemana] })),
    })
  })

  it('T1: lista renderiza hábitos e pausar chama API', async () => {
    vi.mocked(habitoApi.pausarHabito).mockResolvedValue({
      ...pagina.content[0],
      diasSemana: [...pagina.content[0].diasSemana],
      status: 'PAUSADO',
    })
    vi.mocked(habitoApi.listarHabitos)
      .mockResolvedValueOnce({
        ...pagina,
        content: pagina.content.map((h) => ({ ...h, diasSemana: [...h.diasSemana] })),
      })
      .mockResolvedValueOnce({ ...pagina, content: [], totalElements: 0, totalPages: 0 })

    const user = userEvent.setup()
    renderLista()

    expect(await screen.findByText('Correr')).toBeInTheDocument()
    expect(habitoApi.listarHabitos).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ATIVO', page: 0, size: 20 }),
    )

    await user.click(screen.getByRole('button', { name: /^pausar$/i }))

    await waitFor(() => {
      expect(habitoApi.pausarHabito).toHaveBeenCalledWith('h-correr')
    })
  })

  it('T5: streak aparece na lista quando a API envia streakAtual', async () => {
    renderLista()

    expect(await screen.findByLabelText('Sequência atual 4')).toHaveTextContent('Sequência 4')
  })

  it('T2: form exige ≥ 1 dia e duração múltipla de 15', async () => {
    const user = userEvent.setup()
    renderLista('/habitos/nova')

    expect(await screen.findByRole('heading', { name: 'Novo hábito' })).toBeInTheDocument()

    await user.type(screen.getByLabelText('Título'), 'Meditar')
    await user.click(screen.getByRole('button', { name: /criar hábito/i }))

    expect(await screen.findByText(/pelo menos um dia/i)).toBeInTheDocument()
    expect(habitoApi.criarHabito).not.toHaveBeenCalled()

    await user.click(screen.getByRole('checkbox', { name: /segunda/i }))
    await user.clear(screen.getByLabelText('Duração (minutos)'))
    await user.type(screen.getByLabelText('Duração (minutos)'), '20')
    await user.click(screen.getByRole('button', { name: /criar hábito/i }))

    expect(await screen.findByText(/múltiplo de 15/i)).toBeInTheDocument()
    expect(habitoApi.criarHabito).not.toHaveBeenCalled()
  })
})
