import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as decidirApi from '../decidir/api/decidirApi'
import * as historicoApi from '../historico/api/historicoApi'
import * as planoApi from '../planner/api/planoApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import type { ListarTarefasParams } from '../tarefa/api/tarefaApi'
import { itemDecidir, pagina, paginaDecidir, plano, tarefa } from '../test/revisaoFixtures'
import { RevisaoPage } from './RevisaoPage'

vi.mock('../historico/api/historicoApi', () => ({ consultarHistoricoSemana: vi.fn() }))
vi.mock('../tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  atualizarTarefa: vi.fn(),
  excluirEsperaTarefa: vi.fn(),
}))
vi.mock('../decidir/api/decidirApi', () => ({
  listarDecidir: vi.fn(),
  excluirDecidir: vi.fn(),
  promoverDecidir: vi.fn(),
}))
vi.mock('../planner/api/planoApi', () => ({
  gerarPlano: vi.fn(),
  buscarPlano: vi.fn(),
  aceitarProposta: vi.fn(),
  descartarProposta: vi.fn(),
}))

const RESUMO = {
  periodo: { escopo: 'SEMANA' as const, inicio: '2026-11-09', fim: '2026-11-15' },
  tarefasFinalizadas: 4,
  blocosConcluidos: 6,
  minutosTrabalhados: 240,
  xpGanho: 120,
}
const t1 = tarefa({ id: 't-1', titulo: 'Atrasada um' })
const t2 = tarefa({ id: 't-2', titulo: 'Atrasada dois' })
const item = itemDecidir({ id: 'd-1', titulo: 'Assinar academia' })
const proposta = plano({ id: 'p-1', data: '2026-11-16' })

const ESCRITAS = [
  tarefaApi.atualizarTarefa,
  tarefaApi.excluirEsperaTarefa,
  decidirApi.excluirDecidir,
  decidirApi.promoverDecidir,
  planoApi.gerarPlano,
  planoApi.aceitarProposta,
  planoApi.descartarProposta,
]

let atrasadas = [t1, t2]

function renderPage() {
  render(
    <MemoryRouter>
      <RevisaoPage hojeIso="2026-11-13" />
    </MemoryRouter>,
  )
}

async function continuar(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /^continuar$/i }))
}

