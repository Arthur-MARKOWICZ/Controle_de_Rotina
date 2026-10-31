import type { RefObject } from 'react'

type CampoComErro = [erro: string | undefined, ref: RefObject<HTMLInputElement | null>]

/** Foca o primeiro campo (na ordem da tela) que tem erro. */
export function focarPrimeiroInvalido(campos: CampoComErro[]): void {
  campos.find(([erro]) => erro)?.[1].current?.focus()
}

export function temErro(erros: Record<string, string | undefined>): boolean {
  return Object.values(erros).some(Boolean)
}
