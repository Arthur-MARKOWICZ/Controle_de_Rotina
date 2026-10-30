import { describe, expect, it } from 'vitest'

import { tituloPorRota } from './documentTitle'

describe('tituloPorRota (T4)', () => {
  it('define títulos esperados para rotas protegidas e auth', () => {
    expect(tituloPorRota('/')).toBe('Hoje · Controle de Rotina')
    expect(tituloPorRota('/tarefas')).toBe('Tarefas · Controle de Rotina')
    expect(tituloPorRota('/tarefas/nova')).toBe('Nova tarefa · Controle de Rotina')
    expect(tituloPorRota('/tarefas/abc-123')).toBe('Editar tarefa · Controle de Rotina')
    expect(tituloPorRota('/projetos')).toBe('Projetos · Controle de Rotina')
    expect(tituloPorRota('/projetos/nova')).toBe('Novo projeto · Controle de Rotina')
    expect(tituloPorRota('/projetos/abc-123')).toBe('Projeto · Controle de Rotina')
    expect(tituloPorRota('/calendario')).toBe('Calendário · Controle de Rotina')
    expect(tituloPorRota('/capacidade')).toBe('Capacidade · Controle de Rotina')
    expect(tituloPorRota('/historico')).toBe('Histórico · Controle de Rotina')
    expect(tituloPorRota('/perfil')).toBe('Perfil · Controle de Rotina')
    expect(tituloPorRota('/login')).toBe('Login · Controle de Rotina')
    expect(tituloPorRota('/cadastro')).toBe('Cadastro · Controle de Rotina')
  })
})
