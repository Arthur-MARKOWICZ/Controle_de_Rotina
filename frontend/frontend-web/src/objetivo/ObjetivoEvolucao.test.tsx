import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { ObjetivoEvolucao as EvolucaoApi } from './api/objetivoApi'
import { montarSerie, type SerieEvolucao } from './evolucaoObjetivo'
import { ObjetivoEvolucao } from './ObjetivoEvolucao'

const HOJE = '2026-11-24'

function serie(
  pontos: [string, number, number][],
  antesDoHistorico = 0,
  objetivo: Parameters<typeof montarSerie>[1] = { status: 'ATIVO', concluidoEm: null, arquivadoEm: null },
): SerieEvolucao {
  const resposta: EvolucaoApi = {
    antesDoHistorico,
    pontos: pontos.map(([data, minutosNoDia, minutosAcumulados]) => ({ data, minutosNoDia, minutosAcumulados })),
  }
  const montada = montarSerie(resposta, objetivo, HOJE)
  if (!montada) {
    throw new Error('série vazia no teste')
  }
  return montada
}

function renderEvolucao(props: Partial<React.ComponentProps<typeof ObjetivoEvolucao>> = {}) {
  const onTentarDeNovo = vi.fn()
  const resultado = render(
    <ObjetivoEvolucao
      serie={null}
      carregando={false}
      erro={null}
      onTentarDeNovo={onTentarDeNovo}
      {...props}
    />,
  )
  return { ...resultado, onTentarDeNovo }
}

describe('ObjetivoEvolucao', () => {
  it('mostra o título, o total acumulado em texto e o gráfico', () => {
    const { container } = renderEvolucao({
      serie: serie([['2026-11-03', 30, 30], ['2026-11-05', 45, 75]]),
    })

    expect(screen.getByRole('heading', { name: 'Evolução' })).toBeInTheDocument()
    expect(container.querySelector('.objetivo-evolucao__resumo')).toHaveTextContent('1h 15min acumuladas')
    expect(container.querySelector('svg.objetivo-evolucao__svg')).toBeInTheDocument()
    expect(container.querySelector('path.objetivo-evolucao__linha')).toBeInTheDocument()
    expect(container.querySelectorAll('circle.objetivo-evolucao__ponto')).toHaveLength(2)
  })

  it('o gráfico fica escondido de leitor de tela e a tabela traz um dia por linha', () => {
    const { container } = renderEvolucao({
      serie: serie([['2026-11-03', 30, 30], ['2026-11-04', 15, 45], ['2026-11-06', 45, 90]]),
    })

    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    const tabela = screen.getByRole('table', { name: 'Minutos concluídos por dia' })
    const linhas = within(tabela).getAllByRole('row')
    expect(linhas).toHaveLength(4)
    expect(within(linhas[1]).getAllByRole('cell').map((c) => c.textContent)).toEqual([
      '03/11/2026',
      '30min',
      '30min',
    ])
    expect(within(linhas[3]).getAllByRole('cell').map((c) => c.textContent)).toEqual([
      '06/11/2026',
      '45min',
      '1h 30min',
    ])
  })

  it('mostra "antes do histórico" e o gráfico parte dele', () => {
    const { container } = renderEvolucao({
      serie: serie([['2026-11-05', 30, 150]], 120),
    })

    expect(container.querySelector('.objetivo-evolucao__resumo')).toHaveTextContent(
      '2h 30min acumuladas · 2h antes do histórico',
    )
    const caminho = container.querySelector('path.objetivo-evolucao__linha')?.getAttribute('d') ?? ''
    expect(caminho).toMatch(/^M 62 /)
  })

  it('só "antes do histórico" mostra o total sem gráfico nem tabela', () => {
    const { container } = renderEvolucao({ serie: serie([], 90) })

    expect(screen.getByText(/1h 30min antes do histórico/)).toBeInTheDocument()
    expect(container.querySelector('svg')).not.toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('objetivo encerrado mostra o marcador com texto, na legenda e no gráfico', () => {
    const { container } = renderEvolucao({
      serie: serie([['2026-11-03', 30, 30]], 0, {
        status: 'CONCLUIDO',
        concluidoEm: '2026-11-12T15:00:00Z',
        arquivadoEm: null,
      }),
    })

    expect(container.querySelector('line.objetivo-evolucao__marcador')).toBeInTheDocument()
    expect(screen.getAllByText('Concluído em 12 nov')).toHaveLength(2)
  })

  it('objetivo ativo não tem marcador de encerramento', () => {
    const { container } = renderEvolucao({ serie: serie([['2026-11-03', 30, 30]]) })

    expect(container.querySelector('line.objetivo-evolucao__marcador')).not.toBeInTheDocument()
    expect(screen.queryByText(/Concluído em|Arquivado em/)).not.toBeInTheDocument()
  })

  it('mostra o placeholder enquanto carrega, sem gráfico', () => {
    const { container } = renderEvolucao({ carregando: true })

    expect(screen.getByRole('heading', { name: 'Evolução' })).toBeInTheDocument()
    expect(container.querySelector('[aria-busy="true"]')).toBeInTheDocument()
    expect(container.querySelector('svg')).not.toBeInTheDocument()
  })

  it('recarregando com a série já na tela mantém o gráfico em vez do placeholder', () => {
    const { container } = renderEvolucao({
      carregando: true,
      serie: serie([['2026-11-03', 30, 30]]),
    })

    expect(container.querySelector('svg')).toBeInTheDocument()
    expect(container.querySelector('[aria-busy="true"]')).not.toBeInTheDocument()
  })

  it('mostra o erro com "Tentar de novo", que chama o callback', async () => {
    const user = userEvent.setup()
    const { onTentarDeNovo, container } = renderEvolucao({ erro: 'Não foi possível carregar a evolução.' })

    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível carregar a evolução.')
    await user.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    expect(onTentarDeNovo).toHaveBeenCalledTimes(1)
    expect(container.querySelector('svg')).not.toBeInTheDocument()
  })

  it('não renderiza nada quando não há série, nem carga, nem erro', () => {
    const { container } = renderEvolucao()

    expect(container).toBeEmptyDOMElement()
  })

  it('tem um só rótulo de data quando o eixo é de um único dia', () => {
    const { container } = renderEvolucao({ serie: serie([['2026-11-24', 30, 30]]) })

    const rotulosDeData = Array.from(container.querySelectorAll('text.objetivo-evolucao__rotulo'))
      .map((t) => t.textContent)
      .filter((t) => /^\d{2}\/\d{2}$/.test(t ?? ''))
    expect(rotulosDeData).toEqual(['24/11'])
  })

  it('mostra início e fim do eixo atravessando a virada de mês', () => {
    const { container } = renderEvolucao({
      serie: serie([['2026-11-30', 30, 30], ['2026-12-01', 15, 45]]),
    })

    const rotulosDeData = Array.from(container.querySelectorAll('text.objetivo-evolucao__rotulo'))
      .map((t) => t.textContent)
      .filter((t) => /^\d{2}\/\d{2}$/.test(t ?? ''))
    expect(rotulosDeData).toEqual(['30/11', '01/12'])
  })
})