describe('RevisaoPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    atrasadas = [t1, t2]
    vi.mocked(historicoApi.consultarHistoricoSemana).mockResolvedValue(RESUMO)
    vi.mocked(tarefaApi.listarTarefas).mockImplementation(async (f?: ListarTarefasParams) =>
      pagina(f?.bloqueada ? [] : atrasadas),
    )
    vi.mocked(tarefaApi.atualizarTarefa).mockImplementation(async (id) => {
      atrasadas = atrasadas.filter((t) => t.id !== id)
      return tarefa({ id, status: 'PENDENTE' })
    })
    vi.mocked(decidirApi.listarDecidir)
      .mockResolvedValueOnce(paginaDecidir([item]))
      .mockResolvedValue(paginaDecidir([]))
    vi.mocked(decidirApi.promoverDecidir).mockResolvedValue({ ...item, tarefaId: 't-9' })
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(proposta)
    vi.mocked(planoApi.buscarPlano).mockImplementation(async ({ data }) =>
      data === proposta.data ? proposta : null,
    )
    vi.mocked(planoApi.aceitarProposta).mockResolvedValue({ ...proposta, status: 'APROVADO' })
  })

  it('percorre os 4 passos e termina com o plano aprovado e o resumo da sessão', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(screen.getByText('Passo 1 de 4')).toBeInTheDocument()
    expect(await screen.findByTestId('semana-tarefas')).toHaveTextContent('4')
    await continuar(user)

    expect(screen.getByText('Passo 2 de 4')).toBeInTheDocument()
    for (const titulo of ['Atrasada um', 'Atrasada dois']) {
      const linha = (await screen.findByText(titulo)).closest('li') as HTMLElement
      await user.click(within(linha).getByRole('button', { name: /reagendar/i }))
      await user.type(within(linha).getByLabelText(/novo prazo/i), '2026-11-20')
      await user.click(within(linha).getByRole('button', { name: /salvar prazo/i }))
      await waitFor(() => expect(screen.queryByText(titulo)).not.toBeInTheDocument())
    }
    await continuar(user)

    expect(screen.getByText('Passo 3 de 4')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: /promover/i }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /^promover$/i }))
    expect(await screen.findByText(/fila decidir vazia/i)).toBeInTheDocument()
    await continuar(user)

    expect(screen.getByText('Passo 4 de 4')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^continuar$/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /gerar plano/i }))
    await user.click(await screen.findByRole('button', { name: /aprovar plano/i }))

    expect(await screen.findByText('Revisão concluída')).toBeInTheDocument()
    expect(screen.getByText('2 tarefas tratadas')).toBeInTheDocument()
    expect(screen.getByText('1 item decidido')).toBeInTheDocument()
    expect(screen.getByTestId('revisao-desfecho')).toHaveTextContent('Plano da semana aprovado.')
  })

  it('pular os 4 passos chega à conclusão sem nenhuma escrita', async () => {
    const user = userEvent.setup()
    renderPage()

    for (let i = 0; i < 4; i++) {
      await user.click(screen.getByRole('button', { name: /^pular$/i }))
    }

    expect(await screen.findByText('Revisão concluída')).toBeInTheDocument()
    expect(screen.getByTestId('revisao-desfecho')).toHaveTextContent(/não foi gerado/i)
    expect(screen.getByText('0 tarefas tratadas')).toBeInTheDocument()
    for (const escrita of ESCRITAS) {
      expect(vi.mocked(escrita)).not.toHaveBeenCalled()
    }
  })

  it('voltar do passo 3 recarrega o passo 2 com dados atuais', async () => {
    const user = userEvent.setup()
    renderPage()

    await continuar(user)
    await screen.findByText('Atrasada um')
    const chamadasAntes = vi.mocked(tarefaApi.listarTarefas).mock.calls.length
    await continuar(user)
    atrasadas = [t2]
    await user.click(screen.getByRole('button', { name: /^voltar$/i }))

    expect(screen.getByText('Passo 2 de 4')).toBeInTheDocument()
    expect(await screen.findByText('Atrasada dois')).toBeInTheDocument()
    expect(screen.queryByText('Atrasada um')).not.toBeInTheDocument()
    expect(vi.mocked(tarefaApi.listarTarefas).mock.calls.length).toBeGreaterThan(chamadasAntes)
  })

  it('não mostra Voltar no primeiro passo', () => {
    renderPage()

    expect(screen.queryByRole('button', { name: /^voltar$/i })).not.toBeInTheDocument()
  })

  it('não aprovar termina com o desfecho de recusa explícita', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.descartarProposta).mockResolvedValue()
    renderPage()

    for (let i = 0; i < 3; i++) {
      await user.click(screen.getByRole('button', { name: /^pular$/i }))
    }
    await user.click(screen.getByRole('button', { name: /gerar plano/i }))
    await user.click(await screen.findByRole('button', { name: /não aprovar agora/i }))

    expect(await screen.findByTestId('revisao-desfecho')).toHaveTextContent(
      /você escolheu não aprovar/i,
    )
  })

  it('conclusão oferece links para plano e tarefas', async () => {
    const user = userEvent.setup()
    renderPage()

    for (let i = 0; i < 4; i++) {
      await user.click(screen.getByRole('button', { name: /^pular$/i }))
    }

    expect(screen.getByRole('link', { name: /ir para o plano/i })).toHaveAttribute('href', '/plano')
    expect(screen.getByRole('link', { name: /ver tarefas/i })).toHaveAttribute('href', '/tarefas')
  })

  it('erro em um passo não impede pular para o próximo', async () => {
    const user = userEvent.setup()
    vi.mocked(historicoApi.consultarHistoricoSemana).mockRejectedValue(new Error('rede'))
    renderPage()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^pular$/i }))

    expect(screen.getByText('Passo 2 de 4')).toBeInTheDocument()
  })
})
