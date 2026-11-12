import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as execucaoApi from '../../execucao/api/execucaoApi'
import { SessaoAtivaProvider } from '../../execucao/SessaoAtivaContext'
import { ToastProvider } from '../../feedback/ToastProvider'
import { XpProgressProvider } from '../../xp/XpProgressContext'
import * as habitoApi from '../api/habitoApi'
import { HabitoDrawer } from '../HabitoDrawer'
import { HabitosPage } from './HabitosPage'

vi.mock('../../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

vi.mock('../../auth/api/meApi', () => ({
  buscarMe: vi.fn().mockResolvedValue({
    id: 'u-1',
    nome: 'Teste',
    email: 't@t.com',
    xpTotal: 0,
    nivel: 1,
    xpNoNivel: 0,
    xpParaProximoNivel: 10,
  }),
  atualizarMe: vi.fn(),
}))

vi.mock('../../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../execucao/api/execucaoApi')>()
  return {
    ...actual,
    listarExecucoes: vi.fn().mockResolvedValue({ planoAprovado: null, itens: [] }),
    concluirExecucao: vi.fn(),
    abortarExecucao: vi.fn(),
  }
})

vi.mock('../../planner/data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../planner/data')>()
  return {
    ...actual,
    dataHojeIso: () => '2026-11-11',
  }
})

