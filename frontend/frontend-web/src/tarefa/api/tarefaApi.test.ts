import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  atualizarTarefa,
  buscarTarefa,
  concluirTarefaDireto,
  criarEsperaTarefa,
  criarTarefa,
  excluirEsperaTarefa,
  excluirTarefa,
  listarEsperasTarefa,
  iniciarTarefaSessao,
  listarTarefas,
  restaurarTarefa,
  reordenarTarefas,
  type TarefaResponse,
} from './tarefaApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const tarefa: TarefaResponse = {
  id: 't-1',
  titulo: 'Estudar',
  descricao: 'Spring',
  prioridade: 'MEDIA',
  prazo: '2030-01-01T12:00:00Z',
  status: 'PENDENTE',
  duracaoMinutos: 120,
  restanteMinutos: 120,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

describe('tarefaApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista tarefas com filtros, página e ordenação via GET /tarefas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [tarefa],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const page = await listarTarefas({
      status: ['PENDENTE', 'ATRASADA'],
      prioridade: ['ALTA'],
      prazoDe: '2030-01-01T00:00:00Z',
      page: 1,
      size: 10,
      sort: 'duracao',
      direction: 'desc',
    })

    expect(apiClient.apiFetch).toHaveBeenCalledTimes(1)
    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/tarefas?')
    expect(url).toContain('status=PENDENTE')
    expect(url).toContain('status=ATRASADA')
    expect(url).toContain('prioridade=ALTA')
    expect(url).toContain('prazoDe=2030-01-01T00%3A00%3A00Z')
    expect(url).toContain('page=1')
    expect(url).toContain('size=10')
    expect(url).toContain('sort=duracao%2Cdesc')
    expect(page.content).toHaveLength(1)
    expect(page.totalElements).toBe(1)
  })

  it('cria tarefa com POST /tarefas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tarefa), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = {
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'MEDIA' as const,
      prazo: '2030-01-01T12:00:00Z',
      duracaoMinutos: 120,
    }
    const criada = await criarTarefa(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criada.id).toBe('t-1')
  })

  it('cria tarefa só com título (body mínimo)', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...tarefa, descricao: '', prazo: null, duracaoMinutos: 30 }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await criarTarefa({ titulo: 'Rápida' })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titulo: 'Rápida' }),
    })
  })

  it('cria subtarefa com parentId no POST /tarefas', async () => {
    const filha: TarefaResponse = {
      ...tarefa,
      id: 't-filho',
      titulo: 'Capítulo 1',
      parentId: 't-1',
      parentTitulo: 'Estudar',
      temFilhos: false,
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(filha), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = { titulo: 'Capítulo 1', duracaoMinutos: 60, parentId: 't-1' }
    const criada = await criarTarefa(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criada.parentId).toBe('t-1')
    expect(criada.parentTitulo).toBe('Estudar')
    expect(criada.temFilhos).toBe(false)
  })

  it('busca tarefa por id', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tarefa), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const encontrada = await buscarTarefa('t-1')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1')
    expect(encontrada.titulo).toBe('Estudar')
  })

  it('atualiza tarefa com PATCH', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...tarefa, titulo: 'Novo' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const atualizada = await atualizarTarefa('t-1', { titulo: 'Novo' })
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titulo: 'Novo' }),
    })
    expect(atualizada.titulo).toBe('Novo')
  })

  it('propaga ApiError com detalhe da API', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Prazo inválido',
          detail: 'O prazo não pode estar no passado.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await criarTarefa({
        titulo: 'X',
        descricao: 'Y',
        prioridade: 'BAIXA',
        prazo: '2000-01-01T00:00:00Z',
        duracaoMinutos: 60,
      })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 400,
        message: 'O prazo não pode estar no passado.',
      })
    }
  })

  it('lista com filtros de ausência sem prazo, projeto e tag', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listarTarefas({ semPrazo: true, semProjeto: true, semTag: true })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('semPrazo=true')
    expect(url).toContain('semProjeto=true')
    expect(url).toContain('semTag=true')
  })

  it('lista com filtro projetoId', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listarTarefas({ projetoId: 'proj-1', page: 0, size: 20 })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('projetoId=proj-1')
  })

  it('lista filhos com query parentId', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 100,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listarTarefas({ parentId: 't-pai', page: 0, size: 100 })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('parentId=t-pai')
    expect(url).toContain('size=100')
  })

  it('lista só raízes com query somenteRaizes', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [{ ...tarefa, parentId: null, parentTitulo: null, temFilhos: true }],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const page = await listarTarefas({ somenteRaizes: true })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('somenteRaizes=true')
    expect(url).not.toContain('parentId=')
    expect(page.content[0].temFilhos).toBe(true)
    expect(page.content[0].parentId).toBeNull()
  })

  it('lista com filtros de deletadas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listarTarefas({ incluirDeletadas: true, somenteDeletadas: false })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('incluirDeletadas=true')
    expect(url).toContain('somenteDeletadas=false')
  })

  it('exclui tarefa com DELETE /tarefas/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await excluirTarefa('t-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1', { method: 'DELETE' })
  })

  it('restaura tarefa com POST /tarefas/{id}/restore', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tarefa), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const restaurada = await restaurarTarefa('t-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/restore', { method: 'POST' })
    expect(restaurada.deletada).toBe(false)
  })

  it('inicia sessão com POST /tarefas/{id}/iniciar', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'e-1',
          blocoPlanoId: 'b-1',
          status: 'EM_ANDAMENTO',
          iniciadoEm: '2026-10-20T12:00:00Z',
          concluidoEm: null,
          abortadoEm: null,
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const execucao = await iniciarTarefaSessao('t-1', {
      duracaoMinutos: 30,
      data: '2026-10-20',
    })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/iniciar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ duracaoMinutos: 30, data: '2026-10-20' }),
    })
    expect(execucao.status).toBe('EM_ANDAMENTO')
  })

  it('conclui direto com POST /tarefas/{id}/concluir-direto', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'e-2',
          blocoPlanoId: 'b-2',
          status: 'CONCLUIDO',
          iniciadoEm: '2026-10-20T12:00:00Z',
          concluidoEm: '2026-10-20T12:00:00Z',
          abortadoEm: null,
          xp: { ganhoNesteRequest: 45, eventos: [], progresso: null },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const execucao = await concluirTarefaDireto('t-1', { duracaoMinutos: 30 })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/concluir-direto', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ duracaoMinutos: 30 }),
    })
    expect(execucao.status).toBe('CONCLUIDO')
    expect(execucao.xp?.ganhoNesteRequest).toBe(45)
  })

  it('propaga erro de status ATRASADA com prazo futuro no PATCH', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Status inválido',
          detail: 'Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await atualizarTarefa('t-1', { status: 'ATRASADA' })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 400,
        message: 'Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.',
      })
    }
  })

  it('lista esperas da tarefa via GET /tarefas/{id}/esperas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            id: 'e-1',
            alvoTipo: 'TAREFA',
            alvoId: 't-2',
            alvoTitulo: 'Revisar API',
            resolvida: false,
          },
        ]),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const esperas = await listarEsperasTarefa('t-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/esperas')
    expect(esperas).toHaveLength(1)
    expect(esperas[0].alvoTitulo).toBe('Revisar API')
    expect(esperas[0].resolvida).toBe(false)
  })

  it('cria espera da tarefa via POST /tarefas/{id}/esperas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'e-1',
          alvoTipo: 'PROJETO',
          alvoId: 'p-1',
          alvoTitulo: 'TCC',
          resolvida: false,
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const espera = await criarEsperaTarefa('t-1', { alvoTipo: 'PROJETO', alvoId: 'p-1' })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/esperas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ alvoTipo: 'PROJETO', alvoId: 'p-1' }),
    })
    expect(espera.id).toBe('e-1')
  })

  it('remove espera da tarefa via DELETE e propaga erro de ciclo', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await excluirEsperaTarefa('t-1', 'e-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/esperas/e-1', {
      method: 'DELETE',
    })

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Espera inválida', detail: 'Isso criaria um ciclo.' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    try {
      await criarEsperaTarefa('t-1', { alvoTipo: 'TAREFA', alvoId: 't-2' })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({ status: 400, message: 'Isso criaria um ciclo.' })
    }
  })

  it('reordena tarefas via PUT /tarefas/ordem', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await reordenarTarefas(['t-2', 't-1'])

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/ordem', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: ['t-2', 't-1'] }),
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
      }),
    )
    await expect(listarTarefas()).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404 ao buscar tarefa inexistente', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Tarefa inexistente.' }), {
        status: 404,
      }),
    )
    await expect(buscarTarefa('t-sumiu')).rejects.toMatchObject({
      status: 404,
      message: 'Tarefa inexistente.',
    })
  })

  it('propaga 409 de conflito de versão', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'Tarefa já finalizada.' }), {
        status: 409,
      }),
    )
    await expect(concluirTarefaDireto('t-1')).rejects.toMatchObject({
      status: 409,
      message: 'Tarefa já finalizada.',
    })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(listarTarefas()).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML da SPA em erro e JSON inválido no 2xx', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 404,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(buscarTarefa('t-1')).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(listarTarefas()).rejects.toThrow()
  })
})
