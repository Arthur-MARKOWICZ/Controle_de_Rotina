import { XIcon } from '@phosphor-icons/react'
import type { CSSProperties } from 'react'

import { PRIORIDADE_LABEL } from '../../home/labels'
import { TAG_COR_PADRAO } from '../../tag/api/tagApi'
import {
  chaveTag,
  rotuloDuracao,
  rotuloPrazo,
  type TokenQuickAdd,
  type TokenTag,
} from './interpretarQuickAdd'

type Props = {
  tokens: TokenQuickAdd[]
  /** Nomes (minúsculos) das tags novas que o usuário confirmou criar. */
  tagsConfirmadas: ReadonlySet<string>
  onRemover: (token: TokenQuickAdd) => void
  onAlternarTag: (token: TokenTag) => void
  disabled?: boolean
}

/** Descrição curta do chip, usada no texto visível e no nome acessível. */
export function descreverToken(token: TokenQuickAdd): string {
  switch (token.tipo) {
    case 'duracao':
      return `duração ${rotuloDuracao(token.minutos)}`
    case 'prazo':
      return `prazo ${rotuloPrazo(token.data)}`
    case 'prioridade':
      return `prioridade ${PRIORIDADE_LABEL[token.prioridade]}`
    case 'tag':
      return `tag ${token.tag?.nome ?? token.nome}`
  }
}

function Conteudo({
  token,
  confirmada,
  onAlternarTag,
  disabled,
}: {
  token: TokenQuickAdd
  confirmada: boolean
  onAlternarTag: (token: TokenTag) => void
  disabled: boolean
}) {
  switch (token.tipo) {
    case 'duracao':
      return <span>{rotuloDuracao(token.minutos)}</span>
    case 'prazo':
      return <span>{rotuloPrazo(token.data)}</span>
    case 'prioridade':
      return <span>{PRIORIDADE_LABEL[token.prioridade]}</span>
    case 'tag':
      if (token.tag) {
        return <span>#{token.tag.nome}</span>
      }
      return (
        <>
          <span>{confirmada ? `#${token.nome} (nova)` : `criar #${token.nome}?`}</span>
          <button
            type="button"
            className="quick-add-chip__acao"
            onClick={() => onAlternarTag(token)}
            disabled={disabled}
            aria-pressed={confirmada}
            aria-label={confirmada ? `Desfazer criação da tag ${token.nome}` : `Criar tag ${token.nome}`}
          >
            {confirmada ? 'Desfazer' : 'Criar'}
          </button>
        </>
      )
  }
}

export function QuickAddChips({ tokens, tagsConfirmadas, onRemover, onAlternarTag, disabled = false }: Props) {
  if (!tokens.length) {
    return null
  }
  return (
    <ul className="quick-add-chips" aria-label="Valores interpretados">
      {tokens.map((token) => {
        const erro = token.tipo === 'duracao' ? token.erro : null
        const pendente = token.tipo === 'tag' && !token.tag
        const confirmada = token.tipo === 'tag' && tagsConfirmadas.has(chaveTag(token.nome))
        const cor = token.tipo === 'tag' ? (token.tag?.cor ?? TAG_COR_PADRAO) : undefined
        const classes = [
          'quick-add-chip',
          `quick-add-chip--${token.tipo}`,
          erro ? 'quick-add-chip--erro' : '',
          pendente && !confirmada ? 'quick-add-chip--pendente' : '',
        ]
          .filter(Boolean)
          .join(' ')
        return (
          <li
            key={`${token.tipo}-${token.indice}`}
            className={classes}
            style={cor ? ({ '--tag-cor': cor } as CSSProperties) : undefined}
          >
            <Conteudo
              token={token}
              confirmada={confirmada}
              onAlternarTag={onAlternarTag}
              disabled={disabled}
            />
            <button
              type="button"
              className="quick-add-chip__remover"
              onClick={() => onRemover(token)}
              disabled={disabled}
              aria-label={`Remover ${descreverToken(token)}`}
            >
              <XIcon size={12} weight="bold" aria-hidden />
            </button>
            {erro ? (
              <span className="quick-add-chip__erro" role="alert">
                {erro}
              </span>
            ) : null}
          </li>
        )
      })}
    </ul>
  )
}
