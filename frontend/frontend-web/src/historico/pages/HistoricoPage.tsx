import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import {
  adicionarDiasIso,
  formatarDataIso,
  formatarIntervaloSemana,
  inicioSemana,
} from '../../capacidade/semana'
import { InlineAlert } from '../../feedback/InlineAlert'
import {
  consultarHistoricoDia,
  consultarHistoricoSemana,
  type HistoricoResumoResponse,
} from '../api/historicoApi'
import { formatarDataCurtaPtBr, formatarMinutosTrabalhados, tituloDiaHistorico } from '../formatacao'
import { GraficoXpSemana, type PontoXpDia } from '../GraficoXpSemana'
import { formatarVariacaoPercentual, variacaoPercentual } from '../variacao'

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

function labelVsSemanaPassada(atual: number, anterior: number): string {
  return `${formatarVariacaoPercentual(variacaoPercentual(atual, anterior))} vs. semana passada`
}

function resumoVazio(resumo: HistoricoResumoResponse | null): boolean {
  if (!resumo) {
    return false
  }
  return (
    resumo.tarefasFinalizadas === 0 &&
    resumo.blocosConcluidos === 0 &&
    resumo.minutosTrabalhados === 0 &&
    resumo.xpGanho === 0
  )
}

function Metrica({
  rotulo,
  valor,
  comparacao,
}: {
  rotulo: string
  valor: string | number
  comparacao?: string
}) {
  return (
    <div className="historico-metrica">
      <dt>{rotulo}</dt>
      <dd>
        <span className="historico-metrica__valor">{valor}</span>
        {comparacao ? <span className="historico-variacao">{comparacao}</span> : null}
      </dd>
    </div>
  )
}

function ResumoCard({
  headingId,
  titulo,
  subtitulo,
  resumo,
  comparacao,
  empty,
}: {
  headingId: string
  titulo: string
  subtitulo: string
  resumo: HistoricoResumoResponse | null
  comparacao?: HistoricoResumoResponse | null
  empty?: ReactNode
}) {
  const mostrarEmpty = resumo != null && resumoVazio(resumo) && empty

  return (
    <section className="historico-card card" aria-labelledby={headingId}>
      <header>
        <h2 id={headingId}>{titulo}</h2>
        <p className="historico-periodo">{subtitulo}</p>
      </header>
      {resumo == null ? (
        <p className="historico-vazio">Carregando…</p>
      ) : mostrarEmpty ? (
        empty
      ) : (
        <dl className="historico-metricas">
          <Metrica
            rotulo="Tarefas finalizadas"
            valor={resumo.tarefasFinalizadas}
            comparacao={
              comparacao
                ? labelVsSemanaPassada(resumo.tarefasFinalizadas, comparacao.tarefasFinalizadas)
                : undefined
            }
          />
          <Metrica
            rotulo="Blocos concluídos"
            valor={resumo.blocosConcluidos}
            comparacao={
              comparacao
                ? labelVsSemanaPassada(resumo.blocosConcluidos, comparacao.blocosConcluidos)
                : undefined
            }
          />
          <Metrica
            rotulo="Tempo trabalhado"
            valor={formatarMinutosTrabalhados(resumo.minutosTrabalhados)}
            comparacao={
              comparacao
                ? labelVsSemanaPassada(resumo.minutosTrabalhados, comparacao.minutosTrabalhados)
                : undefined
            }
          />
          <Metrica
            rotulo="XP ganho"
            valor={`+${resumo.xpGanho} XP`}
            comparacao={
              comparacao ? labelVsSemanaPassada(resumo.xpGanho, comparacao.xpGanho) : undefined
            }
          />
        </dl>
      )}
    </section>
  )
}

