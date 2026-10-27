export function formatarMinutosTrabalhados(minutos: number): string {
  const total = Math.max(0, Math.floor(minutos))
  const horas = Math.floor(total / 60)
  const resto = total % 60
  if (horas === 0) {
    return `${resto}min`
  }
  if (resto === 0) {
    return `${horas}h`
  }
  return `${horas}h${String(resto).padStart(2, '0')}`
}
