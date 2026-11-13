import { describe, expect, it } from 'vitest'

import { rotuloPorRota, tituloPorRota } from './documentTitle'

describe('tituloPorRota (T4)', () => {
  it('define títulos esperados para rotas protegidas e auth', () => {
    expect(tituloPorRota('/plano')).toBe('Plano · Controle de Rotina')
    expect(tituloPorRota('/tarefas')).toBe('Tarefas · Controle de Rotina')
    expect(tituloPorRota('/tarefas/nova')).toBe('Nova tarefa · Controle de Rotina')
    expect(tituloPorRota('/tarefas/abc-123')).toBe('Editar tarefa · Controle de Rotina')
    expect(tituloPorRota('/projetos')).toBe('Projetos · Controle de Rotina')
    expect(tituloPorRota('/projetos/nova')).toBe('Novo projeto · Controle de Rotina')
    expect(tituloPorRota('/projetos/abc-123')).toBe('Projeto · Controle de Rotina')
    expect(tituloPorRota('/habitos')).toBe('Hábitos · Controle de Rotina')
    expect(tituloPorRota('/habitos/nova')).toBe('Novo hábito · Controle de Rotina')
    expect(tituloPorRota('/habitos/abc-123')).toBe('Editar hábito · Controle de Rotina')
    expect(tituloPorRota('/calendario')).toBe('Calendário · Controle de Rotina')
    expect(tituloPorRota('/capacidade')).toBe('Capacidade · Controle de Rotina')
    expect(tituloPorRota('/historico')).toBe('Histórico · Controle de Rotina')
    expect(tituloPorRota('/tags')).toBe('Tags · Controle de Rotina')
    expect(tituloPorRota('/decidir')).toBe('Decidir · Controle de Rotina')
    expect(tituloPorRota('/decidir/nova')).toBe('Novo item · Controle de Rotina')
    expect(tituloPorRota('/decidir/abc-123')).toBe('Editar item · Controle de Rotina')
    expect(tituloPorRota('/perfil')).toBe('Perfil · Controle de Rotina')
    expect(tituloPorRota('/login')).toBe('Login · Controle de Rotina')
    expect(tituloPorRota('/cadastro')).toBe('Cadastro · Controle de Rotina')
  })

  it('rota desconhecida usa só o sufixo e não tem rótulo', () => {
    expect(tituloPorRota('/xyz/abc')).toBe('Controle de Rotina')
    expect(rotuloPorRota('/xyz/abc')).toBeNull()
    expect(rotuloPorRota('/capacidade')).toBe('Capacidade')
  })

  it('não trata subpath extra como detalhe conhecido', () => {
    expect(tituloPorRota('/tarefas/abc/extra')).toBe('Controle de Rotina')
    expect(rotuloPorRota('/tarefas/abc/extra')).toBeNull()
  })

  it('pathname vazio e raiz não têm rótulo', () => {
    expect(tituloPorRota('')).toBe('Controle de Rotina')
    expect(rotuloPorRota('/')).toBeNull()
  })

  it('rotuloPorRota devolve só o nome curto', () => {
    expect(rotuloPorRota('/login')).toBe('Login')
    expect(rotuloPorRota('/cadastro')).toBe('Cadastro')
  })

  it('não casa /tarefas-arquivo com a lista de tarefas', () => {
    expect(rotuloPorRota('/tarefas-arquivo')).toBeNull()
  })

  it('trata /decidir/nova como novo item e não como edição', () => {
    expect(tituloPorRota('/decidir/nova')).toBe('Novo item · Controle de Rotina')
    expect(tituloPorRota('/decidir/d-1')).toBe('Editar item · Controle de Rotina')
  })
})
