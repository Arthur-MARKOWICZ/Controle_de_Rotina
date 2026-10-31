import { FlagIcon, WarningCircleIcon } from '@phosphor-icons/react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { formatarDataIso } from '../../capacidade/semana'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { CapacidadeBar } from '../../home/CapacidadeBar'
import { labelPrioridade, labelStatusPlano } from '../../home/labels'
import { PageHeader } from '../../layout/PageHeader'
import { PeriodoNav } from '../../layout/PeriodoNav'
import { formatarCapacidadeMinutos } from '../../planner/data'
import type { TarefaDrawerState } from '../../tarefa/TarefaDrawer'
import type { Prioridade } from '../../tarefa/validation/tarefaForm'
import {
  consultarCalendario,
  type CalendarioDia,
} from '../api/calendarioApi'
import {
  celulasGradeMes,
  diasDaSemanaIso,
  intervaloPorVisao,
  labelDiaCurto,
  labelPeriodo,
  navegarPeriodo,
  parseLente,
  parseVisao,
  type LenteCalendario,
  type VisaoCalendario,
} from '../periodo'

const VISOES: { valor: VisaoCalendario; label: string }[] = [
  { valor: 'semana', label: 'Semana' },
  { valor: 'mes', label: 'Mês' },
]

const LENTES: { valor: LenteCalendario; label: string }[] = [
  { valor: 'plano', label: 'Plano' },
  { valor: 'prazos', label: 'Prazos' },
]

const DRAWER_STATE: TarefaDrawerState = { daLista: true }

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

function diaPorData(dias: CalendarioDia[], data: string): CalendarioDia | undefined {
  return dias.find((d) => d.data === data)
}

function numeroDoDia(iso: string): string {
  return iso.slice(8, 10).replace(/^0/, '')
}

function formatarDataPainel(iso: string): string {
  const [ano, mes, dia] = iso.split('-').map(Number)
  const raw = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(ano, mes - 1, dia, 12, 0, 0))
  return raw.charAt(0).toUpperCase() + raw.slice(1)
}

function classeCelula(base: string, opcoes: { selecionado: boolean; sobrecarga: boolean; hoje: boolean }) {
  return [
    base,
    opcoes.hoje ? `${base}--hoje` : '',
    opcoes.selecionado ? `${base}--selecionada` : opcoes.sobrecarga ? `${base}--sobrecarga` : '',
  ]
    .filter(Boolean)
    .join(' ')
}

