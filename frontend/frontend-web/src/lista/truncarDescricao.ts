/** Descrição curta para cards de lista; `null` quando não há texto. */
export function truncarDescricao(descricao: string | null | undefined, max = 120): string | null {
  const texto = descricao?.trim()
  if (!texto) {
    return null
  }
  return texto.length <= max ? texto : `${texto.slice(0, max - 1)}…`
}
