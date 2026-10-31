import type {
  ExecucaoResponse,
  ItemExecucaoResponse,
  StatusExecucao,
} from '../execucao/api/execucaoApi'
import type { BlocoPlanoResponse, PlanoResponse } from '../planner/api/planoApi'

/*
 * Funções puras de atualização otimista da Home. Nunca mutam a entrada: o array/objeto
 * recebido é o snapshot que o chamador restaura em caso de erro (rollback).
 */

function atualizarExecucao(
  itens: ItemExecucaoResponse[],
  blocoId: string,
  alterar: (execucao: ExecucaoResponse) => ExecucaoResponse,
): ItemExecucaoResponse[] {
  return itens.map((item) =>
    item.bloco.id === blocoId ? { ...item, execucao: alterar(item.execucao) } : item,
  )
}

function comStatus(
  itens: ItemExecucaoResponse[],
  blocoId: string,
  status: StatusExecucao,
  campos: Partial<ExecucaoResponse>,
): ItemExecucaoResponse[] {
  return atualizarExecucao(itens, blocoId, (execucao) => ({ ...execucao, ...campos, status }))
}

export function aplicarIniciar(
  itens: ItemExecucaoResponse[],
  blocoId: string,
  agora: Date,
): ItemExecucaoResponse[] {
  return comStatus(itens, blocoId, 'EM_ANDAMENTO', {
    iniciadoEm: agora.toISOString(),
    concluidoEm: null,
    abortadoEm: null,
  })
}

export function aplicarConcluir(
  itens: ItemExecucaoResponse[],
  blocoId: string,
  agora: Date,
): ItemExecucaoResponse[] {
  return comStatus(itens, blocoId, 'CONCLUIDO', { concluidoEm: agora.toISOString() })
}

export function aplicarAbortar(
  itens: ItemExecucaoResponse[],
  blocoId: string,
  agora: Date,
): ItemExecucaoResponse[] {
  return comStatus(itens, blocoId, 'ABORTADO', { abortadoEm: agora.toISOString() })
}

/** Substitui a execução do item `blocoId` pela resposta da API (sem o payload de XP). */
export function mesclarExecucao(
  itens: ItemExecucaoResponse[],
  blocoId: string,
  resposta: ExecucaoResponse,
): ItemExecucaoResponse[] {
  const { xp: _xp, ...execucao } = resposta
  return atualizarExecucao(itens, blocoId, () => execucao)
}

/** Rollback de um item: devolve o snapshot `anterior` sem desfazer mudanças em outros itens. */
export function restaurarItem(
  itens: ItemExecucaoResponse[],
  anterior: ItemExecucaoResponse,
): ItemExecucaoResponse[] {
  return itens.map((item) => (item.bloco.id === anterior.bloco.id ? anterior : item))
}

export function aplicarRemoverBloco(plano: PlanoResponse, blocoId: string): PlanoResponse {
  return { ...plano, blocos: plano.blocos.filter((bloco) => bloco.id !== blocoId) }
}

/** Rollback da remoção: reinsere o bloco na posição da sua `ordem`. */
export function restaurarBloco(plano: PlanoResponse, bloco: BlocoPlanoResponse): PlanoResponse {
  if (plano.blocos.some((atual) => atual.id === bloco.id)) {
    return plano
  }
  const blocos = [...plano.blocos, bloco].sort((a, b) => a.ordem - b.ordem)
  return { ...plano, blocos }
}

export function encontrarBloco(
  plano: PlanoResponse,
  blocoId: string,
): BlocoPlanoResponse | undefined {
  return plano.blocos.find((bloco) => bloco.id === blocoId)
}
