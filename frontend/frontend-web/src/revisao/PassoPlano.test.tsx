import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as planoApi from '../planner/api/planoApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import { plano } from '../test/revisaoFixtures'
import { PassoPlano } from './PassoPlano'

vi.mock('../planner/api/planoApi', () => ({
  gerarPlano: vi.fn(),
  buscarPlano: vi.fn(),
  aceitarProposta: vi.fn(),
  descartarProposta: vi.fn(),
}))

/** Sexta, 13/11/2026 → próxima segunda 16/11. */
const HOJE = '2026-11-13'

const segunda = plano({
  id: 'p-seg',
  data: '2026-11-16',
  blocos: [
    {
      id: 'b-1',
      duracaoMinutos: 90,
      ordem: 0,
      origem: 'TAREFA',
      tarefa: {
        id: 't-1',
        titulo: 'Escrever proposta',
        prioridade: 'ALTA',
        status: 'PENDENTE',
        duracaoMinutos: 90,
        prazo: null,
      },
    },
  ],
})
const terca = plano({ id: 'p-ter', data: '2026-11-17' })

function servirDias(dias: PlanoResponse[]) {
  vi.mocked(planoApi.buscarPlano).mockImplementation(
    async ({ data }) => dias.find((d) => d.data === data) ?? null,
  )
}

function renderPasso(hojeIso = HOJE) {
  const onDesfecho = vi.fn()
  render(
    <MemoryRouter>
      <PassoPlano hojeIso={hojeIso} onDesfecho={onDesfecho} />
    </MemoryRouter>,
  )
  return { onDesfecho }
}

describe('PassoPlano', () => {
  beforeEach(() => {
    vi.mocked(planoApi.gerarPlano).mockReset()
    vi.mocked(planoApi.buscarPlano).mockReset()
    vi.mocked(planoApi.aceitarProposta).mockReset()
    vi.mocked(planoApi.descartarProposta).mockReset()
  })

  it('gera a partir da próxima segunda e mostra os dias da proposta', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(segunda)
    servirDias([segunda, terca])
    renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))

    expect(await screen.findByText(/Escrever proposta/)).toBeInTheDocument()
    expect(planoApi.gerarPlano).toHaveBeenCalledWith('2026-11-16')
    expect(screen.getByText(/seg 16\/11/i)).toBeInTheDocument()
    expect(screen.getByText(/ter 17\/11/i)).toBeInTheDocument()
  })

  it('no domingo a proposta começa amanhã', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(segunda)
    servirDias([segunda])
    renderPasso('2026-11-15')

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))

    await waitFor(() => expect(planoApi.gerarPlano).toHaveBeenCalledWith('2026-11-16'))
  })

  it('aprovar aceita a proposta uma única vez mesmo com clique duplo', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(segunda)
    servirDias([segunda, terca])
    let resolver: (p: PlanoResponse) => void = () => {}
    vi.mocked(planoApi.aceitarProposta).mockReturnValue(
      new Promise((resolve) => {
        resolver = resolve
      }),
    )
    const { onDesfecho } = renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))
    const aprovar = await screen.findByRole('button', { name: /aprovar plano/i })
    await user.dblClick(aprovar)

    expect(planoApi.aceitarProposta).toHaveBeenCalledTimes(1)
    expect(planoApi.aceitarProposta).toHaveBeenCalledWith('prop-1')
    expect(screen.getByRole('button', { name: /aprovando/i })).toHaveAttribute('aria-busy', 'true')
    resolver({ ...segunda, status: 'APROVADO' })
    await waitFor(() => expect(onDesfecho).toHaveBeenCalledWith('APROVADO'))
  })

  it('não aprovar descarta a proposta', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(segunda)
    servirDias([segunda])
    vi.mocked(planoApi.descartarProposta).mockResolvedValue()
    const { onDesfecho } = renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))
    await user.click(await screen.findByRole('button', { name: /não aprovar agora/i }))

    await waitFor(() => expect(onDesfecho).toHaveBeenCalledWith('RECUSADO'))
    expect(planoApi.descartarProposta).toHaveBeenCalledWith('prop-1')
    expect(planoApi.aceitarProposta).not.toHaveBeenCalled()
  })

  it('semana já aprovada (409) mostra link para /plano sem aceitar nada', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockRejectedValue(new ApiError(409, 'Plano já aprovado'))
    servirDias([{ ...segunda, status: 'APROVADO', propostaId: null }])
    const { onDesfecho } = renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))

    expect(await screen.findByText(/já tem plano aprovado/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /ver plano/i })).toHaveAttribute('href', '/plano')
    expect(planoApi.aceitarProposta).not.toHaveBeenCalled()
    expect(onDesfecho).not.toHaveBeenCalled()
  })

  it('409 que não é semana aprovada mostra a mensagem do servidor', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockRejectedValue(
      new ApiError(409, 'Versão de plano já existe'),
    )
    servirDias([])
    renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Versão de plano já existe')
    expect(screen.getByRole('button', { name: /gerar plano/i })).toBeEnabled()
  })

  it('sem capacidade mostra a mensagem do planner e os itens não alocados', async () => {
    const user = userEvent.setup()
    const vazio = plano({
      id: 'p-vazio',
      data: '2026-11-16',
      mensagem: 'Sem tempo disponível na janela.',
      itensNaoAlocados: [
        { tipo: 'TAREFA', id: 't-9', titulo: 'Declarar imposto', duracaoMinutos: 120, motivo: 'SEM_CAPACIDADE' },
      ],
    })
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(vazio)
    servirDias([vazio])
    renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))

    expect(await screen.findByText('Sem tempo disponível na janela.')).toBeInTheDocument()
    expect(screen.getByText(/não coube: declarar imposto/i)).toBeInTheDocument()
  })

  it('proposta resolvida em outra aba volta para Gerar plano com a mensagem', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(segunda)
    servirDias([segunda])
    vi.mocked(planoApi.aceitarProposta).mockRejectedValue(new ApiError(409, 'Proposta inválida'))
    const { onDesfecho } = renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))
    await user.click(await screen.findByRole('button', { name: /aprovar plano/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Proposta inválida')
    expect(screen.getByRole('button', { name: /gerar plano/i })).toBeInTheDocument()
    expect(onDesfecho).not.toHaveBeenCalled()
  })

  it('erro 500 ao gerar mantém o botão e mostra o texto padrão', async () => {
    const user = userEvent.setup()
    vi.mocked(planoApi.gerarPlano).mockRejectedValue(new TypeError('Failed to fetch'))
    renderPasso()

    await user.click(screen.getByRole('button', { name: /gerar plano/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível gerar o plano.')
  })
})
