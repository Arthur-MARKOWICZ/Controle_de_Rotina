const TIME_ZONE = 'America/Sao_Paulo'

/** Meio-dia em São Paulo: a data civil não muda com o fuso de quem formata. */
function meioDiaSaoPaulo(iso: string): Date {
  return new Date(`${iso}T15:00:00Z`)
}

/** Data por extenso em pt-BR (ex.: "Terça, 27 de outubro"). */
export function formatarDataPorExtenso(ref: Date = new Date()): string {
  const raw = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone: TIME_ZONE,
  }).format(ref)

  return raw.charAt(0).toUpperCase() + raw.slice(1)
}

/** Data ISO (YYYY-MM-DD) por extenso. */
export function formatarDataIsoPorExtenso(iso: string): string {
  return formatarDataPorExtenso(meioDiaSaoPaulo(iso))
}

/** Rótulo curto de dia (ex.: "ter 04/11"). */
export function formatarDiaCurto(iso: string): string {
  const ref = meioDiaSaoPaulo(iso)
  const semana = new Intl.DateTimeFormat('pt-BR', { weekday: 'short', timeZone: TIME_ZONE })
    .format(ref)
    .replace('.', '')
  const [, mes, dia] = iso.split('-')
  return `${semana} ${dia}/${mes}`
}