export function HistoricoPage() {
  const [dataDia, setDataDia] = useState(() => formatarDataIso(new Date()))
  const [semanaInicio, setSemanaInicio] = useState(() => inicioSemana())
  const [dia, setDia] = useState<HistoricoResumoResponse | null>(null)
  const [semana, setSemana] = useState<HistoricoResumoResponse | null>(null)
  const [semanaAnterior, setSemanaAnterior] = useState<HistoricoResumoResponse | null>(null)
  const [pontosXp, setPontosXp] = useState<PontoXpDia[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro(null)
    const semanaPassadaInicio = adicionarDiasIso(semanaInicio, -7)
    const datasSemana = Array.from({ length: 7 }, (_, i) => adicionarDiasIso(semanaInicio, i))

    Promise.all([
      consultarHistoricoDia(dataDia),
      consultarHistoricoSemana(semanaInicio),
      consultarHistoricoSemana(semanaPassadaInicio),
      Promise.all(datasSemana.map((data) => consultarHistoricoDia(data))),
    ])
      .then(([resumoDia, resumoSemana, resumoSemanaAnterior, resumosDias]) => {
        if (!ativo) {
          return
        }
        setDia(resumoDia)
        setSemana(resumoSemana)
        setSemanaAnterior(resumoSemanaAnterior)
        setPontosXp(
          datasSemana.map((data, i) => ({
            data,
            xp: resumosDias[i].xpGanho,
          })),
        )
      })
      .catch((error) => {
        if (!ativo) {
          return
        }
        setErro(mensagemErro(error, 'Não foi possível carregar o histórico.'))
        setDia(null)
        setSemana(null)
        setSemanaAnterior(null)
        setPontosXp(null)
      })
      .finally(() => {
        if (ativo) {
          setCarregando(false)
        }
      })
    return () => {
      ativo = false
    }
  }, [dataDia, semanaInicio])

  const hoje = formatarDataIso(new Date())
  const podeAvancarDia = dataDia < hoje
  const emptyMotivacional = (
    <p className="historico-vazio" role="status">
      Ainda sem atividade neste período.{' '}
      <Link to="/">Ver o plano de hoje</Link>
    </p>
  )

  return (
    <main className="historico-page">
      <header className="historico-header">
        <h1>Histórico</h1>
        <p className="historico-subtitle">Progresso do dia e da semana</p>
      </header>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}
      {carregando ? <p className="historico-subtitle">Atualizando resumos…</p> : null}

      <section className="historico-bloco" aria-label="Resumo do dia">
        <div className="historico-nav-periodo" aria-label="Navegação do dia">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setDataDia(adicionarDiasIso(dataDia, -1))}
          >
            Dia anterior
          </button>
          <span className="historico-nav-periodo__label">{formatarDataCurtaPtBr(dataDia)}</span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            disabled={!podeAvancarDia}
            onClick={() => setDataDia(adicionarDiasIso(dataDia, 1))}
          >
            Próximo dia
          </button>
        </div>

        <ResumoCard
          headingId="historico-dia-titulo"
          titulo={tituloDiaHistorico(dataDia, hoje)}
          subtitulo={formatarDataCurtaPtBr(dataDia)}
          resumo={dia}
          empty={emptyMotivacional}
        />
      </section>

      <section className="historico-bloco" aria-label="Resumo da semana">
        <div className="historico-nav-periodo" aria-label="Navegação da semana">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setSemanaInicio(adicionarDiasIso(semanaInicio, -7))}
          >
            Semana anterior
          </button>
          <span className="historico-nav-periodo__label">
            Semana {formatarIntervaloSemana(semanaInicio)}
          </span>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => setSemanaInicio(adicionarDiasIso(semanaInicio, 7))}
          >
            Próxima semana
          </button>
        </div>

        <ResumoCard
          headingId="historico-semana-titulo"
          titulo="Esta semana"
          subtitulo={formatarIntervaloSemana(semanaInicio)}
          resumo={semana}
          comparacao={semanaAnterior}
          empty={emptyMotivacional}
        />

        {pontosXp ? <GraficoXpSemana pontos={pontosXp} /> : null}
      </section>
    </main>
  )
}
