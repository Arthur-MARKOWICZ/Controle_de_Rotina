import { describe, expect, it } from 'vitest'

import { validarTarefaForm, validarTarefaFormCampos } from './tarefaForm'

describe('validarTarefaForm', () => {
  const futuro = new Date(Date.now() + 60 * 60 * 1000).toISOString()

  it('aceita dados válidos no create', () => {
    expect(
      validarTarefaForm({
        titulo: 'Estudar',
        descricao: 'Revisar Spring',
        prioridade: 'MEDIA',
        prazo: futuro,
        duracaoMinutos: 120,
      }),
    ).toBeNull()
  })

  it('aceita descrição vazia no create', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: '  ',
        prioridade: 'BAIXA',
        prazo: futuro,
        duracaoMinutos: 60,
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
        duracaoMinutos: 60,
      }),
    ).toMatch(/título/i)
  })

  it('T5: erro de título fica no campo', () => {
    const erros = validarTarefaFormCampos({
      titulo: '',
      descricao: '',
      prioridade: 'MEDIA',
      prazo: futuro,
      duracaoMinutos: 60,
    })
    expect(erros.titulo).toMatch(/título/i)
    expect(erros.descricao).toBeUndefined()
  })

  it('rejeita título acima de 120 caracteres', () => {
    expect(
      validarTarefaForm({
        titulo: 'a'.repeat(121),
        descricao: 'Desc',
        prioridade: 'ALTA',
        prazo: futuro,
        duracaoMinutos: 60,
      }),
    ).toMatch(/120/)
  })

  it('rejeita descrição acima de 2000 caracteres', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'a'.repeat(2001),
        prioridade: 'BAIXA',
        prazo: futuro,
        duracaoMinutos: 60,
      }),
    ).toMatch(/2000/)
  })

  it('rejeita duração menor que 15 ou não múltipla de 15', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'Desc',
        prioridade: 'MEDIA',
        prazo: futuro,
        duracaoMinutos: 0,
      }),
    ).toMatch(/duração/i)
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'Desc',
        prioridade: 'MEDIA',
        prazo: futuro,
        duracaoMinutos: 10,
      }),
    ).toMatch(/15/)
  })

  it('T5: aceita duração em minutos múltipla de 15', () => {
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'Desc',
        prioridade: 'MEDIA',
        prazo: futuro,
        duracaoMinutos: 90,
      }),
    ).toBeNull()
  })

  it('rejeita prazo no passado', () => {
    const passado = new Date(Date.now() - 60 * 1000).toISOString()
    expect(
      validarTarefaForm({
        titulo: 'Tarefa',
        descricao: 'Desc',
        prioridade: 'MEDIA',
        prazo: passado,
        duracaoMinutos: 60,
      }),
    ).toMatch(/prazo/i)
  })

  it('T4: aceita salvar sem prazo', () => {
    expect(
      validarTarefaForm({
        titulo: 'Sem prazo',
        descricao: '',
        prioridade: 'MEDIA',
        prazo: '',
        duracaoMinutos: 60,
      }),
    ).toBeNull()
  })

  it('rejeita ATRASADA com prazo futuro no edit', () => {
    expect(
      validarTarefaForm(
        {
          titulo: 'Tarefa',
          descricao: 'Desc',
          prioridade: 'MEDIA',
          prazo: futuro,
          duracaoMinutos: 60,
          status: 'ATRASADA',
        },
        { modo: 'editar' },
      ),
    ).toMatch(/ATRASADA/i)
  })
})
