/** Lê `tagId` repetível da query string (contrato da API). */
export function lerTagIdsDaUrl(params: URLSearchParams): string[] {
  return [...new Set(params.getAll('tagId').map((id) => id.trim()).filter(Boolean))]
}

export function escreverTagIdsNaUrl(params: URLSearchParams, tagIds: string[]): URLSearchParams {
  const proximo = new URLSearchParams(params)
  proximo.delete('tagId')
  for (const id of tagIds) {
    proximo.append('tagId', id)
  }
  return proximo
}
