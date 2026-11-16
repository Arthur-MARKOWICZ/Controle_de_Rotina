import type { TagResumo } from '../../tag/api/tagApi'
import {
  adicionarDiasCivil,
  dataCivilExiste,
  fimDoDiaBrtIso,
  partesCivisBrt,
  proximoDiaSemana,
  weekdayIsoBrt,
  type DataCivil,
} from '../dataCivilBrt'
import type { Prioridade } from '../validation/tarefaForm'

export const ERRO_DURACAO = 'A duração deve ser no mínimo 15 minutos e múltiplo de 15.'
export const TITULO_MAXIMO = 120
export const ERRO_TITULO_LONGO = `O título deve ter no máximo ${TITULO_MAXIMO} caracteres.`
/** O campo aceita mais que o título para comportar os tokens. */
export const TEXTO_MAXIMO = 200

type TokenBase = {
  /** Posição da palavra no texto digitado. */
  indice: number
  /** Palavra exatamente como foi digitada. */
  textoOriginal: string
}

export type TokenDuracao = TokenBase & { tipo: 'duracao'; minutos: number; erro: string | null }
export type TokenPrazo = TokenBase & { tipo: 'prazo'; data: DataCivil; prazoIso: string }
export type TokenPrioridade = TokenBase & { tipo: 'prioridade'; prioridade: Prioridade }
/** `tag` nulo = tag inexistente no catálogo (pendente de confirmação). */
export type TokenTag = TokenBase & { tipo: 'tag'; nome: string; tag: TagResumo | null }

export type TokenQuickAdd = TokenDuracao | TokenPrazo | TokenPrioridade | TokenTag

export type ResultadoQuickAdd = {
  titulo: string
  /** Tokens efetivos, na ordem em que aparecem no texto. */
  tokens: TokenQuickAdd[]
  duracao: TokenDuracao | null
  prazo: TokenPrazo | null
  prioridade: TokenPrioridade | null
  tags: TokenTag[]
}

export type OpcoesQuickAdd = {
  ref?: Date
  tagsCatalogo?: TagResumo[]
  /** Textos de token (normalizados) que o usuário mandou de volta para o título. */
  rejeitados?: ReadonlySet<string>
}

export function normalizarToken(texto: string): string {
  return texto
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
}

/**
 * Chave de comparação de tag: só ignora maiúsculas, como a unicidade de nome no servidor
 * (`Ciência` e `Ciencia` são tags diferentes).
 */
export function chaveTag(nome: string): string {
  return nome.toLowerCase()
}

const DIAS_SEMANA: Record<string, number> = {
  seg: 1,
  segunda: 1,
  'segunda-feira': 1,
  ter: 2,
  terca: 2,
  'terca-feira': 2,
  qua: 3,
  quarta: 3,
  'quarta-feira': 3,
  qui: 4,
  quinta: 4,
  'quinta-feira': 4,
  sex: 5,
  sexta: 5,
  'sexta-feira': 5,
  sab: 6,
  sabado: 6,
  dom: 7,
  domingo: 7,
}

const PRIORIDADES: Record<string, Prioridade> = {
  '!alta': 'ALTA',
  '!media': 'MEDIA',
  '!baixa': 'BAIXA',
}

