import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as meApi from '../auth/api/meApi'
import * as capacidadeApi from '../capacidade/api/capacidadeSemanalApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import { OnboardingChecklist } from './OnboardingChecklist'
import { chaveOnboardingDismissed } from './onboardingStorage'

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn(),
}))

vi.mock('../capacidade/api/capacidadeSemanalApi', () => ({
  listarCapacidadesSemanais: vi.fn(),
}))

vi.mock('../tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
}))

describe('OnboardingChecklist (T4)', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(meApi.buscarMe).mockReset()
    vi.mocked(capacidadeApi.listarCapacidadesSemanais).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(meApi.buscarMe).mockResolvedValue({
      id: 'u-novo',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
    vi.mocked(capacidadeApi.listarCapacidadesSemanais).mockResolvedValue([])
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 1,
    })
  })

  it('aparece para usuário novo e some ao dispensar', async () => {
    const user = userEvent.setup()
    const onAbrirQuickAdd = vi.fn()

    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} onAbrirQuickAdd={onAbrirQuickAdd} />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: /primeiros passos/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /definir capacidade semanal/i })).toHaveAttribute(
      'href',
      '/capacidade',
    )
    expect(screen.getByRole('button', { name: /criar primeira tarefa/i })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /não mostrar de novo/i }))

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /primeiros passos/i })).not.toBeInTheDocument()
    })
    expect(localStorage.getItem(chaveOnboardingDismissed('u-novo'))).toBe('1')
  })

  it('não aparece quando já foi dispensado', async () => {
    localStorage.setItem(chaveOnboardingDismissed('u-novo'), '1')

    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} onAbrirQuickAdd={vi.fn()} />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(meApi.buscarMe).toHaveBeenCalled()
    })
    expect(screen.queryByRole('heading', { name: /primeiros passos/i })).not.toBeInTheDocument()
  })
})
