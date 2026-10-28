/** Data por extenso em pt-BR (ex.: "Terça, 27 de outubro"). */
export function formatarDataPorExtenso(ref: Date = new Date()): string {
  const raw = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone: 'America/Sao_Paulo',
  }).format(ref)

  return raw.charAt(0).toUpperCase() + raw.slice(1)
}
