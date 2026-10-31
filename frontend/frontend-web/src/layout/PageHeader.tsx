import type { ReactNode } from 'react'

type Props = {
  title: string
  description?: ReactNode
  actions?: ReactNode
}

export function PageHeader({ title, description, actions }: Props) {
  return (
    <header className="page-header">
      <div className="page-header__texto">
        <h1 className="page-header__titulo">{title}</h1>
        {description ? <p className="page-header__descricao">{description}</p> : null}
      </div>
      {actions ? <div className="page-header__acoes">{actions}</div> : null}
    </header>
  )
}
