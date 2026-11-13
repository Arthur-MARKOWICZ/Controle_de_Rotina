import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as capacidadeOverrideApi from '../capacidade/api/capacidadeOverrideApi'
import * as capacidadeResolvidaApi from '../capacidade/api/capacidadeResolvidaApi'
import * as execucaoApi from '../execucao/api/execucaoApi'
import type { ItemExecucaoResponse } from '../execucao/api/execucaoApi'
import * as planoApi from '../planner/api/planoApi'
import type { PlanoResponse } from '../planner/api/planoApi'
import * as tarefaApi from '../tarefa/api/tarefaApi'
import { usePlanoDoDia } from './usePlanoDoDia'

const toastMocks = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

const confirmMocks = vi.hoisted(() => ({
  confirm: vi.fn().mockResolvedValue(true),
}))

vi.mock('../feedback/ToastProvider', () => ({
  useToast: () => toastMocks,
}))

vi.mock('../feedback/useConfirmDialog', () => ({
  useConfirmDialog: () => ({ confirm: confirmMocks.confirm, dialog: null }),
}))

vi.mock('../layout/QuickAddContext', () => ({
  useQuickAdd: () => ({
    abrir: vi.fn(),
    assinarCriada: () => () => undefined,
  }),
}))

vi.mock('../xp/XpProgressContext', () => ({
  useXpProgress: () => ({
    progresso: { xpTotal: 0, nivel: 1, xpNoNivel: 0, xpParaProximoNivel: 10 },
    aplicarProgresso: vi.fn(),
  }),
}))

vi.mock('../planner/api/planoApi', () => ({
  buscarPlano: vi.fn(),
  gerarPlano: vi.fn(),
  aprovarPlano: vi.fn(),
  aceitarProposta: vi.fn(),
  descartarProposta: vi.fn(),
  adicionarBloco: vi.fn(),
  adicionarBlocoHabito: vi.fn(),
  removerBloco: vi.fn(),
  listarDatasDaProposta: vi.fn().mockResolvedValue([]),
}))

vi.mock('../capacidade/api/capacidadeResolvidaApi', () => ({
  consultarCapacidadeResolvida: vi.fn(),
}))

vi.mock('../capacidade/api/capacidadeOverrideApi', () => ({
  listarCapacidadesOverride: vi.fn().mockResolvedValue([]),
  criarCapacidadeOverride: vi.fn(),
  atualizarCapacidadeOverride: vi.fn(),
}))

vi.mock('../tarefa/api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
}))

vi.mock('../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../execucao/api/execucaoApi')>()
  return {
    ...actual,
    listarExecucoes: vi.fn(),
    iniciarExecucao: vi.fn(),
    concluirExecucao: vi.fn(),
    abortarExecucao: vi.fn(),
  }
})

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 100,
  }),
}))

vi.mock('../projeto/api/reservaProjetoApi', () => ({
  listarReservas: vi.fn().mockResolvedValue([]),
}))

vi.mock('../planner/data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../planner/data')>()
  return {
    ...actual,
    dataHojeIso: () => '2026-10-20',
  }
})

const planoRascunho: PlanoResponse = {
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
        titulo: 'Estudar Spring',
        prioridade: 'ALTA',
        status: 'PENDENTE',
        duracaoMinutos: 60,
        prazo: '2026-10-22T15:00:00Z',
      },
    },
  ],
}

const planoAprovado: PlanoResponse = {
  ...planoRascunho,
  status: 'APROVADO',
  versao: 2,
}

const itemExecucao: ItemExecucaoResponse = {
  bloco: { id: 'b-1', duracaoMinutos: 60, ordem: 1 },
  tarefaResumo: {
    id: 't-1',
    titulo: 'Estudar Spring',
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
  },
}

const paginaVazia = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 100,
}

async function hookCarregado() {
  const utils = renderHook(() => usePlanoDoDia())
  await waitFor(() => expect(utils.result.current.carregando).toBe(false))
  return utils
}

