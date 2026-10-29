import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { formatarDataIso } from '../../capacidade/semana'
import { InlineAlert } from '../../feedback/InlineAlert'
import { CapacidadeBar } from '../../home/CapacidadeBar'
import { labelStatusPlano } from '../../home/labels'
import { formatarCapacidadeMinutos } from '../../planner/data'
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

export function CalendarioPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const visao = parseVisao(searchParams.get('visao'))
  const lente = parseLente(searchParams.get('lente'))
  const dataRef =
    searchParams.get('dataDe') ??
    formatarDataIso(new Date())

  const [dias, setDias] = useState<CalendarioDia[]>([])
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(true)
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
    const novaVisao = parcial.visao ?? visao
    const novaLente = parcial.lente ?? lente
    const novaData = parcial.dataDe ?? dataRef
    next.set('visao', novaVisao)
    next.set('lente', novaLente)
    next.set('dataDe', novaData)
    setSearchParams(next, { replace: true })
  }

  const diaDetalhe = diaSelecionado ? diaPorData(dias, diaSelecionado) : undefined
  const diasSemana =
    visao === 'semana' ? diasDaSemanaIso(intervalo.dataDe) : []

  return (
    <main className="calendario-page">
      <header className="calendario-header">
        <h1>Calendário</h1>
        <p className="calendario-subtitle">Plano aprovado e prazos no horizonte</p>
      </header>

      <div className="calendario-controles">
        <div
          className="calendario-toggle"
          role="radiogroup"
          aria-label="Período"
        >
          <button
            type="button"
            role="radio"
            aria-checked={visao === 'semana'}
            className={
              visao === 'semana'
                ? 'btn btn--sm calendario-toggle__btn calendario-toggle__btn--ativo'
                : 'btn btn--ghost btn--sm calendario-toggle__btn'
            }
            onClick={() => atualizarQuery({ visao: 'semana' })}
          >
            Semana
          </button>
          <button
            type="button"
            role="radio"
            aria-checked={visao === 'mes'}
            className={
              visao === 'mes'
                ? 'btn btn--sm calendario-toggle__btn calendario-toggle__btn--ativo'
                : 'btn btn--ghost btn--sm calendario-toggle__btn'
            }
            onClick={() => atualizarQuery({ visao: 'mes', dataDe: intervaloPorVisao('mes', dataRef).dataDe })}
          >
            Mês
          </button>
        </div>

        <div
          className="calendario-toggle"
          role="radiogroup"
          aria-label="Lente"
        >
          <button
            type="button"
            role="radio"
            aria-checked={lente === 'plano'}
            className={
              lente === 'plano'
                ? 'btn btn--sm calendario-toggle__btn calendario-toggle__btn--ativo'
                : 'btn btn--ghost btn--sm calendario-toggle__btn'
            }
            onClick={() => atualizarQuery({ lente: 'plano' })}
          >
            Plano
          </button>
          <button
            type="button"
            role="radio"
            aria-checked={lente === 'prazos'}
            className={
              lente === 'prazos'
                ? 'btn btn--sm calendario-toggle__btn calendario-toggle__btn--ativo'
                : 'btn btn--ghost btn--sm calendario-toggle__btn'
            }
            onClick={() => atualizarQuery({ lente: 'prazos' })}
          >
            Prazos
          </button>
        </div>

        <div className="calendario-nav-periodo" aria-label="Navegação do período">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() =>
              atualizarQuery({ dataDe: navegarPeriodo(visao, dataRef, -1) })
            }
          >
            Anterior
          </button>
          <span className="calendario-nav-periodo__label">{labelPeriodo(visao, dataRef)}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() =>
              atualizarQuery({ dataDe: navegarPeriodo(visao, dataRef, 1) })
            }
          >
            Próximo
          </button>
        </div>
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}
      {carregando ? <p className="calendario-subtitle">Carregando calendário…</p> : null}

      {visao === 'semana' ? (
        <section className="calendario-semana" aria-label="Grade da semana">
          <div className="calendario-semana__cabecalho" aria-hidden="true">
            {DIAS_CABECALHO.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
          <div className="calendario-semana__grade">
            {diasSemana.map((data, indice) => {
              const dia = diaPorData(dias, data)
              return (
                <CelulaSemana
                  key={data}
                  data={data}
                  indice={indice}
                  dia={dia}
                  lente={lente}
                  selecionado={diaSelecionado === data}
                  onSelecionar={() => setDiaSelecionado(data)}
                />
              )
            })}
          </div>
        </section>
      ) : (
        <section className="calendario-mes" aria-label="Grade do mês">
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
              const dia = diaPorData(dias, celula.data)
              return (
                <CelulaMes
                  key={celula.data}
                  data={celula.data}
                  dia={dia}
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

function CelulaSemana({
  data,
  indice,
  dia,
  lente,
  selecionado,
  onSelecionar,
}: {
  data: string
  indice: number
  dia: CalendarioDia | undefined
  lente: LenteCalendario
  selecionado: boolean
  onSelecionar: () => void
}) {
  const sobrecarga = dia?.sobrecarga === true
  const chipAprovado = dia?.statusPlano === 'APROVADO'
  const ariaNome = `${labelDiaCurto(indice)} ${numeroDoDia(data)}`

  return (
    <button
      type="button"
      className={
        selecionado
          ? 'calendario-semana__celula calendario-semana__celula--selecionada'
          : sobrecarga
            ? 'calendario-semana__celula calendario-semana__celula--sobrecarga'
            : 'calendario-semana__celula'
      }
      aria-label={ariaNome}
      aria-pressed={selecionado}
      onClick={onSelecionar}
    >
      <div className="calendario-semana__celula-topo">
        <span className="calendario-semana__dia-num">{numeroDoDia(data)}</span>
        {chipAprovado ? (
          <span className="badge status-plano-aprovado">{labelStatusPlano('APROVADO')}</span>
        ) : null}
      </div>
      {sobrecarga ? (
        <span className="calendario-indicador calendario-indicador--sobrecarga" aria-label="Sobrecarga">
          Sobrecarga
        </span>
      ) : null}
      {lente === 'plano' ? (
        <ul className="calendario-semana__lista">
          {(dia?.plano?.blocos ?? []).map((bloco) => (
            <li key={bloco.id}>
              <span className="calendario-semana__titulo" title={bloco.titulo}>
                {bloco.titulo}
              </span>
              <span className="calendario-semana__duracao">
                {formatarCapacidadeMinutos(bloco.duracaoMinutos)}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <ul className="calendario-semana__lista">
          {(dia?.tarefasComPrazo ?? []).map((tarefa) => (
            <li key={tarefa.id}>
              <span className="calendario-semana__titulo" title={tarefa.titulo}>
                {tarefa.titulo}
              </span>
            </li>
          ))}
        </ul>
      )}
    </button>
  )
}

function CelulaMes({
  data,
  dia,
  selecionado,
  onSelecionar,
}: {
  data: string
  dia: CalendarioDia | undefined
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
      className={
        selecionado
          ? 'calendario-mes__celula calendario-mes__celula--selecionada'
          : sobrecarga
            ? 'calendario-mes__celula calendario-mes__celula--sobrecarga'
            : 'calendario-mes__celula'
      }
      aria-label={`Dia ${numeroDoDia(data)}`}
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
          <span
            className="calendario-indicador calendario-indicador--prazo"
            aria-label="Tem prazo"
          />
        ) : null}
        {sobrecarga ? (
          <span
            className="calendario-indicador calendario-indicador--sobrecarga"
            aria-label="Sobrecarga"
          />
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
              <Link to={`/tarefas/${tarefa.id}`} className="calendario-painel__titulo">
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
