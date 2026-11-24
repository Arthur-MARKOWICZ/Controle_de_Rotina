import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import type { ObjetivoItens } from './api/objetivoApi'
import { ObjetivoItensSecao } from './ObjetivoItensSecao'

const ITENS: ObjetivoItens = {
  tarefas: [
    {
      id: 't-1',
      titulo: 'Estudar S3',
      status: 'PENDENTE',
      origem: 'DIRETO',
      projetoId: null,
      projetoNome: null,
    },
    {
      id: 't-2',
      titulo: 'Simulado 1',
      status: 'FINALIZADA',
      origem: 'VIA_PROJETO',
      projetoId: 'p-1',
      projetoNome: 'Estudos AWS',
    },
  ],
  habitos: [{ id: 'h-1', titulo: 'Flashcards', status: 'PAUSADO', origem: 'DIRETO' }],
  projetos: [{ id: 'p-1', nome: 'Estudos AWS', status: 'ATIVO', origem: 'DIRETO' }],
}

function renderSecao(props: Partial<React.ComponentProps<typeof ObjetivoItensSecao>> = {}) {
  return render(
    <MemoryRouter>
      <ObjetivoItensSecao itens={ITENS} carregando={false} erro={null} {...props} />
    </MemoryRouter>,
  )
}

function grupo(nome: RegExp): HTMLElement {
  return screen.getByRole('heading', { name: nome }).parentElement as HTMLElement
}

describe('ObjetivoItensSecao', () => {
  it('mostra os três grupos com contagem, status e links para a tela de cada item', () => {
    renderSecao()

    const tarefas = grupo(/^Tarefas \(2\)$/)
    expect(within(tarefas).getByRole('link', { name: 'Estudar S3' })).toHaveAttribute(
      'href',
      '/tarefas/t-1',
    )
    expect(tarefas).toHaveTextContent('Pendente')
    expect(tarefas).toHaveTextContent('Finalizada')

    const habitos = grupo(/^Hábitos \(1\)$/)
    expect(within(habitos).getByRole('link', { name: 'Flashcards' })).toHaveAttribute(
      'href',
      '/habitos/h-1',
    )
    expect(habitos).toHaveTextContent('Pausado')

    const projetos = grupo(/^Projetos \(1\)$/)
    expect(within(projetos).getByRole('link', { name: 'Estudos AWS' })).toHaveAttribute(
      'href',
      '/projetos/p-1',
    )
  })

  it('selo "via projeto X" só na tarefa herdada', () => {
    renderSecao()

    const tarefas = grupo(/^Tarefas/)
    const diretas = within(tarefas).getByRole('link', { name: 'Estudar S3' }).closest('li')
    const herdada = within(tarefas).getByRole('link', { name: 'Simulado 1' }).closest('li')
    expect(diretas).not.toHaveTextContent('via projeto')
    expect(herdada).toHaveTextContent('via projeto Estudos AWS')
  })

  it('tarefa herdada sem nome de projeto mostra só "via projeto", sem quebrar', () => {
    renderSecao({
      itens: {
        tarefas: [
          {
            id: 't-9',
            titulo: 'Órfã',
            status: 'ATRASADA',
            origem: 'VIA_PROJETO',
            projetoId: null,
            projetoNome: null,
          },
        ],
        habitos: [],
        projetos: [],
      },
    })

    expect(screen.getByText('via projeto')).toBeInTheDocument()
    expect(screen.getByText('Atrasada')).toBeInTheDocument()
  })

  it('sem nenhum item mostra o estado vazio com a orientação e nenhum grupo', () => {
    renderSecao({ itens: { tarefas: [], habitos: [], projetos: [] } })

    const vazio = screen.getByRole('status')
    expect(vazio).toHaveTextContent('Nenhum item vinculado')
    expect(vazio).toHaveTextContent('formulário de uma tarefa, de um hábito ou de um projeto')
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
  })

  it('grupo vazio não é exibido: só hábitos esconde Tarefas e Projetos', () => {
    renderSecao({ itens: { tarefas: [], habitos: ITENS.habitos, projetos: [] } })

    expect(screen.getByRole('heading', { name: /^Hábitos \(1\)$/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^Tarefas/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^Projetos/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('carregando mostra o placeholder, sem grupos nem estado vazio', () => {
    const { container } = renderSecao({ itens: null, carregando: true })

    expect(container.querySelector('.skeleton')).not.toBeNull()
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
    expect(screen.queryByText('Nenhum item vinculado')).not.toBeInTheDocument()
  })

  it('erro mostra o alerta e nenhum grupo nem estado vazio', () => {
    renderSecao({ itens: null, erro: 'Não foi possível carregar os itens vinculados.' })

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Não foi possível carregar os itens vinculados.',
    )
    expect(screen.queryByRole('heading', { level: 3 })).not.toBeInTheDocument()
    expect(screen.queryByText('Nenhum item vinculado')).not.toBeInTheDocument()
  })

  it('erro com itens antigos em memória não mostra os grupos', () => {
    renderSecao({ erro: 'Não foi possível carregar os itens vinculados.' })

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^Tarefas/ })).not.toBeInTheDocument()
  })
})
