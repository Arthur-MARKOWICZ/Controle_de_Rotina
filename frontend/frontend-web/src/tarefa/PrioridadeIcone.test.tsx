import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { PrioridadeIcone } from './PrioridadeIcone'

describe('PrioridadeIcone', () => {
  it('deveDesenharFormaDiferenteParaCadaPrioridade', () => {
    const formas = (['ALTA', 'MEDIA', 'BAIXA'] as const).map((prioridade) => {
      const { container, unmount } = render(<PrioridadeIcone prioridade={prioridade} />)
      const svg = container.querySelector('svg')
      const forma = svg?.innerHTML ?? ''
      unmount()
      return forma
    })

    expect(formas.every((forma) => forma.length > 0)).toBe(true)
    expect(new Set(formas).size).toBe(3)
  })

  it('deveAplicarClasseDaPrioridadeParaACor', () => {
    const { container } = render(<PrioridadeIcone prioridade="ALTA" />)

    expect(container.querySelector('svg')).toHaveClass('prioridade-icone', 'prioridade-icone--alta')
  })

  it('deveSerDecorativoQuandoORotuloVemDeFora', () => {
    const { container } = render(<PrioridadeIcone prioridade="MEDIA" />)

    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('deveAceitarClasseExtraSemPerderAClasseBase', () => {
    const { container } = render(<PrioridadeIcone prioridade="BAIXA" className="calendario-pilula__icone" />)

    expect(container.querySelector('svg')).toHaveClass(
      'prioridade-icone',
      'prioridade-icone--baixa',
      'calendario-pilula__icone',
    )
  })

  it('naoDeveRenderizarNadaSemPrioridade', () => {
    const { container } = render(<PrioridadeIcone prioridade={null} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('naoDeveRenderizarNadaParaPrioridadeDesconhecida', () => {
    const { container } = render(
      <PrioridadeIcone prioridade={'URGENTE' as unknown as 'ALTA'} />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('naoDeveEntrarNaArvoreAcessivel', () => {
    const { container } = render(<PrioridadeIcone prioridade="ALTA" />)

    expect(container.querySelector('[role="img"]')).toBeNull()
    expect(container.querySelector('svg')?.getAttribute('aria-label')).toBeNull()
  })
})