describe('usePlanoDoDia', () => {
  beforeEach(() => {
    toastMocks.success.mockReset()
    toastMocks.error.mockReset()
    confirmMocks.confirm.mockReset().mockResolvedValue(true)
    vi.mocked(planoApi.buscarPlano).mockReset().mockResolvedValue(planoRascunho)
    vi.mocked(planoApi.gerarPlano).mockReset()
    vi.mocked(planoApi.aprovarPlano).mockReset()
    vi.mocked(planoApi.adicionarBloco).mockReset()
    vi.mocked(planoApi.removerBloco).mockReset()
    vi.mocked(planoApi.listarDatasDaProposta).mockReset().mockResolvedValue([])
    vi.mocked(capacidadeResolvidaApi.consultarCapacidadeResolvida)
      .mockReset()
      .mockResolvedValue([{ data: '2026-10-20', horas: 4, origem: 'SEMANAL' }])
    vi.mocked(capacidadeOverrideApi.listarCapacidadesOverride).mockReset().mockResolvedValue([])
    vi.mocked(tarefaApi.listarTarefas).mockReset().mockResolvedValue(paginaVazia)
    vi.mocked(execucaoApi.listarExecucoes)
      .mockReset()
      .mockResolvedValue({ planoAprovado: null, itens: [] })
    vi.mocked(execucaoApi.iniciarExecucao).mockReset()
  })

  it('carrega o plano rascunho do dia e a capacidade resolvida', async () => {
    const { result } = await hookCarregado()

    expect(result.current.plano?.id).toBe('p-1')
    expect(result.current.plano?.status).toBe('RASCUNHO')
    expect(result.current.capacidade?.horas).toBe(4)
    expect(result.current.erro).toBeNull()
    expect(result.current.podeGerar).toBe(true)
    expect(planoApi.buscarPlano).toHaveBeenCalledWith({ data: '2026-10-20' })
  })

  it('gera um novo plano e substitui o rascunho', async () => {
    const gerado: PlanoResponse = {
      ...planoRascunho,
      versao: 2,
      mensagem: 'Plano gerado',
      blocos: [
        {
          id: 'b-2',
          duracaoMinutos: 30,
          ordem: 1,
          tarefa: {
            id: 't-9',
            titulo: 'Revisar PRs',
            prioridade: 'MEDIA',
            status: 'PENDENTE',
            duracaoMinutos: 30,
            prazo: null,
          },
        },
      ],
    }
    vi.mocked(planoApi.gerarPlano).mockResolvedValue(gerado)

    const { result } = await hookCarregado()
    await act(async () => {
      await result.current.handleGerar()
    })

    expect(planoApi.gerarPlano).toHaveBeenCalledWith('2026-10-20')
    expect(result.current.plano?.versao).toBe(2)
    expect(result.current.plano?.blocos[0].id).toBe('b-2')
    expect(result.current.gerando).toBe(false)
  })

  it('aprova o rascunho e carrega as execuções do dia', async () => {
    vi.mocked(planoApi.aprovarPlano).mockResolvedValue(planoAprovado)
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 2 },
      itens: [itemExecucao],
    })

    const { result } = await hookCarregado()
    await act(async () => {
      await result.current.handleAprovar()
    })

    expect(planoApi.aprovarPlano).toHaveBeenCalledWith('p-1')
    expect(result.current.plano?.status).toBe('APROVADO')
    expect(result.current.itensExecucao).toHaveLength(1)
    expect(result.current.ehRascunho).toBe(false)
  })

  it('reverte o bloco removido quando a API falha (rollback otimista)', async () => {
    vi.mocked(planoApi.removerBloco).mockRejectedValue(new ApiError(500, 'Falha ao remover'))

    const { result } = await hookCarregado()
    expect(result.current.plano?.blocos).toHaveLength(1)

    await act(async () => {
      await result.current.handleRemover('b-1')
    })

    expect(result.current.plano?.blocos.map((bloco) => bloco.id)).toEqual(['b-1'])
    expect(result.current.pendentes.has('b-1')).toBe(false)
    expect(toastMocks.error).toHaveBeenCalled()
  })

  it('mantém o rascunho quando aprovar retorna 409 de versão', async () => {
    vi.mocked(planoApi.aprovarPlano).mockRejectedValue(
      new ApiError(409, 'Versão do plano desatualizada'),
    )

    const { result } = await hookCarregado()
    await act(async () => {
      await result.current.handleAprovar()
    })

    expect(result.current.plano?.status).toBe('RASCUNHO')
    expect(result.current.plano?.versao).toBe(1)
    expect(result.current.aprovando).toBe(false)
    expect(toastMocks.error).toHaveBeenCalledWith(
      'Versão do plano desatualizada',
      expect.objectContaining({ action: expect.anything() }),
    )
  })

  it('expõe erro de rede e deixa o plano vazio quando o carregamento inicial falha', async () => {
    vi.mocked(planoApi.buscarPlano).mockRejectedValue(new TypeError('Failed to fetch'))

    const { result } = await hookCarregado()

    expect(result.current.plano).toBeNull()
    expect(result.current.itensExecucao).toEqual([])
    expect(result.current.erro).toBe('Failed to fetch')
    expect(result.current.carregando).toBe(false)
  })

  it('preserva o plano anterior quando gerar falha', async () => {
    vi.mocked(planoApi.gerarPlano).mockRejectedValue(new ApiError(503, 'Planner indisponível'))

    const { result } = await hookCarregado()
    await act(async () => {
      await result.current.handleGerar()
    })

    expect(result.current.plano).toEqual(planoRascunho)
    expect(result.current.gerando).toBe(false)
    expect(toastMocks.error).toHaveBeenCalledWith(
      'Planner indisponível',
      expect.objectContaining({ action: expect.anything() }),
    )
  })

  it('aceita plano ausente (404) sem alterar capacidade já carregada em paralelo', async () => {
    vi.mocked(planoApi.buscarPlano).mockResolvedValue(null)

    const { result } = await hookCarregado()

    expect(result.current.plano).toBeNull()
    expect(result.current.capacidade?.horas).toBe(4)
    expect(result.current.erro).toBeNull()
    expect(result.current.podeGerar).toBe(true)
  })

  it('não chama a API ao aprovar quando o plano já não é rascunho', async () => {
    vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoAprovado)
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 2 },
      itens: [itemExecucao],
    })

    const { result } = await hookCarregado()
    await act(async () => {
      await result.current.handleAprovar()
    })

    expect(planoApi.aprovarPlano).not.toHaveBeenCalled()
    expect(result.current.plano?.status).toBe('APROVADO')
  })

  it('reverte a execução otimista quando iniciar falha', async () => {
    vi.mocked(planoApi.buscarPlano).mockResolvedValue(planoAprovado)
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 2 },
      itens: [itemExecucao],
    })
    vi.mocked(execucaoApi.iniciarExecucao).mockRejectedValue(new ApiError(409, 'Já em andamento'))

    const { result } = await hookCarregado()
    await act(async () => {
      await result.current.handleIniciar(itemExecucao)
    })

    expect(result.current.itensExecucao[0].execucao.status).toBe('NAO_INICIADO')
    expect(result.current.pendentes.has('b-1')).toBe(false)
    expect(toastMocks.error).toHaveBeenCalledWith(
      'Já em andamento',
      expect.objectContaining({ action: expect.anything() }),
    )
  })
})
