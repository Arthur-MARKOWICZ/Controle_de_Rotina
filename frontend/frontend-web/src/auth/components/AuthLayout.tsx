import type { ReactNode } from 'react'

import { LogoRotina } from '../../brand/LogoRotina'

const PROPOSTA_VALOR = 'Diga o que precisa fazer; o plano do dia sai pronto.'

type Props = {
  titulo: string
  subtitulo: string
  rodape: ReactNode
  children: ReactNode
}

export function AuthLayout({ titulo, subtitulo, rodape, children }: Props) {
  return (
    <main className="auth-page">
      <div className="auth-marca">
        <p className="auth-marca__nome">
          <LogoRotina />
          Rotina
        </p>
        <p className="auth-marca__frase">{PROPOSTA_VALOR}</p>
      </div>

      <div className="auth-conteudo">
        <section className="auth-panel card" aria-labelledby="auth-titulo">
          <h1 id="auth-titulo">{titulo}</h1>
          <p className="auth-subtitle">{subtitulo}</p>
          {children}
          <p className="auth-footer">{rodape}</p>
        </section>
      </div>
    </main>
  )
}
