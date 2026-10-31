import { describe, expect, it } from 'vitest'

import type { ExecucaoResponse, ItemExecucaoResponse } from '../execucao/api/execucaoApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import {
  aplicarAbortar,
  aplicarConcluir,
  aplicarIniciar,
  aplicarRemoverBloco,
  mesclarExecucao,
  restaurarBloco,
  restaurarItem,
} from './otimista'

const agora = new Date('2026-10-20T14:00:00.000Z')

function item(blocoId: string, execucao: Partial<ExecucaoResponse> = {}): ItemExecucaoResponse {
  return {
    bloco: { id: blocoId, duracaoMinutos: 60, ordem: 1 },
    tarefaResumo: {
      id: `t-${blocoId}`,
      titulo: `Tarefa ${blocoId}`,
      prioridade: 'ALTA',
      status: 'PENDENTE',
      duracaoMinutos: 60,
    },
    execucao: {
      id: null,
      blocoPlanoId: null,
      status: 'NAO_INICIADO',
      iniciadoEm: null,
      concluidoEm: null,
      abortadoEm: null,
      ...execucao,
    },
  }
}

const plano: PlanoResponse = {
  id: 'p-1',
  data: '2026-10-20',
  versao: 1,
  status: 'RASCUNHO',
  temConflito: false,
  mensagem: null,
  propostaId: null,
  propostaPendente: false,
  geradoEm: '2026-10-20T09:00:00Z',
  createdAt: '2026-10-20T09:00:00Z',
  updatedAt: '2026-10-20T09:00:00Z',
  blocos: [
    {
      id: 'b-1',
      duracaoMinutos: 60,
      ordem: 1,
      tarefa: {
        id: 't-1',
        titulo: 'A',
        prioridade: 'ALTA',
        status: 'PENDENTE',
        duracaoMinutos: 60,
        prazo: null,
      },
    },
    {
      id: 'b-2',
      duracaoMinutos: 30,
      ordem: 2,
      tarefa: {
        id: 't-2',
        titulo: 'B',
        prioridade: 'BAIXA',
        status: 'PENDENTE',
        duracaoMinutos: 30,
        prazo: null,
      },
    },
  ],
}

describe('otimista (T1)', () => {
  it('iniciar marca EM_ANDAMENTO com iniciadoEm = agora só no bloco alvo', () => {
    const snapshot = [item('b-1'), item('b-2')]
    const copia = structuredClone(snapshot)

    const resultado = aplicarIniciar(snapshot, 'b-1', agora)

    expect(resultado[0].execucao.status).toBe('EM_ANDAMENTO')
    expect(resultado[0].execucao.iniciadoEm).toBe(agora.toISOString())
    expect(resultado[1]).toBe(snapshot[1])
    expect(snapshot).toEqual(copia)
  })

  it('concluir e abortar mudam status sem tocar no snapshot', () => {
    const snapshot = [item('b-1', { id: 'e-1', status: 'EM_ANDAMENTO' })]
    const copia = structuredClone(snapshot)

    expect(aplicarConcluir(snapshot, 'b-1', agora)[0].execucao.status).toBe('CONCLUIDO')
    expect(aplicarAbortar(snapshot, 'b-1', agora)[0].execucao.status).toBe('ABORTADO')
    expect(snapshot).toEqual(copia)
  })

  it('rollback de execução devolve exatamente o snapshot do item', () => {
    const snapshot = [item('b-1'), item('b-2')]

    const otimista = aplicarIniciar(snapshot, 'b-1', agora)
    const revertido = restaurarItem(otimista, snapshot[0])

    expect(revertido).toEqual(snapshot)
    expect(revertido[0]).toBe(snapshot[0])
  })

  it('remover tira o bloco e o rollback devolve o plano igual ao snapshot', () => {
    const copia = structuredClone(plano)

    const resultado = aplicarRemoverBloco(plano, 'b-1')
    expect(resultado.blocos.map((bloco) => bloco.id)).toEqual(['b-2'])
    expect(plano).toEqual(copia)

    expect(restaurarBloco(resultado, plano.blocos[0])).toEqual(plano)
  })
})

describe('mesclarExecucao (T2)', () => {
  it('substitui só a execução do bloco certo e descarta o xp', () => {
    const itens = [item('b-1', { status: 'EM_ANDAMENTO' }), item('b-2')]
    const resposta: ExecucaoResponse = {
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'CONCLUIDO',
      iniciadoEm: '2026-10-20T13:00:00Z',
      concluidoEm: '2026-10-20T14:00:00Z',
      abortadoEm: null,
      xp: null,
    }

    const resultado = mesclarExecucao(itens, 'b-1', resposta)

    expect(resultado[0].execucao).toEqual({
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'CONCLUIDO',
      iniciadoEm: '2026-10-20T13:00:00Z',
      concluidoEm: '2026-10-20T14:00:00Z',
      abortadoEm: null,
    })
    expect(resultado[0].bloco).toBe(itens[0].bloco)
    expect(resultado[1]).toBe(itens[1])
  })
})
