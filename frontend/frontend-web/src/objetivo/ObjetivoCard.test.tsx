import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { ObjetivoResponse } from './api/objetivoApi'
import { ObjetivoCard, type AcaoObjetivo } from './ObjetivoCard'

const HOJE = '2026-11-17'

function objetivo(parcial: Partial<ObjetivoResponse> = {}): ObjetivoResponse {
  return {
    id: 'obj-1',
    titulo: 'Perder 5 kg',
    descricao: null,
    prazo: null,
    status: 'ATIVO',
    concluidoEm: null,
    arquivadoEm: null,
    createdAt: '2026-11-01T12:00:00Z',
    updatedAt: '2026-11-01T12:00:00Z',
    progresso: null,
    ...parcial,
  }
}

type OnAcao = (acao: AcaoObjetivo, objetivo: ObjetivoResponse) => void

function renderCard(
  obj: ObjetivoResponse,
  {
    acaoEmAndamento = null,
    bloqueado = acaoEmAndamento != null,
  }: { acaoEmAndamento?: AcaoObjetivo | null; bloqueado?: boolean } = {},
) {
  const onAcao = vi.fn<OnAcao>()
  render(
    <MemoryRouter initialEntries={['/objetivos?status=CONCLUIDO']}>
      <ul>
        <ObjetivoCard
          objetivo={obj}
          hojeIso={HOJE}
          acaoEmAndamento={acaoEmAndamento}
          bloqueado={bloqueado}
          onAcao={onAcao}
        />
      </ul>
    </MemoryRouter>,
  )
  return { card: screen.getByRole('listitem'), onAcao }
}

function nomesDosBotoes(card: HTMLElement): string[] {
  return within(card)
    .getAllByRole('button')
    .map((b) => b.textContent ?? '')
}

describe('ObjetivoCard', () => {
  it('ativo com prazo futuro mostra prazo, descrição e ações Concluir/Arquivar', async () => {
    const user = userEvent.setup()
    const { card, onAcao } = renderCard(
      objetivo({ prazo: '2026-11-20', descricao: 'Correr 3x por semana' }),
    )

    expect(card).toHaveTextContent('Até 20 nov · faltam 3 dias')
    expect(card).toHaveTextContent('Correr 3x por semana')
    expect(nomesDosBotoes(card)).toEqual(['Concluir', 'Arquivar'])
    expect(within(card).getByRole('link', { name: /editar/i })).toHaveAttribute(
      'href',
      '/objetivos/obj-1?status=CONCLUIDO',
    )

    await user.click(within(card).getByRole('button', { name: 'Concluir' }))
    expect(onAcao).toHaveBeenCalledWith('concluir', expect.objectContaining({ id: 'obj-1' }))
  })

  it('ativo sem prazo não mostra linha de prazo', () => {
    const { card } = renderCard(objetivo())

    expect(card).not.toHaveTextContent(/até|vence|prazo vencido/i)
  })

  it('ativo vencido é destacado com texto "Prazo vencido"', () => {
    const { card } = renderCard(objetivo({ prazo: '2026-11-16' }))

    expect(card).toHaveTextContent('Prazo vencido · 16 nov')
    expect(card).toHaveAttribute('data-vencido', 'true')
    expect(card).toHaveClass('objetivo-card--vencido')
  })

  it('prazo de hoje mostra "Vence hoje" sem destaque de vencido', () => {
    const { card } = renderCard(objetivo({ prazo: HOJE }))

    expect(card).toHaveTextContent('Vence hoje')
    expect(card).not.toHaveAttribute('data-vencido')
  })

  it('concluído com prazo passado mostra a conclusão, sem destaque e com Arquivar/Reabrir', () => {
    const { card } = renderCard(
      objetivo({
        status: 'CONCLUIDO',
        prazo: '2026-11-01',
        concluidoEm: '2026-11-12T15:00:00Z',
      }),
    )

    expect(card).toHaveTextContent('Concluído em 12 nov')
    expect(card).not.toHaveTextContent(/prazo vencido/i)
    expect(card).not.toHaveAttribute('data-vencido')
    expect(nomesDosBotoes(card)).toEqual(['Arquivar', 'Reabrir'])
  })

  it('arquivado fica atenuado e só oferece Reabrir', () => {
    const { card } = renderCard(
      objetivo({ status: 'ARQUIVADO', prazo: '2026-11-01', arquivadoEm: '2026-11-10T12:00:00Z' }),
    )

    expect(card).toHaveClass('objetivo-card--arquivado')
    expect(card).not.toHaveAttribute('data-vencido')
    expect(nomesDosBotoes(card)).toEqual(['Reabrir'])
  })

  it('espaço de progresso não mostra percentual nem barra de progresso', () => {
    const { card } = renderCard(objetivo())

    expect(within(card).getByTestId('objetivo-progresso')).toHaveTextContent(
      'Progresso aparece quando houver itens vinculados',
    )
    expect(within(card).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(card).not.toHaveTextContent('%')
  })

  it('com ação em andamento desabilita todos os botões e mostra o rótulo de progresso', () => {
    const { card } = renderCard(objetivo(), { acaoEmAndamento: 'arquivar' })

    const botoes = within(card).getAllByRole('button')
    expect(botoes.every((b) => (b as HTMLButtonElement).disabled)).toBe(true)
    expect(nomesDosBotoes(card)).toEqual(['Concluir', 'Arquivando…'])
  })

  it('bloqueado por ação em outro card desabilita os botões sem trocar os rótulos', () => {
    const { card } = renderCard(objetivo(), { bloqueado: true })

    const botoes = within(card).getAllByRole('button')
    expect(botoes.every((b) => (b as HTMLButtonElement).disabled)).toBe(true)
    expect(nomesDosBotoes(card)).toEqual(['Concluir', 'Arquivar'])
  })

  it('descrição longa é truncada em 120 caracteres', () => {
    const { card } = renderCard(objetivo({ descricao: 'x'.repeat(200) }))

    expect(card).toHaveTextContent(`${'x'.repeat(119)}…`)
    expect(card).not.toHaveTextContent('x'.repeat(120))
  })
})
