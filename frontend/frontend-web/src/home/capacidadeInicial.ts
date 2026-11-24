import type {
  CapacidadeSemanalResponse,
  CriarCapacidadeSemanalItem,
  DiaSemana,
} from '../capacidade/api/capacidadeSemanalApi'

export const HORAS_PRESET_DIAS_UTEIS = 4

const DIAS_UTEIS: DiaSemana[] = ['SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA']

/** Algum dia do template semanal com horas > 0 (mesma regra do onboarding). */
export function capacidadeConfigurada(lista: { horas: number; ativo: boolean }[]): boolean {
  return lista.some((item) => item.ativo && item.horas > 0)
}

export type AlteracoesDiasUteis = {
  /** Dias úteis sem registro ativo: vão num único `POST` em lote. */
  criar: CriarCapacidadeSemanalItem[]
  /** Ids de dias úteis com registro ativo em 0h: recebem `PATCH`. */
  atualizar: string[]
}

/** O que gravar para chegar em seg–sex 4h sem mexer em dia útil que já tem horas. */
export function alteracoesDiasUteis(semana: CapacidadeSemanalResponse[]): AlteracoesDiasUteis {
  const criar: CriarCapacidadeSemanalItem[] = []
  const atualizar: string[] = []
  for (const dia of DIAS_UTEIS) {
    const existente = semana.find((item) => item.ativo && item.diaSemana === dia)
    if (!existente) {
      criar.push({ diaSemana: dia, horas: HORAS_PRESET_DIAS_UTEIS })
    } else if (existente.horas === 0) {
      atualizar.push(existente.id)
    }
  }
  return { criar, atualizar }
}
