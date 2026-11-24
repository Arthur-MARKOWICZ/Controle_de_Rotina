import { describe, expect, it } from 'vitest'

import { LINHAS, corDaLinha } from './linhaCor'

describe('corDaLinha', () => {
  it('deveUsarUmaDasCoresDeLinhaParaBlocoDeProjeto', () => {
    expect(LINHAS).toContain(corDaLinha({ origem: 'TAREFA', projetoNome: 'TCC' }))
  })

  it('deveDarSempreAMesmaCorParaOMesmoProjeto', () => {
    const primeira = corDaLinha({ origem: 'TAREFA', projetoNome: 'TCC' })

    expect(corDaLinha({ origem: 'TAREFA', projetoNome: 'TCC' })).toBe(primeira)
  })

  it('deveIgnorarCaixaEEspacosNoNomeDoProjeto', () => {
    expect(corDaLinha({ origem: 'TAREFA', projetoNome: '  tcc ' })).toBe(
      corDaLinha({ origem: 'TAREFA', projetoNome: 'TCC' }),
    )
  })

  it('deveEspalharProjetosDiferentesPorMaisDeUmaCor', () => {
    const nomes = ['TCC', 'Certificação', 'Casa', 'Trabalho', 'Academia', 'Inglês', 'Viagem', 'Finanças']
    const cores = new Set(nomes.map((projetoNome) => corDaLinha({ origem: 'TAREFA', projetoNome })))

    expect(cores.size).toBeGreaterThan(1)
  })

  it('deveUsarCorDeHabitoParaBlocoDeHabitoMesmoComProjeto', () => {
    expect(corDaLinha({ origem: 'HABITO', projetoNome: 'TCC' })).toBe('var(--linha-habito)')
  })

  it('deveUsarAAcaoQuandoATarefaNaoTemProjeto', () => {
    expect(corDaLinha({ origem: 'TAREFA', projetoNome: null })).toBe('var(--accent)')
  })

  it('deveTratarNomeVazioComoSemProjeto', () => {
    expect(corDaLinha({ origem: 'TAREFA', projetoNome: '   ' })).toBe('var(--accent)')
  })

  it('deveAssumirTarefaQuandoAOrigemNaoVem', () => {
    expect(corDaLinha({ projetoNome: undefined })).toBe('var(--accent)')
  })
})
