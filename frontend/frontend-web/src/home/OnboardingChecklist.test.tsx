import { render, screen, waitFor, within } from '@testing-library/react'
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

    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: /primeiros passos/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /criar primeira tarefa/i })).toHaveAttribute(
      'href',
      '/tarefas',
    )
    expect(screen.getByRole('link', { name: /definir capacidade semanal/i })).toHaveAttribute(
      'href',
      '/capacidade',
    )

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
        <OnboardingChecklist temPlano={false} />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(meApi.buscarMe).toHaveBeenCalled()
    })
    expect(screen.queryByRole('heading', { name: /primeiros passos/i })).not.toBeInTheDocument()
  })

  it('T3: passos em ordem tarefas → capacidade → plano, sem apontar para / como Hoje', async () => {
    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} />
      </MemoryRouter>,
    )

    const lista = await screen.findByRole('list')
    const itens = within(lista).getAllByRole('listitem')
    expect(itens).toHaveLength(3)
    expect(within(itens[0]).getByRole('link', { name: /criar primeira tarefa/i })).toHaveAttribute(
      'href',
      '/tarefas',
    )
    expect(
      within(itens[1]).getByRole('link', { name: /definir capacidade semanal/i }),
    ).toHaveAttribute('href', '/capacidade')
    expect(within(itens[2]).getByRole('link', { name: /gerar primeiro plano/i })).toHaveAttribute(
      'href',
      '/plano',
    )
    expect(screen.queryByRole('link', { name: /^hoje$/i })).not.toBeInTheDocument()
    expect(lista.querySelector('a[href="/"]')).toBeNull()
  })

  it('some quando /me falha (estado inalterado)', async () => {
    vi.mocked(meApi.buscarMe).mockRejectedValue(new Error('rede'))
    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} />
      </MemoryRouter>,
    )
    await waitFor(() => expect(meApi.buscarMe).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: /primeiros passos/i })).not.toBeInTheDocument()
  })

  it('some quando todos os passos já estão feitos', async () => {
    vi.mocked(capacidadeApi.listarCapacidadesSemanais).mockResolvedValue([
      {
        id: 'c-1',
        diaSemana: 'SEGUNDA',
        horas: 4,
        ativo: true,
        createdAt: '',
        updatedAt: '',
      },
    ])
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue({
      content: [{ id: 't-1' } as never],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 1,
    })
    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano />
      </MemoryRouter>,
    )
    await waitFor(() => expect(meApi.buscarMe).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: /primeiros passos/i })).not.toBeInTheDocument()
  })

  it('usa o callback de gerar plano quando informado', async () => {
    const onGerarPlano = vi.fn()
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} onGerarPlano={onGerarPlano} />
      </MemoryRouter>,
    )
    await user.click(await screen.findByRole('button', { name: /gerar primeiro plano/i }))
    expect(onGerarPlano).toHaveBeenCalledOnce()
  })

  it('capacidade com 0h não marca o passo', async () => {
    vi.mocked(capacidadeApi.listarCapacidadesSemanais).mockResolvedValue([
      {
        id: 'c-1',
        diaSemana: 'SEGUNDA',
        horas: 0,
        ativo: true,
        createdAt: '',
        updatedAt: '',
      },
    ])
    render(
      <MemoryRouter>
        <OnboardingChecklist temPlano={false} />
      </MemoryRouter>,
    )
    const link = await screen.findByRole('link', { name: /definir capacidade semanal/i })
    expect(link).toHaveAttribute('href', '/capacidade')
  })
})
