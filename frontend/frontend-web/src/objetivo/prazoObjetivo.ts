import { dataHojeIso } from '../planner/data'

export type SituacaoPrazo = 'sem-prazo' | 'vencido' | 'hoje' | 'futuro'

const DATA_ISO = /^\d{4}-\d{2}-\d{2}$/
const DIA_MS = 24 * 60 * 60 * 1000

function dataValida(prazo: string | null | undefined): prazo is string {
  return typeof prazo === 'string' && DATA_ISO.test(prazo) && !Number.isNaN(Date.parse(prazo))
}

function diasEntre(deIso: string, ateIso: string): number {
  return Math.round((Date.parse(ateIso) - Date.parse(deIso)) / DIA_MS)
}

/** "20 nov" (ou "20 nov 2027" fora do ano de referência); entrada `yyyy-MM-dd`. */
function formatarDiaMes(iso: string, anoReferencia: string): string {
  const [ano, mes, dia] = iso.split('-').map(Number)
  const mesCurto = new Intl.DateTimeFormat('pt-BR', { month: 'short', timeZone: 'UTC' })
    .format(new Date(Date.UTC(ano, mes - 1, dia)))
    .replace('.', '')
  const base = `${dia} ${mesCurto}`
  return String(ano) === anoReferencia ? base : `${base} ${ano}`
}

/** Prazo é data civil; a comparação com hoje (BRT) é feita como texto `yyyy-MM-dd`. */
export function situacaoPrazo(
  prazo: string | null | undefined,
  hojeIso: string = dataHojeIso(),
): SituacaoPrazo {
  if (!dataValida(prazo)) {
    return 'sem-prazo'
  }
  if (prazo < hojeIso) {
    return 'vencido'
  }
  return prazo === hojeIso ? 'hoje' : 'futuro'
}

export function rotuloPrazo(
  prazo: string | null | undefined,
  hojeIso: string = dataHojeIso(),
): string | null {
  const situacao = situacaoPrazo(prazo, hojeIso)
  if (situacao === 'sem-prazo' || !prazo) {
    return null
  }
  const anoHoje = hojeIso.slice(0, 4)
  const data = formatarDiaMes(prazo, anoHoje)
  if (situacao === 'vencido') {
    return `Prazo vencido · ${data}`
  }
  if (situacao === 'hoje') {
    return 'Vence hoje'
  }
  const dias = diasEntre(hojeIso, prazo)
  return `Até ${data} · ${dias === 1 ? 'falta 1 dia' : `faltam ${dias} dias`}`
}

/** "Prefixo 12 nov" (com o ano fora do ano de hoje). O instante é convertido para a data civil em BRT. */
function rotuloDeInstante(
  prefixo: string,
  instante: string | null | undefined,
  hojeIso: string,
): string | null {
  if (!instante || Number.isNaN(Date.parse(instante))) {
    return null
  }
  return `${prefixo} ${formatarDiaMes(dataHojeIso(new Date(instante)), hojeIso.slice(0, 4))}`
}

export function rotuloConclusao(
  concluidoEm: string | null | undefined,
  hojeIso: string = dataHojeIso(),
): string | null {
  return rotuloDeInstante('Concluído em', concluidoEm, hojeIso)
}

export function rotuloArquivamento(
  arquivadoEm: string | null | undefined,
  hojeIso: string = dataHojeIso(),
): string | null {
  return rotuloDeInstante('Arquivado em', arquivadoEm, hojeIso)
}
