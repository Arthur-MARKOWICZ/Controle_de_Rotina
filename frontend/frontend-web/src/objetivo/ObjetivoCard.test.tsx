import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { ObjetivoResponse } from './api/objetivoApi'
import type { AcaoObjetivo } from './acoesObjetivo'
import { ObjetivoCard } from './ObjetivoCard'

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
      '/objetivos/obj-1/editar',
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

  it('arquivado direto de ativo mostra só a data de arquivamento', () => {
    const { card } = renderCard(
      objetivo({ status: 'ARQUIVADO', concluidoEm: null, arquivadoEm: '2026-11-15T15:00:00Z' }),
    )

    expect(card).toHaveTextContent('Arquivado em 15 nov')
    expect(card).not.toHaveTextContent('Concluído em')
  })

  it('concluído e depois arquivado mostra as duas datas, com a conclusão primeiro', () => {
    const { card } = renderCard(
      objetivo({
        status: 'ARQUIVADO',
        concluidoEm: '2026-11-05T15:00:00Z',
        arquivadoEm: '2026-11-15T15:00:00Z',
      }),
    )

    expect(card).toHaveTextContent('Concluído em 5 nov · Arquivado em 15 nov')
  })

  it('concluído mostra só a conclusão e não a data de arquivamento', () => {
    const { card } = renderCard(
      objetivo({ status: 'CONCLUIDO', concluidoEm: '2026-11-12T15:00:00Z' }),
    )

    expect(card).toHaveTextContent('Concluído em 12 nov')
    expect(card).not.toHaveTextContent('Arquivado em')
  })

  it('encerrado sem nenhuma data válida não mostra linha de data nem quebra', () => {
    const { card } = renderCard(
      objetivo({ status: 'ARQUIVADO', concluidoEm: 'ontem', arquivadoEm: null }),
    )

    expect(card).not.toHaveTextContent('Concluído em')
    expect(card).not.toHaveTextContent('Arquivado em')
    expect(within(card).getByRole('link', { name: 'Perder 5 kg' })).toBeInTheDocument()
  })

  it('encerrado não mostra o prazo nem o destaque de vencido', () => {
    const { card } = renderCard(
      objetivo({
        status: 'ARQUIVADO',
        prazo: '2026-10-01',
        arquivadoEm: '2026-11-15T15:00:00Z',
      }),
    )

    expect(card).not.toHaveTextContent('Prazo vencido')
    expect(card).not.toHaveAttribute('data-vencido')
  })

  it('arquivamento às 02:30Z cai no dia anterior em BRT', () => {
    const { card } = renderCard(
      objetivo({ status: 'ARQUIVADO', arquivadoEm: '2026-11-13T02:30:00Z' }),
    )

    expect(card).toHaveTextContent('Arquivado em 12 nov')
  })

  it('progresso nulo mostra o convite a vincular, sem barra nem percentual', () => {
    const { card } = renderCard(objetivo())

    expect(card).toHaveTextContent('Vincule tarefas, hábitos ou projetos para medir o progresso.')
    expect(within(card).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(card).not.toHaveTextContent('%')
  })

  it('com progresso mostra a barra com o percentual e o tempo em texto', () => {
    const { card } = renderCard(
      objetivo({ progresso: { percentual: 40, minutosConcluidos: 120, minutosTotais: 300 } }),
    )

    const barra = within(card).getByRole('progressbar', { name: 'Progresso de Perder 5 kg' })
    expect(barra).toHaveAttribute('aria-valuenow', '40')
    expect(barra).toHaveAttribute('aria-valuetext', '40%, 2h de 5h concluídas')
    expect(card).not.toHaveTextContent('Vincule tarefas')
  })

  it('concluído mantém a barra com o progresso congelado', () => {
    const { card } = renderCard(
      objetivo({
        status: 'CONCLUIDO',
        concluidoEm: '2026-11-12T15:00:00Z',
        progresso: { percentual: 60, minutosConcluidos: 90, minutosTotais: 150 },
      }),
    )

    expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '60')
  })

  it('arquivado mostra a barra dentro do card atenuado', () => {
    const { card } = renderCard(
      objetivo({
        status: 'ARQUIVADO',
        arquivadoEm: '2026-11-12T15:00:00Z',
        progresso: { percentual: 25, minutosConcluidos: 30, minutosTotais: 120 },
      }),
    )

    expect(card).toHaveClass('objetivo-card--arquivado')
    expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25')
  })

  it('percentual fora de 0–100 vindo da API não estoura a barra', () => {
    const { card } = renderCard(
      objetivo({ progresso: { percentual: 130, minutosConcluidos: 45, minutosTotais: 30 } }),
    )

    expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
  })

  it('progresso com total zero conta como nulo e mostra o convite', () => {
    const { card } = renderCard(
      objetivo({ progresso: { percentual: 0, minutosConcluidos: 0, minutosTotais: 0 } }),
    )

    expect(within(card).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(card).toHaveTextContent('Vincule tarefas, hábitos ou projetos')
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

  it('título é link para o detalhe do objetivo', () => {
    const { card } = renderCard(objetivo())

    expect(within(card).getByRole('link', { name: 'Perder 5 kg' })).toHaveAttribute(
      'href',
      '/objetivos/obj-1',
    )
  })

  describe('navegação carrega a visão da lista em state', () => {
    function Destino() {
      const location = useLocation()
      return (
        <p data-testid="destino">
          {location.pathname}|{JSON.stringify(location.state)}
        </p>
      )
    }

    function renderComRotas(inicial: string) {
      render(
        <MemoryRouter initialEntries={[inicial]}>
          <Routes>
            <Route
              path="/objetivos"
              element={
                <ul>
                  <ObjetivoCard
                    objetivo={objetivo()}
                    hojeIso={HOJE}
                    acaoEmAndamento={null}
                    bloqueado={false}
                    onAcao={vi.fn()}
                  />
                </ul>
              }
            />
            <Route path="/objetivos/:id/*" element={<Destino />} />
          </Routes>
        </MemoryRouter>,
      )
    }

    it('título abre o detalhe com a query da lista no state', async () => {
      const user = userEvent.setup()
      renderComRotas('/objetivos?status=ARQUIVADO&page=2')

      await user.click(screen.getByRole('link', { name: 'Perder 5 kg' }))

      expect(screen.getByTestId('destino')).toHaveTextContent(
        '/objetivos/obj-1|{"listaSearch":"?status=ARQUIVADO&page=2"}',
      )
    })

    it('Editar abre /editar com a query da lista no state', async () => {
      const user = userEvent.setup()
      renderComRotas('/objetivos?status=CONCLUIDO')

      await user.click(screen.getByRole('link', { name: /editar/i }))

      expect(screen.getByTestId('destino')).toHaveTextContent(
        '/objetivos/obj-1/editar|{"listaSearch":"?status=CONCLUIDO"}',
      )
    })

    it('lista sem query leva listaSearch vazio, sem quebrar', async () => {
      const user = userEvent.setup()
      renderComRotas('/objetivos')

      await user.click(screen.getByRole('link', { name: 'Perder 5 kg' }))

      expect(screen.getByTestId('destino')).toHaveTextContent('/objetivos/obj-1|{"listaSearch":""}')
    })
  })
})
