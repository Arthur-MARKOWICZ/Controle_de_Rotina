import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import type { ListarTarefasParams, PageTarefa } from '../tarefa/api/tarefaApi'
import { pagina, tarefa } from '../test/revisaoFixtures'
import { PassoPendencias } from './PassoPendencias'

vi.mock('../tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  atualizarTarefa: vi.fn(),
  excluirEsperaTarefa: vi.fn(),
}))

const atrasada = tarefa({ id: 't-atrasada', titulo: 'Relatório mensal' })
const emEspera = tarefa({
  id: 't-espera',
  titulo: 'Publicar artigo',
  status: 'PENDENTE',
  bloqueada: true,
  esperas: [
    { id: 'e-1', alvoTipo: 'TAREFA', alvoId: 't-alvo', alvoTitulo: 'Revisão do editor', resolvida: false },
  ],
})
const herdada = tarefa({
  id: 't-herdada',
  titulo: 'Tarefa do projeto',
  status: 'PENDENTE',
  bloqueada: true,
  projetoId: 'p-1',
  projetoNome: 'Mudança',
})

/** Responde por filtro: `bloqueada=true` devolve a lista de espera; o resto, as atrasadas. */
function servir(atrasadas: PageTarefa[], bloqueadas: PageTarefa[]) {
  const filaA = [...atrasadas]
  const filaB = [...bloqueadas]
  vi.mocked(tarefaApi.listarTarefas).mockImplementation(async (filtro?: ListarTarefasParams) => {
    const fila = filtro?.bloqueada ? filaB : filaA
    return (fila.length > 1 ? fila.shift() : fila[0]) as PageTarefa
  })
}

function renderPasso() {
  const onTratou = vi.fn()
  render(
    <MemoryRouter>
      <PassoPendencias onTratou={onTratou} />
    </MemoryRouter>,
  )
  return { onTratou }
}

