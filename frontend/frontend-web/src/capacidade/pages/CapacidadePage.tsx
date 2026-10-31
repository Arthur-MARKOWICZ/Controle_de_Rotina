import {
  CaretLeftIcon,
  CaretRightIcon,
  CopySimpleIcon,
  WarningCircleIcon,
  CalendarCheckIcon,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useToast } from '../../feedback/ToastProvider'
import { PageHeader } from '../../layout/PageHeader'
import { buscarPlano } from '../../planner/api/planoApi'
import { listarProjetos, type ProjetoResponse } from '../../projeto/api/projetoApi'
import {
  atualizarReserva,
  criarReserva,
  listarReservas,
  type ReservaProjetoResponse,
} from '../../projeto/api/reservaProjetoApi'
import {
  excluirCapacidadeOverride,
  listarCapacidadesOverride,
  restaurarCapacidadeOverride,
  type CapacidadeOverrideResponse,
} from '../api/capacidadeOverrideApi'
import {
  consultarCapacidadeResolvida,
  type CapacidadeResolvidaResponse,
} from '../api/capacidadeResolvidaApi'
import {
  atualizarCapacidadeSemanal,
  criarCapacidadeSemanal,
  listarCapacidadesSemanais,
  type CapacidadeSemanalResponse,
  type DiaSemana,
} from '../api/capacidadeSemanalApi'
import {
  adicionarDiasIso,
  datasDaSemana,
  formatarDataIso,
  formatarIntervaloSemana,
  inicioSemana,
  parseDataIsoLocal,
} from '../semana'

const DIAS: { valor: DiaSemana; label: string; curto: string }[] = [
  { valor: 'SEGUNDA', label: 'Segunda', curto: 'Seg' },
  { valor: 'TERCA', label: 'Terça', curto: 'Ter' },
  { valor: 'QUARTA', label: 'Quarta', curto: 'Qua' },
  { valor: 'QUINTA', label: 'Quinta', curto: 'Qui' },
  { valor: 'SEXTA', label: 'Sexta', curto: 'Sex' },
  { valor: 'SABADO', label: 'Sábado', curto: 'Sáb' },
  { valor: 'DOMINGO', label: 'Domingo', curto: 'Dom' },
]

const DIAS_UTEIS: DiaSemana[] = ['SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA']
const FIM_DE_SEMANA: DiaSemana[] = ['SABADO', 'DOMINGO']
const HORAS_MAX = 16
const HORAS_STEP = 1
const BARRA_ESCALA_MIN = 8
const MINUTOS_RESERVA_MIN = 15
const MINUTOS_RESERVA_STEP = 15

type RascunhoHoras = Record<DiaSemana, number>
type MinutosDraft = Record<string, number>

function mapaPorDia(
  lista: CapacidadeSemanalResponse[],
): Partial<Record<DiaSemana, CapacidadeSemanalResponse>> {
  const mapa: Partial<Record<DiaSemana, CapacidadeSemanalResponse>> = {}
  for (const item of lista) {
    if (item.ativo) {
      mapa[item.diaSemana] = item
    }
  }
  return mapa
}

function rascunhoDeCapacidades(lista: CapacidadeSemanalResponse[]): RascunhoHoras {
  const rascunho = {} as RascunhoHoras
  for (const { valor } of DIAS) {
    const item = lista.find((c) => c.diaSemana === valor && c.ativo)
    rascunho[valor] = item?.horas ?? 0
  }
  return rascunho
}

function mapaOverridePorData(
  lista: CapacidadeOverrideResponse[],
): Record<string, CapacidadeOverrideResponse> {
  const mapa: Record<string, CapacidadeOverrideResponse> = {}
  for (const item of lista) {
    if (item.ativo) {
      mapa[item.data] = item
    }
  }
  return mapa
}

function mapaResolvidaPorData(
  lista: CapacidadeResolvidaResponse[],
): Record<string, CapacidadeResolvidaResponse> {
  const mapa: Record<string, CapacidadeResolvidaResponse> = {}
  for (const item of lista) {
    mapa[item.data] = item
  }
  return mapa
}