const ABREV_DIA = ['', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom']

type Classificado =
  | Omit<TokenDuracao, keyof TokenBase>
  | Omit<TokenPrazo, keyof TokenBase>
  | Omit<TokenPrioridade, keyof TokenBase>
  | { tipo: 'tag'; nome: string }

function duracaoMinutos(norm: string): number | null {
  const horas = /^(\d{1,3})h(?:(\d{1,2})(?:min|m)?)?$/.exec(norm)
  if (horas) {
    return Number(horas[1]) * 60 + Number(horas[2] ?? 0)
  }
  const minutos = /^(\d{1,4})(?:min|m)$/.exec(norm)
  return minutos ? Number(minutos[1]) : null
}

function prazoCivil(norm: string, hoje: DataCivil): DataCivil | null {
  if (norm === 'hoje') {
    return hoje
  }
  if (norm === 'amanha') {
    return adicionarDiasCivil(hoje.y, hoje.m, hoje.d, 1)
  }
  const wd = DIAS_SEMANA[norm]
  if (wd) {
    return proximoDiaSemana(hoje.y, hoje.m, hoje.d, wd)
  }
  const ddmm = /^(\d{1,2})\/(\d{1,2})$/.exec(norm)
  if (!ddmm) {
    return null
  }
  const d = Number(ddmm[1])
  const m = Number(ddmm[2])
  const jaPassou = m < hoje.m || (m === hoje.m && d < hoje.d)
  const y = jaPassou ? hoje.y + 1 : hoje.y
  return dataCivilExiste(y, m, d) ? { y, m, d } : null
}

function classificar(palavra: string, hoje: DataCivil): Classificado | null {
  const norm = normalizarToken(palavra)

  const minutos = duracaoMinutos(norm)
  if (minutos !== null) {
    const valida = minutos >= 15 && minutos % 15 === 0
    return { tipo: 'duracao', minutos, erro: valida ? null : ERRO_DURACAO }
  }

  const data = prazoCivil(norm, hoje)
  if (data) {
    return { tipo: 'prazo', data, prazoIso: fimDoDiaBrtIso(data.y, data.m, data.d) }
  }

  const prioridade = PRIORIDADES[norm]
  if (prioridade) {
    return { tipo: 'prioridade', prioridade }
  }

  if (/^#[^#]+$/.test(palavra)) {
    return { tipo: 'tag', nome: palavra.slice(1) }
  }
  return null
}

/**
 * Interpreta a linguagem curta do quick add (`Estudar 2h sex !alta #faculdade`).
 * Função pura: o resultado depende só do texto e das opções.
 */
export function interpretarQuickAdd(texto: string, opcoes: OpcoesQuickAdd = {}): ResultadoQuickAdd {
  const hoje = partesCivisBrt(opcoes.ref ?? new Date())
  const rejeitados = opcoes.rejeitados ?? new Set<string>()
  const catalogo = new Map(
    (opcoes.tagsCatalogo ?? []).map((tag) => [chaveTag(tag.nome), tag] as const),
  )

  const palavras = texto.split(/\s+/).filter(Boolean)
  const literais: (string | null)[] = palavras.map(() => null)
  const candidatos: (TokenQuickAdd | null)[] = palavras.map(() => null)

  palavras.forEach((palavra, indice) => {
    if (palavra.length > 1 && palavra.startsWith('\\')) {
      literais[indice] = palavra.slice(1)
      return
    }
    const classificado = rejeitados.has(normalizarToken(palavra)) ? null : classificar(palavra, hoje)
    if (!classificado) {
      literais[indice] = palavra
      return
    }
    const base = { indice, textoOriginal: palavra }
    candidatos[indice] =
      classificado.tipo === 'tag'
        ? {
            ...base,
            tipo: 'tag',
            nome: classificado.nome,
            tag: catalogo.get(chaveTag(classificado.nome)) ?? null,
          }
        : { ...base, ...classificado }
  })

  // Duração, prazo e prioridade: o último vence; os anteriores voltam ao título.
  const ultimoPorTipo = new Map<string, number>()
  candidatos.forEach((token, indice) => {
    if (token && token.tipo !== 'tag') {
      ultimoPorTipo.set(token.tipo, indice)
    }
  })
  const tagsVistas = new Set<string>()
  const tokens: TokenQuickAdd[] = []
  candidatos.forEach((token, indice) => {
    if (!token) {
      return
    }
    if (token.tipo === 'tag') {
      const chave = chaveTag(token.nome)
      if (!tagsVistas.has(chave)) {
        tagsVistas.add(chave)
        tokens.push(token)
      }
      return
    }
    if (ultimoPorTipo.get(token.tipo) === indice) {
      tokens.push(token)
    } else {
      literais[indice] = token.textoOriginal
    }
  })

  return {
    titulo: literais.filter((p): p is string => p !== null).join(' '),
    tokens,
    duracao: (tokens.find((t) => t.tipo === 'duracao') as TokenDuracao | undefined) ?? null,
    prazo: (tokens.find((t) => t.tipo === 'prazo') as TokenPrazo | undefined) ?? null,
    prioridade: (tokens.find((t) => t.tipo === 'prioridade') as TokenPrioridade | undefined) ?? null,
    tags: tokens.filter((t): t is TokenTag => t.tipo === 'tag'),
  }
}

/** Rótulo curto do prazo, ex.: `sex 20/11`. */
export function rotuloPrazo(data: DataCivil): string {
  const dd = String(data.d).padStart(2, '0')
  const mm = String(data.m).padStart(2, '0')
  return `${ABREV_DIA[weekdayIsoBrt(data.y, data.m, data.d)]} ${dd}/${mm}`
}

/** Rótulo curto da duração, ex.: `45 min`, `2h`, `1h30`. */
export function rotuloDuracao(minutos: number): string {
  if (minutos < 60) {
    return `${minutos} min`
  }
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return resto ? `${horas}h${String(resto).padStart(2, '0')}` : `${horas}h`
}
