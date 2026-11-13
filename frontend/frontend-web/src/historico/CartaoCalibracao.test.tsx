import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as historicoApi from './api/historicoApi'
import type { CalibracaoResponse, RecorteTagCalibracao } from './api/historicoApi'
import { CartaoCalibracao } from './CartaoCalibracao'

vi.mock('./api/historicoApi', () => ({
  consultarCalibracao: vi.fn(),
}))

function tag(nome: string, razao: number | null, suficiente = true): RecorteTagCalibracao {
  return {
    tagId: `tag-${nome}`,
    nome,
    cor: '#3366FF',
    amostras: suficiente ? 6 : 2,
    suficiente,
    razao: suficiente ? razao : null,
    desvioMedioMinutos: suficiente ? 10 : null,
  }
}

function calibracao(parcial: Partial<CalibracaoResponse> = {}): CalibracaoResponse {
  return {
    janela: { inicio: '2026-08-16', fim: '2026-11-13' },
    minimoAmostras: 5,
    geral: { amostras: 12, suficiente: true, razao: 1.4, desvioMedioMinutos: 18 },
    porTag: [],
    porProjeto: [],
    ...parcial,
  }
}

describe('CartaoCalibracao', () => {
  beforeEach(() => {
    vi.mocked(historicoApi.consultarCalibracao).mockReset()
  })

  it('mostra a razão geral, o N e os recortes suficientes por tag e projeto', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({
        porTag: [tag('faculdade', 1.4), tag('leitura', null, false)],
        porProjeto: [
          {
            projetoId: 'p1',
            nome: 'TCC',
            amostras: 7,
            suficiente: true,
            razao: 1.8,
            desvioMedioMinutos: 25,
          },
        ],
      }),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByText('Você costuma levar 1,4× o estimado')).toBeInTheDocument()
    expect(screen.getByText('12 blocos')).toBeInTheDocument()
    expect(screen.getByText('Últimos 90 dias')).toBeInTheDocument()
    const porTag = screen.getByRole('list', { name: 'Ritmo por tag' })
    expect(within(porTag).getByRole('listitem')).toHaveTextContent('#faculdade1,4×')
    expect(within(porTag).queryByText('#leitura')).not.toBeInTheDocument()
    const porProjeto = screen.getByRole('list', { name: 'Ritmo por projeto' })
    expect(within(porProjeto).getByRole('listitem')).toHaveTextContent('TCC1,8×')
  })

  it('mostra skeleton enquanto carrega', () => {
    vi.mocked(historicoApi.consultarCalibracao).mockReturnValue(new Promise(() => {}))

    render(<CartaoCalibracao />)

    expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
    expect(screen.queryByText(/×/)).not.toBeInTheDocument()
  })

  it('diz que as estimativas estão em dia quando a razão é próxima de 1', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({ geral: { amostras: 8, suficiente: true, razao: 1.03, desvioMedioMinutos: 3 } }),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByText('Suas estimativas estão em dia')).toBeInTheDocument()
  })

  it('mostra sem dados e quantos blocos faltam, sem nenhum número ×', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({
        geral: { amostras: 2, suficiente: false, razao: null, desvioMedioMinutos: null },
        porTag: [tag('leitura', null, false)],
      }),
    )

    render(<CartaoCalibracao />)

    const vazio = await screen.findByText(/Ainda sem dados suficientes/)
    expect(vazio).toHaveTextContent('faltam 3 blocos concluídos com timer')
    expect(screen.queryByText(/×/)).not.toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('usa singular quando falta 1 bloco', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({ geral: { amostras: 4, suficiente: false, razao: null, desvioMedioMinutos: null } }),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByText(/falta 1 bloco concluído com timer/)).toBeInTheDocument()
  })

  it('não mostra lista quando nenhum recorte é suficiente', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({ porTag: [tag('leitura', null, false)], porProjeto: [] }),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByText('Você costuma levar 1,4× o estimado')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(screen.queryByText('Por tag')).not.toBeInTheDocument()
  })

  it('limita a 3 recortes por tag na ordem da API', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({ porTag: [tag('a', 2), tag('b', 0.5), tag('c', 1.6), tag('d', 1.3)] }),
    )

    render(<CartaoCalibracao />)

    const lista = await screen.findByRole('list', { name: 'Ritmo por tag' })
    const itens = within(lista).getAllByRole('listitem')
    expect(itens).toHaveLength(3)
    expect(itens.map((li) => li.textContent)).toEqual(['#a2,0×', '#b0,5×', '#c1,6×'])
  })

  it('mostra a mensagem da API no erro 500', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockRejectedValue(
      new ApiError(500, 'Erro interno ao calcular.'),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Erro interno ao calcular.')
    expect(screen.queryByText(/×/)).not.toBeInTheDocument()
  })

  it('mostra o aviso de HTML do proxy em vez de quebrar', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockRejectedValue(
      new ApiError(200, 'Resposta HTML da SPA em vez de JSON (/historico/calibracao).'),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Resposta HTML da SPA em vez de JSON')
  })

  it('usa a mensagem padrão quando o erro não tem texto', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockRejectedValue('falhou')

    render(<CartaoCalibracao />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar sua calibração.',
    )
  })

  it('tolera resposta sem listas de recorte', async () => {
    vi.mocked(historicoApi.consultarCalibracao).mockResolvedValue(
      calibracao({
        porTag: undefined as unknown as CalibracaoResponse['porTag'],
        porProjeto: undefined as unknown as CalibracaoResponse['porProjeto'],
      }),
    )

    render(<CartaoCalibracao />)

    expect(await screen.findByText('Você costuma levar 1,4× o estimado')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })
})