function formatarDataBr(iso: string): string {
  return parseDataIsoLocal(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
  })
}

function totalHoras(rascunho: RascunhoHoras): number {
  return DIAS.reduce((acc, { valor }) => acc + rascunho[valor], 0)
}

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

function reservasDoDia(
  reservas: ReservaProjetoResponse[],
  dia: DiaSemana,
): ReservaProjetoResponse[] {
  return reservas.filter((r) => r.diaSemana === dia)
}

function somaMinutosReservados(reservas: ReservaProjetoResponse[]): number {
  return reservas.reduce((acc, r) => acc + r.minutosReservados, 0)
}

function chaveReserva(reservaId: string): string {
  return reservaId
}

function diasAlterados(
  rascunho: RascunhoHoras,
  porDia: Partial<Record<DiaSemana, CapacidadeSemanalResponse>>,
): DiaSemana[] {
  return DIAS.map(({ valor }) => valor).filter((dia) => {
    const salvo = porDia[dia]
    const horasSalvas = salvo?.horas ?? 0
    const temRegistro = salvo != null
    if (!temRegistro && rascunho[dia] === 0) {
      return false
    }
    if (!temRegistro) {
      return true
    }
    return rascunho[dia] !== horasSalvas
  })
}

function mesclarSemanaisSalvas(
  atuais: CapacidadeSemanalResponse[],
  respostas: CapacidadeSemanalResponse[],
): CapacidadeSemanalResponse[] {
  let proximo = [...atuais]
  for (const item of respostas) {
    proximo = proximo.filter((c) => !(c.diaSemana === item.diaSemana && c.ativo && c.id !== item.id))
    const idx = proximo.findIndex((c) => c.id === item.id)
    if (idx >= 0) {
      proximo[idx] = item
    } else {
      proximo.push(item)
    }
  }
  return proximo
}

function textoDiasAlterados(n: number): string {
  return n === 1 ? '1 dia alterado' : `${n} dias alterados`
}