export function CalendarioPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const visao = parseVisao(searchParams.get('visao'))
  const lente = parseLente(searchParams.get('lente'))
  const hoje = formatarDataIso(new Date())
  const dataRef = searchParams.get('dataDe') ?? hoje

  const [dias, setDias] = useState<CalendarioDia[]>([])
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [jaCarregou, setJaCarregou] = useState(false)
  const [diaSelecionado, setDiaSelecionado] = useState<string | null>(null)

  const intervalo = useMemo(() => intervaloPorVisao(visao, dataRef), [visao, dataRef])

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro(null)
    consultarCalendario(intervalo.dataDe, intervalo.dataAte)
      .then((resposta) => {
        if (!ativo) {
          return
        }
        setDias(resposta.dias)
      })
      .catch((error) => {
        if (!ativo) {
          return
        }
        setErro(mensagemErro(error, 'Não foi possível carregar o calendário.'))
        setDias([])
      })
      .finally(() => {
        if (ativo) {
          setCarregando(false)
          setJaCarregou(true)
        }
      })
    return () => {
      ativo = false
    }
  }, [intervalo.dataDe, intervalo.dataAte])

  function atualizarQuery(parcial: {
    visao?: VisaoCalendario
    lente?: LenteCalendario
    dataDe?: string
  }) {
    const next = new URLSearchParams(searchParams)
    next.set('visao', parcial.visao ?? visao)
    next.set('lente', parcial.lente ?? lente)
    next.set('dataDe', parcial.dataDe ?? dataRef)
    setSearchParams(next, { replace: true })
  }

  function mudarVisao(proxima: VisaoCalendario) {
    atualizarQuery({ visao: proxima, dataDe: intervaloPorVisao(proxima, dataRef).dataDe })
  }

  const diaDetalhe = diaSelecionado ? diaPorData(dias, diaSelecionado) : undefined
  const diasSemana = visao === 'semana' ? diasDaSemanaIso(intervalo.dataDe) : []
  const recarregando = carregando && jaCarregou

  return (
    <main className="calendario-page">
      <PageHeader
        title="Calendário"
        description="Plano aprovado e prazos no horizonte"
        actions={
          <PeriodoNav
            opcoes={VISOES}
            visao={visao}
            onVisaoChange={mudarVisao}
            rotulo={labelPeriodo(visao, dataRef)}
            onAnterior={() => atualizarQuery({ dataDe: navegarPeriodo(visao, dataRef, -1) })}
            onProximo={() => atualizarQuery({ dataDe: navegarPeriodo(visao, dataRef, 1) })}
            onHoje={() => atualizarQuery({ dataDe: intervaloPorVisao(visao, hoje).dataDe })}
          />
        }
      />

      <div className="segmented calendario-lente" role="radiogroup" aria-label="Lente">
        {LENTES.map((opcao) => (
          <button
            key={opcao.valor}
            type="button"
            role="radio"
            aria-checked={lente === opcao.valor}
            className={
              lente === opcao.valor ? 'segmented__item segmented__item--ativo' : 'segmented__item'
            }
            onClick={() => atualizarQuery({ lente: opcao.valor })}
          >
            {opcao.label}
          </button>
        ))}
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {!jaCarregou ? (
        <Skeleton variant="calendario-grade" />
      ) : visao === 'semana' ? (
        <section
          className={
            recarregando ? 'calendario-semana calendario--recarregando' : 'calendario-semana'
          }
          aria-label="Grade da semana"
          aria-busy={recarregando || undefined}
        >
          <div className="calendario-semana__cabecalho" aria-hidden="true">
            {DIAS_CABECALHO.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
          <div className="calendario-semana__grade">
            {diasSemana.map((data, indice) => (
              <CelulaSemana
                key={data}
                data={data}
                indice={indice}
                dia={diaPorData(dias, data)}
                lente={lente}
                hoje={data === hoje}
                selecionado={diaSelecionado === data}
                onSelecionar={() => setDiaSelecionado(data)}
              />
            ))}
          </div>
        </section>
      ) : (
        <section
          className={recarregando ? 'calendario-mes calendario--recarregando' : 'calendario-mes'}
          aria-label="Grade do mês"
          aria-busy={recarregando || undefined}
        >
          <div className="calendario-mes__cabecalho" aria-hidden="true">
            {DIAS_CABECALHO.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
          <div className="calendario-mes__grade">
            {celulasGradeMes(dataRef).map((celula, i) => {
              if (celula.foraDoMes || !celula.data) {
                return (
                  <div
                    key={`pad-${i}`}
                    className="calendario-mes__celula calendario-mes__celula--fora"
                    aria-hidden="true"
                  />
                )
              }
              return (
                <CelulaMes
                  key={celula.data}
                  data={celula.data}
                  dia={diaPorData(dias, celula.data)}
                  hoje={celula.data === hoje}
                  selecionado={diaSelecionado === celula.data}
                  onSelecionar={() => setDiaSelecionado(celula.data)}
                />
              )
            })}
          </div>
        </section>
      )}

      {diaSelecionado && diaDetalhe ? (
        <PainelDia
          data={diaSelecionado}
          dia={diaDetalhe}
          lente={lente}
          onFechar={() => setDiaSelecionado(null)}
        />
      ) : null}
    </main>
  )
}

const DIAS_CABECALHO = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

function PilulaTarefa({
  tarefaId,
  titulo,
  prioridade,
  duracaoMinutos,
  prazo = false,
}: {
  tarefaId: string
  titulo: string
  prioridade: Prioridade
  duracaoMinutos?: number
  prazo?: boolean
}) {
  return (
    <Link
      to={`/tarefas/${tarefaId}`}
      state={DRAWER_STATE}
      className={`calendario-pilula calendario-pilula--${prioridade.toLowerCase()}`}
      title={titulo}
    >
      {prazo ? (
        <FlagIcon className="calendario-pilula__icone" aria-hidden weight="fill" />
      ) : (
        <span className="calendario-pilula__ponto" aria-hidden="true" />
      )}
      <span className="calendario-pilula__titulo">{titulo}</span>
      {duracaoMinutos != null ? (
        <span className="calendario-pilula__duracao">{formatarCapacidadeMinutos(duracaoMinutos)}</span>
      ) : null}
      <span className="visually-hidden">
        {prazo ? ', prazo' : ''}, prioridade {labelPrioridade(prioridade).toLowerCase()}
      </span>
    </Link>
  )
}

function CelulaSemana({
  data,
  indice,
  dia,
  lente,
  hoje,
  selecionado,
  onSelecionar,
}: {
  data: string
  indice: number
  dia: CalendarioDia | undefined
  lente: LenteCalendario
  hoje: boolean
  selecionado: boolean
  onSelecionar: () => void
}) {
  const sobrecarga = dia?.sobrecarga === true
  const chipAprovado = dia?.statusPlano === 'APROVADO'
  const blocos = dia?.plano?.blocos ?? []
  const prazos = dia?.tarefasComPrazo ?? []

  return (
    <div
      className={classeCelula('calendario-semana__celula', { selecionado, sobrecarga, hoje })}
      aria-current={hoje ? 'date' : undefined}
    >
      <div className="calendario-semana__celula-topo">
        <button
          type="button"
          className="calendario-semana__dia"
          aria-label={`${labelDiaCurto(indice)} ${numeroDoDia(data)}`}
          aria-pressed={selecionado}
          onClick={onSelecionar}
        >
          <span className="calendario-semana__dia-semana" aria-hidden="true">
            {labelDiaCurto(indice)}
          </span>
          <span className="calendario-semana__dia-num">{numeroDoDia(data)}</span>
        </button>
        {chipAprovado ? (
          <span className="badge status-plano-aprovado">{labelStatusPlano('APROVADO')}</span>
        ) : null}
      </div>
      {sobrecarga ? (
        <span className="calendario-indicador calendario-indicador--sobrecarga" aria-label="Sobrecarga">
          <WarningCircleIcon aria-hidden weight="fill" />
          Sobrecarga
        </span>
      ) : null}
      {lente === 'plano' ? (
        <ul className="calendario-semana__lista">
          {blocos.map((bloco) => (
            <li key={bloco.id}>
              <PilulaTarefa
                tarefaId={bloco.tarefaId}
                titulo={bloco.titulo}
                prioridade={bloco.prioridade}
                duracaoMinutos={bloco.duracaoMinutos}
              />
            </li>
          ))}
        </ul>
      ) : (
        <ul className="calendario-semana__lista">
          {prazos.map((tarefa) => (
            <li key={tarefa.id}>
              <PilulaTarefa
                tarefaId={tarefa.id}
                titulo={tarefa.titulo}
                prioridade={tarefa.prioridade}
                prazo
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function CelulaMes({
  data,
  dia,
  hoje,
  selecionado,
  onSelecionar,
}: {
  data: string
  dia: CalendarioDia | undefined
  hoje: boolean
  selecionado: boolean
  onSelecionar: () => void
}) {
  const temPlano = dia?.statusPlano === 'APROVADO'
  const temPrazo = (dia?.tarefasComPrazo.length ?? 0) > 0
  const sobrecarga = dia?.sobrecarga === true
  const nBlocos = dia?.plano?.blocos.length ?? 0

  return (
    <button
      type="button"
      className={classeCelula('calendario-mes__celula', { selecionado, sobrecarga, hoje })}
      aria-label={`Dia ${numeroDoDia(data)}`}
      aria-current={hoje ? 'date' : undefined}
      aria-pressed={selecionado}
      onClick={onSelecionar}
    >
      <span className="calendario-mes__dia-num">{numeroDoDia(data)}</span>
      <div className="calendario-mes__marcadores">
        {temPlano ? (
          <span
            className="calendario-indicador calendario-indicador--plano"
            aria-label={nBlocos > 0 ? `Tem plano, ${nBlocos} blocos` : 'Tem plano'}
          />
        ) : null}
        {temPrazo ? (
          <span className="calendario-indicador--icone calendario-indicador--prazo" aria-label="Tem prazo">
            <FlagIcon aria-hidden weight="fill" />
          </span>
        ) : null}
        {sobrecarga ? (
          <span
            className="calendario-indicador--icone calendario-indicador--sobrecarga-icone"
            aria-label="Sobrecarga"
          >
            <WarningCircleIcon aria-hidden weight="fill" />
          </span>
        ) : null}
      </div>
    </button>
  )
}

function PainelDia({
  data,
  dia,
  lente,
  onFechar,
}: {
  data: string
  dia: CalendarioDia
  lente: LenteCalendario
  onFechar: () => void
}) {
  return (
    <section
      className="calendario-painel card"
      aria-label="Detalhe do dia"
    >
      <header className="calendario-painel__header">
        <h2>{formatarDataPainel(data)}</h2>
        <button type="button" className="btn btn--ghost btn--sm" onClick={onFechar}>
          Fechar
        </button>
      </header>

      <CapacidadeBar
        usadosMinutos={dia.minutosAlocados}
        disponiveisMinutos={dia.capacidadeMinutos}
        sobrecarga={dia.sobrecarga}
        versao={dia.plano?.versao}
      />

      {lente === 'plano' ? (
        dia.plano == null ? (
          <p className="calendario-painel__vazio" role="status">
            Sem plano aprovado neste dia.
          </p>
        ) : (
          <ul className="calendario-painel__lista">
            {dia.plano.blocos.map((bloco) => (
              <li key={bloco.id}>
                <Link
                  to={`/tarefas/${bloco.tarefaId}`}
                  state={DRAWER_STATE}
                  className="calendario-painel__titulo"
                >
                  {bloco.titulo}
                </Link>
                <span>{formatarCapacidadeMinutos(bloco.duracaoMinutos)}</span>
              </li>
            ))}
          </ul>
        )
      ) : dia.tarefasComPrazo.length === 0 ? (
        <p className="calendario-painel__vazio" role="status">
          Nenhuma tarefa com prazo neste dia.
        </p>
      ) : (
        <ul className="calendario-painel__lista">
          {dia.tarefasComPrazo.map((tarefa) => (
            <li key={tarefa.id}>
              <Link
                to={`/tarefas/${tarefa.id}`}
                state={DRAWER_STATE}
                className="calendario-painel__titulo"
              >
                {tarefa.titulo}
              </Link>
              <span>{formatarCapacidadeMinutos(tarefa.duracaoMinutos)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
