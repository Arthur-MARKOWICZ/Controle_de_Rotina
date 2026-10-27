import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as apiClient from '../../auth/session/apiClient'
import {
  buildHistoricoDiaUrl,
  buildHistoricoSemanaUrl,
  consultarHistoricoDia,
  consultarHistoricoSemana,
} from './historicoApi'

vi.mock('../../auth/session/apiClient', () => ({
  apiFetch: vi.fn(),
  readApiJson: vi.fn(),
}))

describe('historicoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
    vi.mocked(apiClient.readApiJson).mockReset()
  })

  it('monta URLs com query de período', () => {
    expect(buildHistoricoDiaUrl('2026-10-27')).toBe('/historico/dia?data=2026-10-27')
    expect(buildHistoricoSemanaUrl('2026-10-26')).toBe(
      '/historico/semana?semanaInicio=2026-10-26',
    )
  })

  it('consulta histórico do dia', async () => {
    const resumo = {
      periodo: { escopo: 'DIA', inicio: '2026-10-27', fim: '2026-10-27' },
      tarefasFinalizadas: 1,
      blocosConcluidos: 2,
      minutosTrabalhados: 90,
      xpGanho: 100,
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ ok: true } as Response)
    vi.mocked(apiClient.readApiJson).mockResolvedValue(resumo)

    await expect(consultarHistoricoDia('2026-10-27')).resolves.toEqual(resumo)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/historico/dia?data=2026-10-27')
  })

  it('consulta histórico da semana', async () => {
    const resumo = {
      periodo: { escopo: 'SEMANA', inicio: '2026-10-26', fim: '2026-11-01' },
      tarefasFinalizadas: 3,
      blocosConcluidos: 5,
      minutosTrabalhados: 200,
      xpGanho: 400,
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ ok: true } as Response)
    vi.mocked(apiClient.readApiJson).mockResolvedValue(resumo)

    await expect(consultarHistoricoSemana('2026-10-26')).resolves.toEqual(resumo)
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/historico/semana?semanaInicio=2026-10-26',
    )
  })
})
