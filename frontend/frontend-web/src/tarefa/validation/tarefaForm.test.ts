import { describe, expect, it } from 'vitest'

import { validarTarefaForm } from './tarefaForm'

describe('validarTarefaForm', () => {
  const futuro = new Date(Date.now() + 60 * 60 * 1000).toISOString()

  it('aceita dados válidos no create', () => {
    expect(
      validarTarefaForm({
        titulo: 'Estudar',
        descricao: 'Revisar Spring',
        prioridade: 'MEDIA',
        prazo: futuro,
        duracaoHoras: 2,
      }),
    ).toBeNull()
  })

  it('rejeita título vazio', () => {
    expect(
      validarTarefaForm({
        titulo: '   ',
        descricao: 'Desc',
        prioridade: 'ALTA',
        prazo: futuro,
        duracaoHoras: 1,
      }),
    ).toMatch(/título/i)
  })

  it('rejeita título acima de 120 caracteres', () => {
    expect(
      validarTarefaForm({
        titulo: 'a'.repeat(121),
        descricao: 'Desc',
        prioridade: 'ALTA',
        prazo: futuro,
        duracaoHoras: 1,
      }),
    ).toMatch(/120/)
  })

  it('rejeita descrição vazia', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: '  ',
        prioridade: 'BAIXA',
        prazo: futuro,
        duracaoHoras: 1,
      }),
    ).toMatch(/descrição/i)
  })

  it('rejeita descrição acima de 2000 caracteres', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'a'.repeat(2001),
        prioridade: 'BAIXA',
        prazo: futuro,
        duracaoHoras: 1,
      }),
    ).toMatch(/2000/)
  })

  it('rejeita duração menor que 1', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'Desc',
        prioridade: 'MEDIA',
        prazo: futuro,
        duracaoHoras: 0,
      }),
    ).toMatch(/duração/i)
  })

  it('rejeita prazo no passado', () => {
    const passado = new Date(Date.now() - 60 * 1000).toISOString()
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'Desc',
        prioridade: 'MEDIA',
        prazo: passado,
        duracaoHoras: 1,
      }),
    ).toMatch(/prazo/i)
  })

  it('rejeita ATRASADA com prazo futuro no edit', () => {
    expect(
      validarTarefaForm(
        {
          titulo: 'Tarefa',
          descricao: 'Desc',
          prioridade: 'MEDIA',
          prazo: futuro,
          duracaoHoras: 1,
          status: 'ATRASADA',
        },
        { modo: 'editar' },
      ),
    ).toMatch(/ATRASADA/i)
  })
})
