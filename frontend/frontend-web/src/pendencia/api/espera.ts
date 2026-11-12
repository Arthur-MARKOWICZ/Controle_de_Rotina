/** Tipos compartilhados da relação de espera (tarefa/projeto aguarda tarefa/projeto). */

export type TipoRecursoEspera = 'TAREFA' | 'PROJETO'

export type EsperaResumo = {
  id: string
  alvoTipo: TipoRecursoEspera
  alvoId: string
  alvoTitulo: string | null
  resolvida: boolean
}

export type CriarEsperaRequest = {
  alvoTipo: TipoRecursoEspera
  alvoId: string
}

/** Esperas ainda não resolvidas — as que de fato bloqueiam a origem. */
export function esperasIrresolvidas(esperas: EsperaResumo[] | undefined): EsperaResumo[] {
  return (esperas ?? []).filter((espera) => !espera.resolvida)
}

/** Rótulo curto para badge: um alvo vira o título; vários viram o resumo. */
export function rotuloEspera(esperas: EsperaResumo[] | undefined): string | null {
  const abertas = esperasIrresolvidas(esperas)
  if (abertas.length === 0) {
    return null
  }
  if (abertas.length === 1) {
    const titulo = abertas[0].alvoTitulo?.trim()
    return titulo ? `Espera ${titulo}` : 'Espera 1 item'
  }
  return `Espera ${abertas.length} itens`
}
