import type { CSSProperties } from 'react'

import type { TagResumo } from './api/tagApi'

type Props = {
  tag: TagResumo
  className?: string
}

export function TagChip({ tag, className }: Props) {
  const classe = className ? `tag-chip ${className}` : 'tag-chip'
  return (
    <span
      className={classe}
      style={{ '--tag-cor': tag.cor } as CSSProperties}
      title={tag.nome}
    >
      {tag.nome}
    </span>
  )
}

type ListaProps = {
  tags?: TagResumo[]
  className?: string
}

export function TagChipList({ tags, className }: ListaProps) {
  if (!tags?.length) {
    return null
  }
  const classe = className ? `tag-chip-list ${className}` : 'tag-chip-list'
  return (
    <span className={classe} aria-label="Tags">
      {tags.map((tag) => (
        <TagChip key={tag.id} tag={tag} />
      ))}
    </span>
  )
}
