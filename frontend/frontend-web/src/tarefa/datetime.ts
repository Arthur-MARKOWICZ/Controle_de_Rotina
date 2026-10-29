/** Converte Instant ISO da API para valor de input datetime-local (hora local). */
export function instantParaDatetimeLocal(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** Converte valor de datetime-local para Instant ISO enviado à API; vazio → null. */
export function datetimeLocalParaInstant(local: string): string | null {
  if (!local.trim()) {
    return null
  }
  const date = new Date(local)
  return date.toISOString()
}

export function formatarDataHora(iso: string | null | undefined): string {
  if (!iso) {
    return 'Sem prazo'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return date.toLocaleString('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}
