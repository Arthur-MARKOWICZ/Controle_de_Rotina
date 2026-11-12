export function moverItem<T>(itens: T[], from: number, to: number): T[] {
  if (
    from === to ||
    from < 0 ||
    to < 0 ||
    from >= itens.length ||
    to >= itens.length
  ) {
    return itens
  }
  const proximo = [...itens]
  const [item] = proximo.splice(from, 1)
  proximo.splice(to, 0, item)
  return proximo
}

export function compararOrdem(
  a: { ordem?: number; createdAt: string },
  b: { ordem?: number; createdAt: string },
): number {
  const oa = a.ordem ?? 0
  const ob = b.ordem ?? 0
  if (oa !== ob) {
    return oa - ob
  }
  return a.createdAt.localeCompare(b.createdAt)
}

export function mesmaOrdem(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((id, indice) => id === b[indice])
}
