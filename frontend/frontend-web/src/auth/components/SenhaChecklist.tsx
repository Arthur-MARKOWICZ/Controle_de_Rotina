import { regrasSenha } from '../validation/senhaForte'

type Props = {
  senha: string
  id?: string
}

export function SenhaChecklist({ senha, id = 'senha-checklist' }: Props) {
  const regras = regrasSenha(senha)

  return (
    <ul className="senha-checklist" id={id} aria-label="Requisitos da senha">
      {regras.map((regra) => (
        <li
          key={regra.id}
          className={
            regra.ok ? 'senha-checklist__item senha-checklist__item--ok' : 'senha-checklist__item'
          }
          data-regra={regra.id}
          data-ok={regra.ok ? 'true' : 'false'}
        >
          <span aria-hidden="true">{regra.ok ? '✓' : '○'}</span>
          <span>{regra.label}</span>
          <span className="visually-hidden">{regra.ok ? 'atendido' : 'pendente'}</span>
        </li>
      ))}
    </ul>
  )
}
