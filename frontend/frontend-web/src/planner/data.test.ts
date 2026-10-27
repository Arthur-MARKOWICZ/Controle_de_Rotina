import { describe, expect, it } from 'vitest'

import {
  dataHojeIso,
  filtrarElegiveisForaDoPlano,
  formatarCapacidadeMinutos,
  minutosDisponiveis,
  minutosUsados,
} from './data'

describe('planner/data', () => {
  it('formata data de hoje em America/Sao_Paulo como YYYY-MM-DD', () => {
    // 2026-10-20 02:30 UTC = ainda 19/10 em BRT (UTC-3)
    const aindaDia19 = new Date('2026-10-20T02:30:00Z')
    expect(dataHojeIso(aindaDia19)).toBe('2026-10-19')

    // 2026-10-20 03:30 UTC = já 20/10 em BRT
    const jaDia20 = new Date('2026-10-20T03:30:00Z')
    expect(dataHojeIso(jaDia20)).toBe('2026-10-20')
  })

  it('soma minutos usados dos blocos', () => {
    expect(minutosUsados([{ duracaoMinutos: 30 }, { duracaoMinutos: 45 }])).toBe(75)
    expect(minutosUsados([])).toBe(0)
  })

  it('converte capacidade resolvida em minutos (VAZIO/null → 0)', () => {
    expect(minutosDisponiveis(2, 'SEMANAL')).toBe(120)
    expect(minutosDisponiveis(null, 'VAZIO')).toBe(0)
    expect(minutosDisponiveis(undefined, 'OVERRIDE')).toBe(0)
  })

  it('formata minutos como horas e minutos legíveis', () => {
    expect(formatarCapacidadeMinutos(0)).toBe('0min')
    expect(formatarCapacidadeMinutos(45)).toBe('45min')
    expect(formatarCapacidadeMinutos(60)).toBe('1h')
    expect(formatarCapacidadeMinutos(90)).toBe('1h 30min')
  })

  it('filtra elegíveis fora do plano (exclui finalizada, deletada e já alocada)', () => {
    const tarefas = [
      { id: 't-1', status: 'PENDENTE' as const, deletada: false },
      { id: 't-2', status: 'ATRASADA' as const, deletada: false },
      { id: 't-3', status: 'FINALIZADA' as const, deletada: false },
      { id: 't-4', status: 'PENDENTE' as const, deletada: true },
      { id: 't-5', status: 'EM_PROGRESSO' as const, deletada: false },
    ]
    const fora = filtrarElegiveisForaDoPlano(tarefas, ['t-1'])
    expect(fora.map((t) => t.id)).toEqual(['t-2', 't-5'])
  })
})