describe('PassoPendencias', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.atualizarTarefa).mockReset()
    vi.mocked(tarefaApi.excluirEsperaTarefa).mockReset()
  })

  it('separa atrasadas e em espera, com o alvo resumido', async () => {
    servir([pagina([atrasada])], [pagina([emEspera])])

    renderPasso()

    const atrasadas = await screen.findByRole('region', { name: /atrasadas/i })
    const espera = screen.getByRole('region', { name: /em espera/i })
    expect(within(atrasadas).getByText('Relatório mensal')).toBeInTheDocument()
    expect(within(espera).getByText('Publicar artigo')).toBeInTheDocument()
    expect(within(espera).getByText(/Espera Revisão do editor/)).toBeInTheDocument()
    expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(expect.objectContaining({ bloqueada: true }))
  })

  it('mostra estado vazio quando não há nada pendente', async () => {
    servir([pagina([])], [pagina([])])

    renderPasso()

    expect(await screen.findByText(/nada atrasado nem em espera/i)).toHaveAttribute('role', 'status')
  })

  it('reagendar envia o fim do dia em BRT e tira a tarefa do grupo', async () => {
    const user = userEvent.setup()
    servir([pagina([atrasada]), pagina([])], [pagina([])])
    vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({ ...atrasada, status: 'PENDENTE' })
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /reagendar/i }))
    await user.type(screen.getByLabelText(/novo prazo de relatório mensal/i), '2026-11-20')
    await user.click(screen.getByRole('button', { name: /salvar prazo/i }))

    await waitFor(() =>
      expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith('t-atrasada', {
        prazo: '2026-11-21T02:59:00.000Z',
      }),
    )
    expect(onTratou).toHaveBeenCalledTimes(1)
    expect(await screen.findByText(/nada atrasado/i)).toBeInTheDocument()
  })

  it('cancelar a confirmação de descarte não altera a tarefa', async () => {
    const user = userEvent.setup()
    servir([pagina([atrasada])], [pagina([])])
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /^descartar$/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /cancelar/i }))

    expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
    expect(onTratou).not.toHaveBeenCalled()
    expect(screen.getByText('Relatório mensal')).toBeInTheDocument()
  })

  it('descartar confirmado cancela a tarefa', async () => {
    const user = userEvent.setup()
    servir([pagina([atrasada]), pagina([])], [pagina([])])
    vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({ ...atrasada, status: 'CANCELADA' })
    renderPasso()

    await user.click(await screen.findByRole('button', { name: /^descartar$/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^descartar$/i }))

    await waitFor(() =>
      expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith('t-atrasada', { status: 'CANCELADA' }),
    )
  })

  it('prazo no passado (400) mantém a tarefa e mostra a mensagem', async () => {
    const user = userEvent.setup()
    servir([pagina([atrasada])], [pagina([])])
    vi.mocked(tarefaApi.atualizarTarefa).mockRejectedValue(
      new ApiError(400, 'O prazo não pode estar no passado.'),
    )
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /reagendar/i }))
    await user.type(screen.getByLabelText(/novo prazo/i), '2020-01-01')
    await user.click(screen.getByRole('button', { name: /salvar prazo/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('O prazo não pode estar no passado.')
    expect(screen.getByText('Relatório mensal')).toBeInTheDocument()
    expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(2)
    expect(onTratou).not.toHaveBeenCalled()
  })

  it('tarefa deletada em outra aba (404) mostra não encontrada e sai da lista', async () => {
    const user = userEvent.setup()
    servir([pagina([atrasada]), pagina([])], [pagina([])])
    vi.mocked(tarefaApi.atualizarTarefa).mockRejectedValue(
      new ApiError(404, 'Tarefa não encontrada.'),
    )
    renderPasso()

    await user.click(await screen.findByRole('button', { name: /reagendar/i }))
    await user.type(screen.getByLabelText(/novo prazo/i), '2026-11-20')
    await user.click(screen.getByRole('button', { name: /salvar prazo/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Tarefa não encontrada.')
    await waitFor(() => expect(screen.queryByText('Relatório mensal')).not.toBeInTheDocument())
    expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(4)
  })

  it('remover a única espera tira a tarefa do grupo', async () => {
    const user = userEvent.setup()
    servir([pagina([])], [pagina([emEspera]), pagina([])])
    vi.mocked(tarefaApi.excluirEsperaTarefa).mockResolvedValue()
    const { onTratou } = renderPasso()

    await user.click(await screen.findByRole('button', { name: /remover espera de “revisão do editor”/i }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: /^remover$/i }))

    await waitFor(() => expect(tarefaApi.excluirEsperaTarefa).toHaveBeenCalledWith('t-espera', 'e-1'))
    expect(onTratou).toHaveBeenCalledTimes(1)
    expect(await screen.findByText(/nada atrasado nem em espera/i)).toHaveAttribute('role', 'status')
  })

  it('bloqueio herdado do projeto indica a origem sem oferecer remover espera', async () => {
    servir([pagina([])], [pagina([herdada])])

    renderPasso()

    expect(await screen.findByText(/espera herdada do projeto “mudança”/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /remover espera/i })).not.toBeInTheDocument()
  })

  it('não repete em Atrasadas a tarefa atrasada que também está bloqueada', async () => {
    const atrasadaBloqueada = { ...emEspera, status: 'ATRASADA' as const }
    servir([pagina([atrasadaBloqueada])], [pagina([atrasadaBloqueada])])

    renderPasso()

    const atrasadas = await screen.findByRole('region', { name: /atrasadas/i })
    expect(within(atrasadas).queryByText('Publicar artigo')).not.toBeInTheDocument()
  })

  it('falha ao carregar mostra erro e permite tentar de novo', async () => {
    const user = userEvent.setup()
    vi.mocked(tarefaApi.listarTarefas).mockRejectedValueOnce(new ApiError(500, 'Erro interno.'))
    renderPasso()

    expect(await screen.findByRole('alert')).toHaveTextContent('Erro interno.')
    servir([pagina([atrasada])], [pagina([])])
    await user.click(screen.getByRole('button', { name: /tentar de novo/i }))
    expect(await screen.findByText('Relatório mensal')).toBeInTheDocument()
  })
})
