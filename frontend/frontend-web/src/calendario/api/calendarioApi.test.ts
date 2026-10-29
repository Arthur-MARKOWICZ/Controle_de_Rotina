import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as apiClient from '../../auth/session/apiClient'
import { buildCalendarioUrl, consultarCalendario } from './calendarioApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
    readApiJson: vi.fn(),
  }
})

describe('calendarioApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
    vi.mocked(apiClient.readApiJson).mockReset()
  })

  it('monta URL com dataDe e dataAte', () => {
    expect(buildCalendarioUrl('2026-10-26', '2026-11-01')).toBe(
      '/calendario?dataDe=2026-10-26&dataAte=2026-11-01',
    )
  })

  it('consulta calendário no intervalo', async () => {
    const payload = {
      periodo: { inicio: '2026-10-26', fim: '2026-11-01' },
      dias: [],
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ ok: true } as Response)
    vi.mocked(apiClient.readApiJson).mockResolvedValue(payload)

    await expect(consultarCalendario('2026-10-26', '2026-11-01')).resolves.toEqual(payload)
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/calendario?dataDe=2026-10-26&dataAte=2026-11-01',
    )
  })
})
