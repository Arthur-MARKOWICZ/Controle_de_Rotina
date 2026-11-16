import {
  adicionarDiasCivil,
  fimDoDiaBrtParaDatetimeLocal,
  partesCivisBrt,
  proximoDiaSemana,
} from './dataCivilBrt'

export { fimDoDiaBrtParaDatetimeLocal }

export type PrazoAtalhoId = 'hoje' | 'amanha' | 'em_3_dias' | 'proxima_segunda'

export type PrazoAtalho = {
  id: PrazoAtalhoId
  label: string
  /** Valor para input datetime-local. */
  prazoLocal: string
}

export function atalhosPrazo(ref: Date = new Date()): PrazoAtalho[] {
  const hoje = partesCivisBrt(ref)
  const amanha = adicionarDiasCivil(hoje.y, hoje.m, hoje.d, 1)
  const em3 = adicionarDiasCivil(hoje.y, hoje.m, hoje.d, 3)
  const segunda = proximoDiaSemana(hoje.y, hoje.m, hoje.d, 1)

  return [
    { id: 'hoje', label: 'Hoje', prazoLocal: fimDoDiaBrtParaDatetimeLocal(hoje.y, hoje.m, hoje.d) },
    {
      id: 'amanha',
      label: 'Amanhã',
      prazoLocal: fimDoDiaBrtParaDatetimeLocal(amanha.y, amanha.m, amanha.d),
    },
    {
      id: 'em_3_dias',
      label: 'Em 3 dias',
      prazoLocal: fimDoDiaBrtParaDatetimeLocal(em3.y, em3.m, em3.d),
    },
    {
      id: 'proxima_segunda',
      label: 'Próxima segunda',
      prazoLocal: fimDoDiaBrtParaDatetimeLocal(segunda.y, segunda.m, segunda.d),
    },
  ]
}