vi.mock('../api/habitoApi', () => ({
  listarHabitos: vi.fn(),
  pausarHabito: vi.fn(),
  reativarHabito: vi.fn(),
  excluirHabito: vi.fn(),
  restaurarHabito: vi.fn(),
  criarHabito: vi.fn(),
  buscarHabito: vi.fn(),
  atualizarHabito: vi.fn(),
  iniciarHabitoAvulso: vi.fn(),
  concluirHabitoDireto: vi.fn(),
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
      <XpProgressProvider>
        <SessaoAtivaProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/habitos" element={<HabitosPage />}>
                <Route path="nova" element={<HabitoDrawer />} />
                <Route path=":id" element={<HabitoDrawer />} />
              </Route>
            </Routes>
          </MemoryRouter>
        </SessaoAtivaProvider>
      </XpProgressProvider>
    </ToastProvider>,
  )
}

describe('HabitosPage', () => {
  beforeEach(() => {
    vi.mocked(habitoApi.listarHabitos).mockReset()
    vi.mocked(habitoApi.pausarHabito).mockReset()
    vi.mocked(habitoApi.reativarHabito).mockReset()
    vi.mocked(habitoApi.excluirHabito).mockReset()
    vi.mocked(habitoApi.iniciarHabitoAvulso).mockReset()
    vi.mocked(habitoApi.concluirHabitoDireto).mockReset()
    vi.mocked(execucaoApi.listarExecucoes).mockReset()
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({ planoAprovado: null, itens: [] })
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

  describe('sessão Iniciar / Concluir', () => {
    it('card ativo do dia oferece Iniciar e Concluir', async () => {
      renderLista()

      expect(await screen.findByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir correr/i })).toBeInTheDocument()
    })

    it('Iniciar chama a API, confirma a sessão e troca para Finalizar e Pausar', async () => {
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-11-11T15:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      })
      const user = userEvent.setup()
      renderLista()

      await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))

      await waitFor(() => {
        expect(habitoApi.iniciarHabitoAvulso).toHaveBeenCalledWith('h-correr', '2026-11-11')
      })
      expect(await screen.findByText('Sessão iniciada')).toBeInTheDocument()
      const cardCorrer = screen.getByRole('link', { name: 'Correr' }).closest('li')
      expect(cardCorrer).not.toBeNull()
      expect(
        within(cardCorrer as HTMLElement).queryByRole('button', { name: /iniciar correr/i }),
      ).not.toBeInTheDocument()
      expect(
        within(cardCorrer as HTMLElement).queryByRole('button', { name: /concluir correr/i }),
      ).not.toBeInTheDocument()
      expect(
        within(cardCorrer as HTMLElement).getByRole('button', { name: /finalizar correr/i }),
      ).toBeInTheDocument()
      expect(
        within(cardCorrer as HTMLElement).getByRole('button', { name: /pausar sessão de correr/i }),
      ).toBeInTheDocument()
    })

    it('Finalizar conclui a execução em andamento e volta Iniciar/Concluir', async () => {
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-11-11T15:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      })
      vi.mocked(execucaoApi.concluirExecucao).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'CONCLUIDO',
        iniciadoEm: '2026-11-11T15:00:00Z',
        concluidoEm: '2026-11-11T15:20:00Z',
        abortadoEm: null,
        xp: {
          ganhoNesteRequest: 10,
          eventos: [{ tipo: 'BLOCO_CONCLUIDO', quantidade: 10 }],
          progresso: { xpTotal: 10, nivel: 1, xpNoNivel: 10, xpParaProximoNivel: 100 },
        },
      })
      const user = userEvent.setup()
      renderLista()

      await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))
      await user.click(await screen.findByRole('button', { name: /finalizar correr/i }))

      await waitFor(() => {
        expect(execucaoApi.concluirExecucao).toHaveBeenCalledWith('e-1')
      })
      expect(await screen.findByText('+10 XP')).toBeInTheDocument()
      expect(await screen.findByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir correr/i })).toBeInTheDocument()
    })

    it('Pausar aborta a sessão e volta Iniciar/Concluir', async () => {
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-11-11T15:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      })
      vi.mocked(execucaoApi.abortarExecucao).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'ABORTADO',
        iniciadoEm: '2026-11-11T15:00:00Z',
        concluidoEm: null,
        abortadoEm: '2026-11-11T15:05:00Z',
      })
      const user = userEvent.setup()
      renderLista()

      await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))
      await user.click(await screen.findByRole('button', { name: /pausar sessão de correr/i }))

      await waitFor(() => {
        expect(execucaoApi.abortarExecucao).toHaveBeenCalledWith('e-1')
      })
      expect(await screen.findByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir correr/i })).toBeInTheDocument()
    })

    it('sessão já em andamento no dia mostra Finalizar e Pausar', async () => {
      vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
        planoAprovado: null,
        itens: [
          {
            bloco: { id: 'b-1', duracaoMinutos: 30, ordem: 1 },
            tarefaResumo: null,
            habitoResumo: {
              id: 'h-correr',
              titulo: 'Correr',
              horarioPreferencial: '07:00:00',
              streakAtual: 4,
            },
            execucao: {
              id: 'e-1',
              blocoPlanoId: 'b-1',
              status: 'EM_ANDAMENTO',
              iniciadoEm: '2026-11-11T15:00:00Z',
              concluidoEm: null,
              abortadoEm: null,
            },
          },
        ],
      })
      renderLista()

      expect(await screen.findByRole('button', { name: /finalizar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /pausar sessão de correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /iniciar correr/i })).not.toBeInTheDocument()
    })

    it('Concluir chama a API e mostra o XP ganho', async () => {
      vi.mocked(habitoApi.concluirHabitoDireto).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'CONCLUIDO',
        iniciadoEm: null,
        concluidoEm: '2026-11-11T15:20:00Z',
        abortadoEm: null,
        xp: {
          ganhoNesteRequest: 10,
          eventos: [{ tipo: 'BLOCO_CONCLUIDO', quantidade: 10 }],
          progresso: { xpTotal: 10, nivel: 1, xpNoNivel: 10, xpParaProximoNivel: 100 },
        },
      })
      const user = userEvent.setup()
      renderLista()

      await user.click(await screen.findByRole('button', { name: /concluir correr/i }))

      await waitFor(() => {
        expect(habitoApi.concluirHabitoDireto).toHaveBeenCalledWith('h-correr', '2026-11-11')
      })
      expect(await screen.findByText('+10 XP')).toBeInTheDocument()
    })

    it('excluir outro hábito mantém Finalizar e Pausar na sessão ativa', async () => {
      const correr = {
        ...pagina.content[0],
        diasSemana: [...pagina.content[0].diasSemana],
      }
      const meditar = {
        ...pagina.content[0],
        id: 'h-meditar',
        titulo: 'Meditar',
        diasSemana: ['QUARTA'] as const,
      }
      vi.mocked(habitoApi.listarHabitos)
        .mockResolvedValueOnce({
          ...pagina,
          totalElements: 2,
          content: [correr, { ...meditar, diasSemana: [...meditar.diasSemana] }],
        })
        .mockImplementation(
          () =>
            new Promise((resolve) => {
              setTimeout(() => {
                resolve({
                  ...pagina,
                  content: [correr],
                })
              }, 30)
            }),
        )
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockResolvedValue({
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-11-11T15:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      })
      vi.mocked(habitoApi.excluirHabito).mockResolvedValue()
      const user = userEvent.setup()
      renderLista()

      await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))
      expect(await screen.findByRole('button', { name: /finalizar correr/i })).toBeInTheDocument()

      const cardMeditar = screen.getByRole('link', { name: 'Meditar' }).closest('li')
      expect(cardMeditar).not.toBeNull()
      await user.click(within(cardMeditar as HTMLElement).getByRole('button', { name: /^excluir$/i }))
      const dialog = await screen.findByRole('dialog')
      await user.click(within(dialog).getByRole('button', { name: /^excluir$/i }))

      await waitFor(() => {
        expect(habitoApi.excluirHabito).toHaveBeenCalledWith('h-meditar')
      })
      expect(await screen.findByRole('link', { name: 'Correr' })).toBeInTheDocument()
      await waitFor(() => {
        expect(screen.queryByRole('link', { name: 'Meditar' })).not.toBeInTheDocument()
      })
      expect(screen.getByRole('button', { name: /finalizar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /pausar sessão de correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /iniciar correr/i })).not.toBeInTheDocument()
    })

    it('não oferece sessão em hábito pausado, excluído ou fora do dia', async () => {
      vi.mocked(habitoApi.listarHabitos).mockResolvedValue({
        ...pagina,
        totalElements: 3,
        content: [
          {
            ...pagina.content[0],
            id: 'h-pausado',
            titulo: 'Pausado',
            diasSemana: ['QUARTA'],
            status: 'PAUSADO',
          },
          {
            ...pagina.content[0],
            id: 'h-excluido',
            titulo: 'Excluído',
            diasSemana: ['QUARTA'],
            deletada: true,
            deletedAt: '2026-11-10T10:00:00Z',
          },
          {
            ...pagina.content[0],
            id: 'h-domingo',
            titulo: 'Só domingo',
            diasSemana: ['DOMINGO'],
          },
        ],
      })
      renderLista()

      expect(await screen.findByRole('link', { name: 'Pausado' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /iniciar pausado/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /iniciar excluído/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /iniciar só domingo/i })).not.toBeInTheDocument()
    })
  })
})
