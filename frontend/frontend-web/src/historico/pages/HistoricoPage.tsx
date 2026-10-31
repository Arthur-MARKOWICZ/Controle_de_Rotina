import { ArrowDownIcon, ArrowUpIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import {
  adicionarDiasIso,
  formatarDataIso,
  formatarIntervaloSemana,
  inicioSemana,
} from '../../capacidade/semana'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { PageHeader } from '../../layout/PageHeader'
import { PeriodoNav } from '../../layout/PeriodoNav'
import {
  consultarHistoricoDia,
  consultarHistoricoSemana,
  type HistoricoResumoResponse,
} from '../api/historicoApi'
import {
  formatarDataCurtaPtBr,
  formatarMinutosTrabalhados,
  tituloDiaHistorico,
  tituloSemanaHistorico,
} from '../formatacao'
import { GraficoXpSemana, type PontoXpDia } from '../GraficoXpSemana'
import { formatarVariacaoPercentual, variacaoPercentual } from '../variacao'

type VisaoHistorico = 'dia' | 'semana'

const VISOES: { valor: VisaoHistorico; label: string }[] = [
  { valor: 'dia', label: 'Dia' },
  { valor: 'semana', label: 'Semana' },
]

const REFERENCIA_COMPARACAO: Record<VisaoHistorico, string> = {
  dia: 'dia anterior',
  semana: 'semana passada',
}

type Resumos = {
  atual: HistoricoResumoResponse
  anterior: HistoricoResumoResponse
  pontosXp: PontoXpDia[] | null
}

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

function resumoVazio(resumo: HistoricoResumoResponse): boolean {
  return (
    resumo.tarefasFinalizadas === 0 &&
    resumo.blocosConcluidos === 0 &&
    resumo.minutosTrabalhados === 0 &&
    resumo.xpGanho === 0
  )
}

async function carregarDia(dataDia: string): Promise<Resumos> {
  const [atual, anterior] = await Promise.all([
    consultarHistoricoDia(dataDia),
    consultarHistoricoDia(adicionarDiasIso(dataDia, -1)),
  ])
  return { atual, anterior, pontosXp: null }
}

async function carregarSemana(semanaInicio: string): Promise<Resumos> {
  const datasSemana = Array.from({ length: 7 }, (_, i) => adicionarDiasIso(semanaInicio, i))
  const [atual, anterior, resumosDias] = await Promise.all([
    consultarHistoricoSemana(semanaInicio),
    consultarHistoricoSemana(adicionarDiasIso(semanaInicio, -7)),
    Promise.all(datasSemana.map((data) => consultarHistoricoDia(data))),
  ])
  const pontosXp = datasSemana.map((data, i) => ({ data, xp: resumosDias[i].xpGanho }))
  return { atual, anterior, pontosXp }
}

function Variacao({
  atual,
  anterior,
  referencia,
}: {
  atual: number
  anterior: number
  referencia: string
}) {
  const variacao = variacaoPercentual(atual, anterior)
  const tendencia =
    variacao == null || variacao === 0 ? 'estavel' : variacao > 0 ? 'alta' : 'queda'
  return (
    <span className={`historico-variacao historico-variacao--${tendencia}`}>
      {tendencia === 'alta' ? <ArrowUpIcon aria-hidden weight="bold" /> : null}
      {tendencia === 'queda' ? <ArrowDownIcon aria-hidden weight="bold" /> : null}
      {`${formatarVariacaoPercentual(variacao)} vs ${referencia}`}
    </span>
  )
}

function Metrica({
  rotulo,
  valor,
  atual,
  anterior,
  referencia,
}: {
  rotulo: string
  valor: string | number
  atual: number
  anterior: number
  referencia: string
}) {
  return (
    <div className="historico-metrica">
      <dt>{rotulo}</dt>
      <dd>
        <span className="historico-metrica__valor">{valor}</span>
        <Variacao atual={atual} anterior={anterior} referencia={referencia} />
      </dd>
    </div>
  )
}

function ResumoCard({
  titulo,
  subtitulo,
  resumo,
  anterior,
  referencia,
}: {
  titulo: string
  subtitulo: string
  resumo: HistoricoResumoResponse
  anterior: HistoricoResumoResponse
  referencia: string
}) {
  return (
    <section className="historico-card card" aria-labelledby="historico-resumo-titulo">
      <header>
        <h2 id="historico-resumo-titulo">{titulo}</h2>
        <p className="historico-periodo">{subtitulo}</p>
      </header>
      {resumoVazio(resumo) ? (
        <p className="historico-vazio" role="status">
          Ainda sem atividade neste período. <Link to="/">Ver o plano de hoje</Link>
        </p>
      ) : (
        <dl className="historico-metricas">
          <Metrica
            rotulo="Tarefas finalizadas"
            valor={resumo.tarefasFinalizadas}
            atual={resumo.tarefasFinalizadas}
            anterior={anterior.tarefasFinalizadas}
            referencia={referencia}
          />
          <Metrica
            rotulo="Blocos concluídos"
            valor={resumo.blocosConcluidos}
            atual={resumo.blocosConcluidos}
            anterior={anterior.blocosConcluidos}
            referencia={referencia}
          />
          <Metrica
            rotulo="Tempo trabalhado"
            valor={formatarMinutosTrabalhados(resumo.minutosTrabalhados)}
            atual={resumo.minutosTrabalhados}
            anterior={anterior.minutosTrabalhados}
            referencia={referencia}
          />
          <Metrica
            rotulo="XP ganho"
            valor={`+${resumo.xpGanho} XP`}
            atual={resumo.xpGanho}
            anterior={anterior.xpGanho}
            referencia={referencia}
          />
        </dl>
      )}
    </section>
  )
}

export function HistoricoPage() {
  const [visao, setVisao] = useState<VisaoHistorico>('semana')
  const [dataDia, setDataDia] = useState(() => formatarDataIso(new Date()))
  const [semanaInicio, setSemanaInicio] = useState(() => inicioSemana())
  const [resumos, setResumos] = useState<Resumos | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [jaCarregou, setJaCarregou] = useState(false)

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro(null)
    const carga = visao === 'dia' ? carregarDia(dataDia) : carregarSemana(semanaInicio)
    carga
      .then((proximos) => {
        if (ativo) {
          setResumos(proximos)
        }
      })
      .catch((error) => {
        if (!ativo) {
          return
        }
        setErro(mensagemErro(error, 'Não foi possível carregar o histórico.'))
        setResumos(null)
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
  }, [visao, dataDia, semanaInicio])

  const hoje = formatarDataIso(new Date())
  const ehDia = visao === 'dia'
  const recarregando = carregando && jaCarregou

  function andar(direcao: -1 | 1) {
    if (ehDia) {
      setDataDia((atual) => adicionarDiasIso(atual, direcao))
    } else {
      setSemanaInicio((atual) => adicionarDiasIso(atual, direcao * 7))
    }
  }

  function irParaHoje() {
    if (ehDia) {
      setDataDia(hoje)
    } else {
      setSemanaInicio(inicioSemana())
    }
  }

  return (
    <main className="historico-page">
      <PageHeader
        title="Histórico"
        description="Progresso do dia e da semana"
        actions={
          <PeriodoNav
            opcoes={VISOES}
            visao={visao}
            onVisaoChange={setVisao}
            rotulo={
              ehDia
                ? formatarDataCurtaPtBr(dataDia)
                : `Semana ${formatarIntervaloSemana(semanaInicio)}`
            }
            onAnterior={() => andar(-1)}
            onProximo={() => andar(1)}
            onHoje={irParaHoje}
            rotuloAnterior={ehDia ? 'Dia anterior' : 'Semana anterior'}
            rotuloProximo={ehDia ? 'Próximo dia' : 'Próxima semana'}
            podeAvancar={!ehDia || dataDia < hoje}
          />
        }
      />

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      {!jaCarregou ? <Skeleton variant="historico-metricas" /> : null}

      {jaCarregou && resumos ? (
        <div
          className={
            recarregando ? 'historico-conteudo historico-conteudo--recarregando' : 'historico-conteudo'
          }
          aria-busy={recarregando || undefined}
        >
          <ResumoCard
            titulo={
              ehDia ? tituloDiaHistorico(dataDia, hoje) : tituloSemanaHistorico(semanaInicio, inicioSemana())
            }
            subtitulo={ehDia ? formatarDataCurtaPtBr(dataDia) : formatarIntervaloSemana(semanaInicio)}
            resumo={resumos.atual}
            anterior={resumos.anterior}
            referencia={REFERENCIA_COMPARACAO[visao]}
          />
          {!ehDia && resumos.pontosXp ? (
            <GraficoXpSemana pontos={resumos.pontosXp} hojeIso={hoje} />
          ) : null}
        </div>
      ) : null}
    </main>
  )
}
