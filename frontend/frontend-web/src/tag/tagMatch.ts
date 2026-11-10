import type { TagResumo } from './api/tagApi'

export function calcularTagMatchScore(
  tags: TagResumo[] | undefined,
  filtroIds: string[],
  scoreApi?: number | null,
): number {
  if (filtroIds.length < 2) {
    return 0
  }
  if (scoreApi != null) {
    return scoreApi
  }
  const ids = new Set(tags?.map((t) => t.id) ?? [])
  const todas = filtroIds.every((id) => ids.has(id))
  return todas ? 2 : 1
}

export type ItemComTags = {
  tags?: TagResumo[]
  tagMatchScore?: number | null
}

/** Estável: match E (2) antes de OU (1) quando há 2+ tags no filtro. */
export function ordenarPorTagMatch<T extends ItemComTags>(itens: T[], filtroIds: string[]): T[] {
  if (filtroIds.length < 2) {
    return itens
  }
  return [...itens].sort((a, b) => {
    const sa = calcularTagMatchScore(a.tags, filtroIds, a.tagMatchScore)
    const sb = calcularTagMatchScore(b.tags, filtroIds, b.tagMatchScore)
    return sb - sa
  })
}
