import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useToast } from '../../feedback/ToastProvider'
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
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [copiando, setCopiando] = useState(false)
  const [acaoOverrideId, setAcaoOverrideId] = useState<string | null>(null)
  const [salvandoReservaId, setSalvandoReservaId] = useState<string | null>(null)
  const [criandoReservaDia, setCriandoReservaDia] = useState<DiaSemana | null>(null)
  const [mostrarCtaProposta, setMostrarCtaProposta] = useState(false)
  const [recarregarEm, setRecarregarEm] = useState(0)

  const [semanaInicio, setSemanaInicio] = useState(() => inicioSemana())
  const [resolvidas, setResolvidas] = useState<CapacidadeResolvidaResponse[]>([])
  const [overrides, setOverrides] = useState<CapacidadeOverrideResponse[]>([])

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const dataAte = adicionarDiasIso(semanaInicio, 6)
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
        if (ativo) {
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
        }
      } catch (error) {
        if (ativo) {
          setErro(mensagemErro(error, 'Não foi possível carregar as capacidades.'))
          setCapacidades([])
          setResolvidas([])
          setOverrides([])
          setReservas([])
          setProjetosAtivos([])
        }
      } finally {
        if (ativo) {
          setCarregando(false)
        }
      }
    }

    void carregar()
    return () => {
      ativo = false
    }
  }, [recarregarEm, semanaInicio])

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

  function ajustarHoras(dia: DiaSemana, delta: number) {
    setRascunho((atual) => {
      const proximo = Math.min(HORAS_MAX, Math.max(0, atual[dia] + delta))
      return { ...atual, [dia]: proximo }
    })
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
      const operacoes = alterados.map(async (dia) => {
        const horas = rascunho[dia]
        const existente = porDia[dia]
        if (existente) {
          await atualizarCapacidadeSemanal(existente.id, { horas })
        } else {
          await criarCapacidadeSemanal({ diaSemana: dia, horas })
        }
      })
      await Promise.all(operacoes)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível salvar a capacidade.'))
    } finally {
      setSalvando(false)
    }
  }

  async function handleVoltarAoPadrao(override: CapacidadeOverrideResponse, label: string) {
    setErro(null)
    setAcaoOverrideId(override.id)
    try {
      await excluirCapacidadeOverride(override.id)
      setRecarregarEm((n) => n + 1)
      toast.success('Exceção removida', {
        action: {
          label: 'Desfazer',
          onClick: () => {
            void restaurarCapacidadeOverride(override.id)
              .then(() => setRecarregarEm((n) => n + 1))
              .catch((error) => {
                setErro(
                  mensagemErro(error, `Não foi possível restaurar a exceção de ${label}.`),
                )
              })
          },
        },
      })
    } catch (error) {
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
    setSalvandoReservaId(reserva.id)
    try {
      await atualizarReserva(reserva.projetoId, reserva.id, { minutosReservados: minutos })
      setRecarregarEm((n) => n + 1)
      toast.success('Reserva atualizada')
      await verificarPropostaPendente()
    } catch (error) {
      const mensagem = mensagemErro(error, 'Não foi possível atualizar a reserva.')
      setErro(mensagem)
      if (error instanceof ApiError && error.status === 409) {
        toast.error(mensagem)
      }
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
    setCriandoReservaDia(dia)
    try {
      await criarReserva(projetoId, { diaSemana: dia, minutosReservados: minutos })
      setNovoProjetoPorDia((atual) => ({ ...atual, [dia]: '' }))
      setNovosMinutosPorDia((atual) => ({ ...atual, [dia]: 60 }))
      setRecarregarEm((n) => n + 1)
      toast.success('Reserva criada')
      await verificarPropostaPendente()
    } catch (error) {
      const mensagem = mensagemErro(error, 'Não foi possível criar a reserva.')
      setErro(mensagem)
      if (error instanceof ApiError && error.status === 409) {
        toast.error(mensagem)
      }
    } finally {
      setCriandoReservaDia(null)
    }
  }

  const mapaNomeProjeto = Object.fromEntries(projetosAtivos.map((p) => [p.id, p.nome]))

  return (
    <main className="capacidade-page">
      <header className="capacidade-header">
        <div>
          <h1>Capacidade</h1>
          <p className="capacidade-subtitle">
            Defina quantas horas você tem disponíveis em cada dia da semana.
          </p>
          <p className="capacidade-hint">
            Tempo reservado a um projeto fica separado no dia; o restante segue a prioridade
            geral.
          </p>
        </div>
        <p className="capacidade-total" aria-live="polite">
          {total}h disponíveis
        </p>
      </header>

      {mostrarCtaProposta ? (
        <div className="capacidade-proposta-cta" role="status">
          <p>Há uma proposta de plano pendente após a mudança de reserva.</p>
          <Link to="/" className="btn btn--secondary btn--sm">
            Revisar proposta
          </Link>
        </div>
      ) : null}

      <div className="capacidade-semana-nav" aria-label="Semana selecionada">
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => setSemanaInicio((atual) => adicionarDiasIso(atual, -7))}
        >
          Semana anterior
        </button>
        <p className="capacidade-semana-label">
          Semana {formatarIntervaloSemana(semanaInicio)}
        </p>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          onClick={() => setSemanaInicio((atual) => adicionarDiasIso(atual, 7))}
        >
          Semana seguinte
        </button>
      </div>

      <div className="capacidade-toolbar">
        <div className="capacidade-presets">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={aplicarPresetDiasUteis}
            disabled={carregando || salvando}
          >
            Dias úteis 4h, fim de semana 0h
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => void handleCopiarSemanaAnterior()}
            disabled={carregando || salvando || copiando}
            aria-busy={copiando}
          >
            {copiando ? 'Copiando…' : 'Copiar semana anterior'}
          </button>
        </div>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => void handleSalvarSemana()}
          disabled={!sujo || carregando || salvando}
          aria-busy={salvando}
        >
          {salvando ? 'Salvando…' : 'Salvar semana'}
        </button>
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {carregando ? <Skeleton variant="capacidade-grade" /> : null}

      {!carregando ? (
        <div className="capacidade-grade" role="list">
          {DIAS.map(({ valor, label, curto }) => {
            const horas = rascunho[valor]
            const dataDia = datasSemana[valor]
            const override = porDataOverride[dataDia]
            const resolvida = porDataResolvida[dataDia]
            const comOverride = override != null || resolvida?.origem === 'OVERRIDE'
            const horasBarra = override?.horas ?? horas
            const alturaPct = Math.round((horasBarra / escalaBarra) * 100)
            const labelLower = label.toLowerCase()
            const ocupadoOverride = override != null && acaoOverrideId === override.id

            return (
              <article
                key={valor}
                className={[
                  'capacidade-celula',
                  'card',
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
                  {comOverride ? <span className="badge badge-excecao">Exceção</span> : null}
                </div>

                <p className="capacidade-data-celula">{formatarDataBr(dataDia)}</p>

                <div className="capacidade-barra-wrap" aria-hidden="true">
                  <div
                    className="capacidade-barra"
                    style={{ height: `${Math.max(alturaPct, horasBarra > 0 ? 8 : 0)}%` }}
                  />
                </div>

                <p
                  className="capacidade-horas"
                  aria-label={`Horas de ${labelLower}`}
                >
                  {horas}h
                </p>

                {comOverride && override ? (
                  <p className="capacidade-efetivas">Efetivas: {override.horas}h</p>
                ) : null}

                <div className="capacidade-stepper">
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    aria-label={`Diminuir ${labelLower}`}
                    disabled={horas <= 0 || salvando}
                    onClick={() => ajustarHoras(valor, -1)}
                  >
                    −
                  </button>
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    aria-label={`Aumentar ${labelLower}`}
                    disabled={horas >= HORAS_MAX || salvando}
                    onClick={() => ajustarHoras(valor, 1)}
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
                            const nome = mapaNomeProjeto[reserva.projetoId] ?? nomeProjeto(reserva.projetoId)
                            const draft =
                              minutosDraft[chaveReserva(reserva.id)] ?? reserva.minutosReservados
                            const ocupado = salvandoReservaId === reserva.id
                            return (
                              <li key={reserva.id} className="capacidade-reservas__item">
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
    </main>
  )
}
