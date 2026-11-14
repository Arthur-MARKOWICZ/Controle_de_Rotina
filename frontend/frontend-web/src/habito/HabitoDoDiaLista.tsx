import { CheckIcon, PlayIcon, StopIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import {
  abortarExecucao,
  concluirExecucao,
} from '../execucao/api/execucaoApi'
import { useToast } from '../feedback/ToastProvider'
import { formatarCapacidadeMinutos } from '../planner/data'
import {
  concluirHabitoDireto,
  iniciarHabitoAvulso,
  listarHabitosDoDia,
  type HabitoDoDia,
  type SlotExecucaoHabito,
} from './api/habitoApi'
import { useMinutosHabito } from './useMinutosHabito'

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

function slotPreferido(habito: HabitoDoDia): SlotExecucaoHabito | null {
  const plano = habito.execucaoNoPlano
  const avulsa = habito.execucaoAvulsa
  if (plano?.status === 'EM_ANDAMENTO') {
    return plano
  }
  if (avulsa?.status === 'EM_ANDAMENTO') {
    return avulsa
  }
  if (plano?.status === 'CONCLUIDO') {
    return plano
  }
  if (avulsa?.status === 'CONCLUIDO') {
    return avulsa
  }
  return plano ?? avulsa
}

type Props = {
  habitos: HabitoDoDia[]
  data: string
  onAtualizado: (habitos: HabitoDoDia[]) => void
  tituloSecao?: string
}

export function HabitoDoDiaLista({
  habitos,
  data,
  onAtualizado,
  tituloSecao = 'Hábitos',
}: Props) {
  const toast = useToast()
  const [pendenteId, setPendenteId] = useState<string | null>(null)

  const { pedirMinutos, dialog: minutosDialog } = useMinutosHabito()

  async function refrescar() {
    const lista = await listarHabitosDoDia(data)
    onAtualizado(lista)
  }

  async function handleIniciar(habito: HabitoDoDia) {
    setPendenteId(habito.id)
    try {
      await iniciarHabitoAvulso(habito.id, data)
      await refrescar()
    } catch (error) {
      toast.error(mensagemErro(error, 'Não foi possível iniciar o hábito.'))
    } finally {
      setPendenteId(null)
    }
  }

  async function handleConcluirDireto(habito: HabitoDoDia) {
    const minutos = await pedirMinutos(habito.titulo, habito.duracaoMinutos)
    if (minutos === null) {
      return
    }
    setPendenteId(habito.id)
    try {
      const execucao = await concluirHabitoDireto(habito.id, data, minutos)
      const xp = execucao.xp?.ganhoNesteRequest ?? 0
      if (xp > 0) {
        toast.success(`+${xp} XP`)
      } else {
        toast.success('Hábito concluído')
      }
      await refrescar()
    } catch (error) {
      toast.error(mensagemErro(error, 'Não foi possível concluir o hábito.'))
    } finally {
      setPendenteId(null)
    }
  }

  async function handleConcluir(habito: HabitoDoDia) {
    const slot = slotPreferido(habito)
    const execucaoId = slot?.execucaoId
    if (!execucaoId) {
      toast.error('Execução não encontrada para concluir.')
      return
    }
    setPendenteId(habito.id)
    try {
      const execucao = await concluirExecucao(execucaoId)
      const xp = execucao.xp?.ganhoNesteRequest ?? 0
      if (xp > 0) {
        toast.success(`+${xp} XP`)
      }
      await refrescar()
    } catch (error) {
      toast.error(mensagemErro(error, 'Não foi possível concluir o hábito.'))
    } finally {
      setPendenteId(null)
    }
  }

  async function handleAbortar(habito: HabitoDoDia) {
    const slot = slotPreferido(habito)
    const execucaoId = slot?.execucaoId
    if (!execucaoId) {
      toast.error('Execução não encontrada para abortar.')
      return
    }
    setPendenteId(habito.id)
    try {
      await abortarExecucao(execucaoId)
      await refrescar()
    } catch (error) {
      toast.error(mensagemErro(error, 'Não foi possível abortar o hábito.'))
    } finally {
      setPendenteId(null)
    }
  }

  if (habitos.length === 0) {
    return (
      <section className="habito-do-dia" aria-label={tituloSecao}>
        <h3 className="habito-do-dia__titulo">{tituloSecao}</h3>
        <p className="habito-do-dia__vazio" role="status">
          Nenhum hábito neste dia.
        </p>
      </section>
    )
  }

  return (
    <section className="habito-do-dia" aria-label={tituloSecao}>
      <h3 className="habito-do-dia__titulo">{tituloSecao}</h3>
      <ul className="habito-do-dia__lista">
        {habitos.map((habito) => {
          const slot = slotPreferido(habito)
          const status = slot?.status ?? 'NAO_INICIADO'
          const emAndamento = status === 'EM_ANDAMENTO'
          const concluido = status === 'CONCLUIDO' || habito.cumprido
          const podeIniciar = !concluido && !emAndamento
          const pendente = pendenteId === habito.id
          return (
            <li key={habito.id} className="habito-do-dia__item">
              <div className="habito-do-dia__corpo">
                <Link to={`/habitos/${habito.id}`} className="habito-do-dia__nome">
                  {habito.titulo}
                </Link>
                <p className="habito-do-dia__meta">
                  {formatarCapacidadeMinutos(habito.duracaoMinutos)}
                  {habito.horarioPreferencial
                    ? ` · preferência ${habito.horarioPreferencial.slice(0, 5)}`
                    : ''}
                  {` · streak ${habito.streakAtual}`}
                  {concluido ? ' · cumprido' : ''}
                  {habito.noPlanoAprovado ? ' · no plano' : ''}
                </p>
              </div>
              <div className="habito-do-dia__acoes">
                {podeIniciar ? (
                  <>
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      disabled={pendente}
                      onClick={() => void handleIniciar(habito)}
                      aria-label={`Iniciar ${habito.titulo}`}
                    >
                      <PlayIcon aria-hidden />
                      Iniciar
                    </button>
                    <button
                      type="button"
                      className="btn btn--secondary btn--sm"
                      disabled={pendente}
                      onClick={() => void handleConcluirDireto(habito)}
                      aria-label={`Concluir direto ${habito.titulo}`}
                    >
                      <CheckIcon aria-hidden />
                      Concluir
                    </button>
                  </>
                ) : null}
                {emAndamento ? (
                  <>
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      disabled={pendente}
                      onClick={() => void handleConcluir(habito)}
                      aria-label={`Concluir ${habito.titulo}`}
                    >
                      <CheckIcon aria-hidden />
                      Concluir
                    </button>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      disabled={pendente}
                      onClick={() => void handleAbortar(habito)}
                      aria-label={`Abortar ${habito.titulo}`}
                    >
                      <StopIcon aria-hidden />
                      Abortar
                    </button>
                  </>
                ) : null}
              </div>
            </li>
          )
        })}
      </ul>
      {minutosDialog}
    </section>
  )
}