export function CapacidadePage() {
  const toast = useToast()
  const [capacidades, setCapacidades] = useState<CapacidadeSemanalResponse[]>([])
  const [rascunho, setRascunho] = useState<RascunhoHoras>(() => rascunhoDeCapacidades([]))
  const [reservas, setReservas] = useState<ReservaProjetoResponse[]>([])
  const [projetosAtivos, setProjetosAtivos] = useState<ProjetoResponse[]>([])
  const [minutosDraft, setMinutosDraft] = useState<MinutosDraft>({})
  const [novoProjetoPorDia, setNovoProjetoPorDia] = useState<Partial<Record<DiaSemana, string>>>(
    {},
  )
  const [novosMinutosPorDia, setNovosMinutosPorDia] = useState<Partial<Record<DiaSemana, number>>>(
    {},
  )
  const [carregandoPrimeira, setCarregandoPrimeira] = useState(true)
  const [recarregandoSemana, setRecarregandoSemana] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [copiando, setCopiando] = useState(false)
  const [acaoOverrideId, setAcaoOverrideId] = useState<string | null>(null)
  const [salvandoReservaId, setSalvandoReservaId] = useState<string | null>(null)
  const [criandoReservaDia, setCriandoReservaDia] = useState<DiaSemana | null>(null)
  const [mostrarCtaProposta, setMostrarCtaProposta] = useState(false)

  const [semanaInicio, setSemanaInicio] = useState(() => inicioSemana())
  const [resolvidas, setResolvidas] = useState<CapacidadeResolvidaResponse[]>([])
  const [overrides, setOverrides] = useState<CapacidadeOverrideResponse[]>([])
  const cargaSemanaId = useRef(0)
  const primeiraCargaFeita = useRef(false)
  const tempReservaSeq = useRef(0)

  useEffect(() => {
    const id = ++cargaSemanaId.current
    const primeira = !primeiraCargaFeita.current
    let ativo = true

    async function carregar() {
      if (primeira) {
        setCarregandoPrimeira(true)
      } else {
        setRecarregandoSemana(true)
      }
      setErro(null)
      try {
        const dataAte = adicionarDiasIso(semanaInicio, 6)
        if (primeira) {
          const [semanais, resolvida, overridesLista, reservasLista, projetosPage] =
            await Promise.all([
              listarCapacidadesSemanais(),
              consultarCapacidadeResolvida({ dataDe: semanaInicio, dataAte }),
              listarCapacidadesOverride({
                dataDe: semanaInicio,
                dataAte,
              }),
              listarReservas(),
              listarProjetos({ status: 'ATIVO', page: 0, size: 100 }),
            ])
          if (!ativo || id !== cargaSemanaId.current) {
            return
          }
          setCapacidades(semanais)
          setRascunho(rascunhoDeCapacidades(semanais))
          setResolvidas(resolvida)
          setOverrides(overridesLista)
          setReservas(reservasLista)
          setProjetosAtivos(projetosPage.content)
          const drafts: MinutosDraft = {}
          for (const reserva of reservasLista) {
            drafts[chaveReserva(reserva.id)] = reserva.minutosReservados
          }
          setMinutosDraft(drafts)
          primeiraCargaFeita.current = true
        } else {
          const [resolvida, overridesLista] = await Promise.all([
            consultarCapacidadeResolvida({ dataDe: semanaInicio, dataAte }),
            listarCapacidadesOverride({
              dataDe: semanaInicio,
              dataAte,
            }),
          ])
          if (!ativo || id !== cargaSemanaId.current) {
            return
          }
          setResolvidas(resolvida)
          setOverrides(overridesLista)
        }
      } catch (error) {
        if (ativo && id === cargaSemanaId.current) {
          setErro(mensagemErro(error, 'Não foi possível carregar as capacidades.'))
          if (primeira) {
            setCapacidades([])
            setResolvidas([])
            setOverrides([])
            setReservas([])
            setProjetosAtivos([])
          }
        }
      } finally {
        if (ativo && id === cargaSemanaId.current) {
          setCarregandoPrimeira(false)
          setRecarregandoSemana(false)
        }
      }
    }

    void carregar()
    return () => {
      ativo = false
    }
  }, [semanaInicio])

  const porDia = mapaPorDia(capacidades)
  const porDataOverride = mapaOverridePorData(overrides)
  const porDataResolvida = mapaResolvidaPorData(resolvidas)
  const datasSemana = datasDaSemana(semanaInicio)
  const alterados = diasAlterados(rascunho, porDia)
  const sujo = alterados.length > 0
  const total = totalHoras(rascunho)
  const escalaBarra = Math.max(
    BARRA_ESCALA_MIN,
    ...DIAS.map(({ valor }) => rascunho[valor]),
    ...Object.values(porDataOverride).map((o) => o.horas),
  )

  useEffect(() => {
    if (!sujo) {
      return
    }
    function onBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload)
    }
  }, [sujo])

  function ajustarHoras(dia: DiaSemana, delta: number) {
    setRascunho((atual) => {
      const proximo = Math.min(HORAS_MAX, Math.max(0, atual[dia] + delta))
      return { ...atual, [dia]: proximo }
    })
  }

  function definirHoras(dia: DiaSemana, horas: number) {
    const proximo = Math.min(HORAS_MAX, Math.max(0, Math.round(horas)))
    setRascunho((atual) => ({ ...atual, [dia]: proximo }))
  }

  function aplicarPresetDiasUteis() {
    setRascunho((atual) => {
      const proximo = { ...atual }
      for (const dia of DIAS_UTEIS) {
        proximo[dia] = 4
      }
      for (const dia of FIM_DE_SEMANA) {
        proximo[dia] = 0
      }
      return proximo
    })
  }

  function handleDescartar() {
    setRascunho(rascunhoDeCapacidades(capacidades))
  }

  async function relerResolvidaSilencioso() {
    try {
      const dataAte = adicionarDiasIso(semanaInicio, 6)
      const resolvida = await consultarCapacidadeResolvida({
        dataDe: semanaInicio,
        dataAte,
      })
      setResolvidas(resolvida)
    } catch {
      // leitura silenciosa: toast já cobre a mutação
    }
  }

  async function handleCopiarSemanaAnterior() {
    setErro(null)
    setCopiando(true)
    try {
      const inicioAnterior = adicionarDiasIso(semanaInicio, -7)
      const fimAnterior = adicionarDiasIso(inicioAnterior, 6)
      const resolvidaAnterior = await consultarCapacidadeResolvida({
        dataDe: inicioAnterior,
        dataAte: fimAnterior,
      })
      const porData = mapaResolvidaPorData(resolvidaAnterior)
      const datas = datasDaSemana(inicioAnterior)
      setRascunho((atual) => {
        const proximo = { ...atual }
        for (const { valor } of DIAS) {
          const item = porData[datas[valor]]
          proximo[valor] = item?.horas ?? 0
        }
        return proximo
      })
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível copiar a semana anterior.'))
    } finally {
      setCopiando(false)
    }
  }

  async function handleSalvarSemana() {
    if (!sujo) {
      return
    }
    setErro(null)
    setSalvando(true)
    try {
      const respostas = await Promise.all(
        alterados.map(async (dia) => {
          const horas = rascunho[dia]
          const existente = porDia[dia]
          if (existente) {
            return [await atualizarCapacidadeSemanal(existente.id, { horas })]
          }
          return criarCapacidadeSemanal({ diaSemana: dia, horas })
        }),
      )
      const flat = respostas.flat()
      setCapacidades((atual) => mesclarSemanaisSalvas(atual, flat))
      toast.success('Capacidade salva')
      await relerResolvidaSilencioso()
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível salvar a capacidade.'))
    } finally {
      setSalvando(false)
    }
  }

  function resolvidaSemOverride(
    lista: CapacidadeResolvidaResponse[],
    data: string,
  ): CapacidadeResolvidaResponse[] {
    const dia = (Object.entries(datasSemana) as [DiaSemana, string][]).find(
      ([, iso]) => iso === data,
    )?.[0]
    const template = dia != null ? porDia[dia] : undefined
    return lista.map((item) => {
      if (item.data !== data) {
        return item
      }
      if (template != null) {
        return { data, horas: template.horas, origem: 'SEMANAL' as const }
      }
      return { data, horas: null, origem: 'VAZIO' as const }
    })
  }

  async function handleVoltarAoPadrao(override: CapacidadeOverrideResponse, label: string) {
    setErro(null)
    setAcaoOverrideId(override.id)
    const snapshotOverrides = overrides
    const snapshotResolvidas = resolvidas
    setOverrides((atual) => atual.filter((o) => o.id !== override.id))
    setResolvidas((atual) => resolvidaSemOverride(atual, override.data))
    try {
      await excluirCapacidadeOverride(override.id)
      toast.success('Exceção removida', {
        action: {
          label: 'Desfazer',
          onClick: () => {
            void restaurarCapacidadeOverride(override.id)
              .then((restaurado) => {
                setOverrides((atual) => {
                  if (atual.some((o) => o.id === restaurado.id)) {
                    return atual
                  }
                  return [...atual, restaurado]
                })
                setResolvidas((atual) =>
                  atual.map((item) =>
                    item.data === restaurado.data
                      ? {
                          data: restaurado.data,
                          horas: restaurado.horas,
                          origem: 'OVERRIDE' as const,
                        }
                      : item,
                  ),
                )
                void relerResolvidaSilencioso()
              })
              .catch((error) => {
                setErro(
                  mensagemErro(error, `Não foi possível restaurar a exceção de ${label}.`),
                )
              })
          },
        },
      })
      await relerResolvidaSilencioso()
    } catch (error) {
      setOverrides(snapshotOverrides)
      setResolvidas(snapshotResolvidas)
      setErro(mensagemErro(error, `Não foi possível remover a exceção de ${label}.`))
    } finally {
      setAcaoOverrideId(null)
    }
  }

  async function verificarPropostaPendente() {
    try {
      const plano = await buscarPlano({ data: formatarDataIso(new Date()) })
      setMostrarCtaProposta(Boolean(plano?.propostaPendente && plano.propostaId))
    } catch {
      setMostrarCtaProposta(false)
    }
  }

  function nomeProjeto(projetoId: string): string {
    return projetosAtivos.find((p) => p.id === projetoId)?.nome ?? 'Projeto'
  }

  async function handleSalvarReserva(reserva: ReservaProjetoResponse) {
    const minutos = minutosDraft[chaveReserva(reserva.id)] ?? reserva.minutosReservados
    if (minutos < MINUTOS_RESERVA_MIN || minutos % MINUTOS_RESERVA_STEP !== 0) {
      setErro(`A reserva deve ser múltiplo de ${MINUTOS_RESERVA_STEP} minutos (mín. ${MINUTOS_RESERVA_MIN}).`)
      return
    }
    setErro(null)
    const snapshot = reserva
    const otimista = { ...reserva, minutosReservados: minutos }
    setSalvandoReservaId(reserva.id)
    setReservas((atual) => atual.map((r) => (r.id === reserva.id ? otimista : r)))
    setMinutosDraft((atual) => ({ ...atual, [chaveReserva(reserva.id)]: minutos }))
    try {
      const atualizada = await atualizarReserva(reserva.projetoId, reserva.id, {
        minutosReservados: minutos,
      })
      setReservas((atual) => atual.map((r) => (r.id === reserva.id ? atualizada : r)))
      setMinutosDraft((atual) => ({
        ...atual,
        [chaveReserva(reserva.id)]: atualizada.minutosReservados,
      }))
      toast.success('Reserva atualizada')
      await relerResolvidaSilencioso()
      await verificarPropostaPendente()
    } catch (error) {
      setReservas((atual) => atual.map((r) => (r.id === reserva.id ? snapshot : r)))
      setMinutosDraft((atual) => ({
        ...atual,
        [chaveReserva(reserva.id)]: snapshot.minutosReservados,
      }))
      const mensagem = mensagemErro(error, 'Não foi possível atualizar a reserva.')
      setErro(mensagem)
      toast.error(mensagem)
    } finally {
      setSalvandoReservaId(null)
    }
  }

  async function handleCriarReserva(dia: DiaSemana) {
    const projetoId = novoProjetoPorDia[dia]
    const minutos = novosMinutosPorDia[dia] ?? 60
    if (!projetoId) {
      setErro('Selecione um projeto para reservar.')
      return
    }
    if (minutos < MINUTOS_RESERVA_MIN || minutos % MINUTOS_RESERVA_STEP !== 0) {
      setErro(`A reserva deve ser múltiplo de ${MINUTOS_RESERVA_STEP} minutos (mín. ${MINUTOS_RESERVA_MIN}).`)
      return
    }
    setErro(null)
    tempReservaSeq.current += 1
    const tempId = `temp-${dia}-${tempReservaSeq.current}`
    const otimista: ReservaProjetoResponse = {
      id: tempId,
      projetoId,
      diaSemana: dia,
      minutosReservados: minutos,
      createdAt: '',
      updatedAt: '',
    }
    setCriandoReservaDia(dia)
    setReservas((atual) => [...atual, otimista])
    setMinutosDraft((atual) => ({ ...atual, [chaveReserva(tempId)]: minutos }))
    setNovoProjetoPorDia((atual) => ({ ...atual, [dia]: '' }))
    setNovosMinutosPorDia((atual) => ({ ...atual, [dia]: 60 }))
    try {
      const criada = await criarReserva(projetoId, { diaSemana: dia, minutosReservados: minutos })
      setReservas((atual) => atual.map((r) => (r.id === tempId ? criada : r)))
      setMinutosDraft((atual) => {
        const proximo = { ...atual }
        delete proximo[chaveReserva(tempId)]
        proximo[chaveReserva(criada.id)] = criada.minutosReservados
        return proximo
      })
      toast.success('Reserva criada')
      await relerResolvidaSilencioso()
      await verificarPropostaPendente()
    } catch (error) {
      setReservas((atual) => atual.filter((r) => r.id !== tempId))
      setMinutosDraft((atual) => {
        const proximo = { ...atual }
        delete proximo[chaveReserva(tempId)]
        return proximo
      })
      setNovoProjetoPorDia((atual) => ({ ...atual, [dia]: projetoId }))
      setNovosMinutosPorDia((atual) => ({ ...atual, [dia]: minutos }))
      const mensagem = mensagemErro(error, 'Não foi possível criar a reserva.')
      setErro(mensagem)
      toast.error(mensagem)
    } finally {
      setCriandoReservaDia(null)
    }
  }

  function onHorasKeyDown(dia: DiaSemana, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      ajustarHoras(dia, HORAS_STEP)
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      ajustarHoras(dia, -HORAS_STEP)
    }
  }

  const mapaNomeProjeto = Object.fromEntries(projetosAtivos.map((p) => [p.id, p.nome]))
  const gradeVisivel = !carregandoPrimeira

  return (
    <main className={sujo ? 'capacidade-page capacidade-page--sujo' : 'capacidade-page'}>
      <PageHeader
        title="Capacidade"
        description={
          <>
            Defina quantas horas você tem disponíveis em cada dia da semana.
            <span className="capacidade-total" aria-live="polite">
              {' '}
              · {total}h disponíveis
            </span>
          </>
        }
        actions={
          <div className="capacidade-presets">
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={aplicarPresetDiasUteis}
              disabled={carregandoPrimeira || salvando}
            >
              <CalendarCheckIcon aria-hidden />
              Dias úteis 4h, fim de semana 0h
            </button>
            <button
              type="button"
              className="btn btn--secondary btn--sm"
              onClick={() => void handleCopiarSemanaAnterior()}
              disabled={carregandoPrimeira || salvando || copiando}
              aria-busy={copiando}
            >
              <CopySimpleIcon aria-hidden />
              {copiando ? 'Copiando…' : 'Copiar semana anterior'}
            </button>
          </div>
        }
      />

      <p className="capacidade-hint">
        Tempo reservado a um projeto fica separado no dia; o restante segue a prioridade geral.
      </p>

      {mostrarCtaProposta ? (
        <div className="capacidade-proposta-cta" role="status">
          <div className="capacidade-proposta-cta__corpo">
            <WarningCircleIcon className="capacidade-proposta-cta__icone" aria-hidden />
            <p>Há uma proposta de plano pendente após a mudança de reserva.</p>
          </div>
          <Link to="/" className="btn btn--secondary btn--sm">
            Revisar proposta
          </Link>
        </div>
      ) : null}

      <div className="capacidade-semana-nav" aria-label="Semana selecionada">
        <button
          type="button"
          className="btn btn--ghost btn--icon btn--touch"
          aria-label="Semana anterior"
          onClick={() => setSemanaInicio((atual) => adicionarDiasIso(atual, -7))}
        >
          <CaretLeftIcon aria-hidden />
        </button>
        <p className="capacidade-semana-label">{formatarIntervaloSemana(semanaInicio)}</p>
        <button
          type="button"
          className="btn btn--ghost btn--icon btn--touch"
          aria-label="Semana seguinte"
          onClick={() => setSemanaInicio((atual) => adicionarDiasIso(atual, 7))}
        >
          <CaretRightIcon aria-hidden />
        </button>
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregandoPrimeira ? <Skeleton variant="capacidade-grade" /> : null}

      {gradeVisivel ? (
        <div
          className={
            recarregandoSemana
              ? 'capacidade-grade capacidade-grade--recarregando'
              : 'capacidade-grade'
          }
          role="list"
          aria-busy={recarregandoSemana || undefined}
        >
          {DIAS.map(({ valor, label, curto }) => {
            const horas = rascunho[valor]
            const dataDia = datasSemana[valor]
            const override = porDataOverride[dataDia]
            const resolvida = porDataResolvida[dataDia]
            const comOverride = override != null
            const horasBarra =
              override?.horas ??
              (resolvida?.origem === 'OVERRIDE' && resolvida.horas != null
                ? resolvida.horas
                : horas)
            const alturaPct = Math.round((horasBarra / escalaBarra) * 100)
            const labelLower = label.toLowerCase()
            const ocupadoOverride = override != null && acaoOverrideId === override.id
            const badgeOrigem = comOverride ? 'Exceção' : 'Padrão'

            return (
              <article
                key={valor}
                className={[
                  'capacidade-celula',
                  comOverride ? 'capacidade-celula-override' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                role="listitem"
              >
                <div className="capacidade-celula-topo">
                  <h2>
                    <span className="capacidade-dia-curto">{curto}</span>
                    <span className="capacidade-dia-longo">{label}</span>
                  </h2>
                  <span
                    className={
                      comOverride ? 'badge badge-excecao' : 'badge badge-padrao'
                    }
                  >
                    {badgeOrigem}
                  </span>
                </div>

                <p className="capacidade-data-celula">{formatarDataBr(dataDia)}</p>

                <div className="capacidade-barra-wrap" aria-hidden="true">
                  <div
                    className="capacidade-barra"
                    style={{ height: `${Math.max(alturaPct, horasBarra > 0 ? 8 : 0)}%` }}
                  />
                </div>

                <p className="capacidade-horas" aria-hidden="true">
                  {horas}h
                </p>

                {comOverride && override ? (
                  <p className="capacidade-efetivas">Efetivas: {override.horas}h</p>
                ) : null}

                <div className="capacidade-stepper">
                  <button
                    type="button"
                    className="btn btn--ghost btn--touch"
                    aria-label={`Diminuir horas de ${labelLower}`}
                    disabled={horas <= 0 || salvando}
                    onClick={() => ajustarHoras(valor, -HORAS_STEP)}
                  >
                    −
                  </button>
                  <div className="capacidade-horas-input">
                    <input
                      type="number"
                      min={0}
                      max={HORAS_MAX}
                      step={HORAS_STEP}
                      value={horas}
                      disabled={salvando}
                      aria-label={`Horas de ${labelLower}`}
                      onChange={(e) => {
                        const n = Number(e.target.value)
                        definirHoras(valor, Number.isFinite(n) ? n : 0)
                      }}
                      onKeyDown={(e) => onHorasKeyDown(valor, e)}
                    />
                  </div>
                  <button
                    type="button"
                    className="btn btn--ghost btn--touch"
                    aria-label={`Aumentar horas de ${labelLower}`}
                    disabled={horas >= HORAS_MAX || salvando}
                    onClick={() => ajustarHoras(valor, HORAS_STEP)}
                  >
                    +
                  </button>
                </div>

                {override ? (
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    disabled={ocupadoOverride || salvando}
                    aria-busy={ocupadoOverride}
                    onClick={() => void handleVoltarAoPadrao(override, labelLower)}
                  >
                    {ocupadoOverride ? 'Removendo…' : `Voltar ao padrão (${labelLower})`}
                  </button>
                ) : null}

                <section
                  className="capacidade-reservas"
                  aria-label={`Reservas por projeto — ${labelLower}`}
                >
                  <h3 className="capacidade-reservas__titulo">Reservas por projeto</h3>
                  {(() => {
                    const doDia = reservasDoDia(reservas, valor)
                    const soma = somaMinutosReservados(doDia)
                    const capacidadeMin = horas * 60
                    const projetosOcupados = new Set(doDia.map((r) => r.projetoId))
                    const disponiveis = projetosAtivos.filter((p) => !projetosOcupados.has(p.id))
                    return (
                      <>
                        <p
                          className={
                            soma > capacidadeMin
                              ? 'capacidade-reservas__total capacidade-reservas__total--excede'
                              : 'capacidade-reservas__total'
                          }
                        >
                          {soma} / {capacidadeMin} min
                        </p>
                        <ul className="capacidade-reservas__lista">
                          {doDia.map((reserva) => {
                            const nome =
                              mapaNomeProjeto[reserva.projetoId] ?? nomeProjeto(reserva.projetoId)
                            const draft =
                              minutosDraft[chaveReserva(reserva.id)] ?? reserva.minutosReservados
                            const ocupado = salvandoReservaId === reserva.id
                            return (
                              <li
                                key={reserva.id}
                                className={
                                  ocupado
                                    ? 'capacidade-reservas__item capacidade-reservas__item--pendente'
                                    : 'capacidade-reservas__item'
                                }
                                aria-busy={ocupado || undefined}
                              >
                                <span className="capacidade-reservas__nome">{nome}</span>
                                <label className="capacidade-reservas__campo">
                                  <span className="visually-hidden">
                                    Minutos reservados para {nome}
                                  </span>
                                  <input
                                    type="number"
                                    min={MINUTOS_RESERVA_MIN}
                                    step={MINUTOS_RESERVA_STEP}
                                    value={draft}
                                    disabled={ocupado || salvando}
                                    onChange={(e) => {
                                      const n = Number(e.target.value)
                                      setMinutosDraft((atual) => ({
                                        ...atual,
                                        [chaveReserva(reserva.id)]: Number.isFinite(n) ? n : 0,
                                      }))
                                    }}
                                    aria-label={`Minutos reservados para ${nome}`}
                                  />
                                  <span aria-hidden="true">min</span>
                                </label>
                                <button
                                  type="button"
                                  className="btn btn--ghost btn--sm"
                                  disabled={ocupado || salvando}
                                  aria-busy={ocupado}
                                  aria-label={`Salvar reserva de ${nome}`}
                                  onClick={() => void handleSalvarReserva(reserva)}
                                >
                                  {ocupado ? '…' : 'Salvar'}
                                </button>
                              </li>
                            )
                          })}
                        </ul>
                        {disponiveis.length > 0 ? (
                          <div className="capacidade-reservas__nova">
                            <label className="capacidade-reservas__campo">
                              <span className="visually-hidden">Projeto para {labelLower}</span>
                              <select
                                value={novoProjetoPorDia[valor] ?? ''}
                                disabled={criandoReservaDia === valor || salvando}
                                onChange={(e) =>
                                  setNovoProjetoPorDia((atual) => ({
                                    ...atual,
                                    [valor]: e.target.value,
                                  }))
                                }
                                aria-label={`Projeto para reservar em ${labelLower}`}
                              >
                                <option value="">Projeto…</option>
                                {disponiveis.map((projeto) => (
                                  <option key={projeto.id} value={projeto.id}>
                                    {projeto.nome}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="capacidade-reservas__campo">
                              <span className="visually-hidden">Minutos novos em {labelLower}</span>
                              <input
                                type="number"
                                min={MINUTOS_RESERVA_MIN}
                                step={MINUTOS_RESERVA_STEP}
                                value={novosMinutosPorDia[valor] ?? 60}
                                disabled={criandoReservaDia === valor || salvando}
                                onChange={(e) => {
                                  const n = Number(e.target.value)
                                  setNovosMinutosPorDia((atual) => ({
                                    ...atual,
                                    [valor]: Number.isFinite(n) ? n : 60,
                                  }))
                                }}
                                aria-label={`Minutos da nova reserva em ${labelLower}`}
                              />
                            </label>
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              disabled={
                                !novoProjetoPorDia[valor] ||
                                criandoReservaDia === valor ||
                                salvando
                              }
                              aria-busy={criandoReservaDia === valor}
                              onClick={() => void handleCriarReserva(valor)}
                            >
                              {criandoReservaDia === valor ? '…' : 'Add'}
                            </button>
                          </div>
                        ) : null}
                      </>
                    )
                  })()}
                </section>
              </article>
            )
          })}
        </div>
      ) : null}

      {sujo ? (
        <div className="capacidade-salvar-barra" role="status" aria-live="polite">
          <p className="capacidade-salvar-barra__texto">{textoDiasAlterados(alterados.length)}</p>
          <div className="capacidade-salvar-barra__acoes">
            <button
              type="button"
              className="btn btn--ghost"
              onClick={handleDescartar}
              disabled={salvando}
            >
              Descartar
            </button>
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => void handleSalvarSemana()}
              disabled={salvando}
              aria-busy={salvando}
            >
              {salvando ? 'Salvando…' : 'Salvar semana'}
            </button>
          </div>
        </div>
      ) : null}
    </main>
  )
}
