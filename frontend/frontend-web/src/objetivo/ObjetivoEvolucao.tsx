import { useId } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { formatarDataCurtaPtBr } from '../historico/formatacao'
import { formatarCapacidadeMinutos } from '../planner/data'
import { diasEntreDatas, type SerieEvolucao } from './evolucaoObjetivo'

type Props = {
  /** `null` quando não há nada a mostrar (a seção some, a menos que esteja carregando ou em erro). */
  serie: SerieEvolucao | null
  carregando: boolean
  erro: string | null
  onTentarDeNovo: () => void
}

const LARGURA = 360
const ALTURA = 200
const MARGEM = { esquerda: 62, direita: 12, topo: 18, base: 28 }
const AREA_LARGURA = LARGURA - MARGEM.esquerda - MARGEM.direita
const AREA_ALTURA = ALTURA - MARGEM.topo - MARGEM.base

/** `yyyy-MM-dd` → `dd/MM`. */
function diaMes(iso: string): string {
  return formatarDataCurtaPtBr(iso).slice(0, 5)
}

function arredondar(valor: number): number {
  return Math.round(valor * 10) / 10
}

type GraficoProps = { serie: SerieEvolucao }

/** Linha em degraus: o acumulado sobe no dia em que há minutos e fica reto nos dias parados. */
function Grafico({ serie }: GraficoProps) {
  const span = Math.max(1, diasEntreDatas(serie.inicio, serie.fim))
  const maximo = Math.max(1, serie.total)
  const x = (data: string) => arredondar(MARGEM.esquerda + (diasEntreDatas(serie.inicio, data) / span) * AREA_LARGURA)
  const y = (minutos: number) => arredondar(MARGEM.topo + AREA_ALTURA - (minutos / maximo) * AREA_ALTURA)
  const base = MARGEM.topo + AREA_ALTURA
  const direita = MARGEM.esquerda + AREA_LARGURA

  let caminho = `M ${MARGEM.esquerda} ${y(serie.antesDoHistorico)}`
  for (const degrau of serie.degraus) {
    caminho += ` H ${x(degrau.data)} V ${y(degrau.acumulado)}`
  }
  caminho += ` H ${direita}`

  const marcadorX = serie.marcador ? x(serie.marcador.data) : null
  const marcadorAlinhaDireita = marcadorX != null && marcadorX > MARGEM.esquerda + AREA_LARGURA * 0.55

  return (
    <svg
      className="objetivo-evolucao__svg"
      viewBox={`0 0 ${LARGURA} ${ALTURA}`}
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      focusable="false"
    >
      <line className="objetivo-evolucao__eixo" x1={MARGEM.esquerda} y1={base} x2={direita} y2={base} />
      <line
        className="objetivo-evolucao__grade"
        x1={MARGEM.esquerda}
        y1={MARGEM.topo}
        x2={direita}
        y2={MARGEM.topo}
      />
      <text className="objetivo-evolucao__rotulo" x={MARGEM.esquerda - 6} y={base + 4} textAnchor="end">
        0
      </text>
      <text className="objetivo-evolucao__rotulo" x={MARGEM.esquerda - 6} y={MARGEM.topo + 4} textAnchor="end">
        {formatarCapacidadeMinutos(serie.total)}
      </text>

      <path className="objetivo-evolucao__linha" d={caminho} fill="none" />
      {serie.degraus.map((degrau) => (
        <circle
          key={degrau.data}
          className="objetivo-evolucao__ponto"
          cx={x(degrau.data)}
          cy={y(degrau.acumulado)}
          r={2.5}
        />
      ))}

      {serie.marcador && marcadorX != null ? (
        <g>
          <line
            className="objetivo-evolucao__marcador"
            x1={marcadorX}
            y1={MARGEM.topo}
            x2={marcadorX}
            y2={base}
          />
          <text
            className="objetivo-evolucao__rotulo objetivo-evolucao__rotulo--marcador"
            x={marcadorX + (marcadorAlinhaDireita ? -4 : 4)}
            y={MARGEM.topo - 5}
            textAnchor={marcadorAlinhaDireita ? 'end' : 'start'}
          >
            {serie.marcador.rotulo}
          </text>
        </g>
      ) : null}

      <text className="objetivo-evolucao__rotulo" x={MARGEM.esquerda} y={ALTURA - 8} textAnchor="start">
        {diaMes(serie.inicio)}
      </text>
      {serie.fim !== serie.inicio ? (
        <text className="objetivo-evolucao__rotulo" x={direita} y={ALTURA - 8} textAnchor="end">
          {diaMes(serie.fim)}
        </text>
      ) : null}
    </svg>
  )
}

/** Minutos concluídos acumulados por dia, na mesma base do progresso. */
export function ObjetivoEvolucao({ serie, carregando, erro, onTentarDeNovo }: Props) {
  const tituloId = useId()

  if (!carregando && erro == null && serie == null) {
    return null
  }

  return (
    <section className="objetivo-evolucao" aria-labelledby={tituloId}>
      <h2 id={tituloId} className="objetivo-evolucao__titulo">
        Evolução
      </h2>

      {carregando && serie == null ? <Skeleton variant="historico-calibracao" /> : null}

      {erro ? (
        <>
          <InlineAlert>{erro}</InlineAlert>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onTentarDeNovo}>
            Tentar de novo
          </button>
        </>
      ) : null}

      {serie ? (
        <>
          <p className="objetivo-evolucao__resumo">
            <strong>{formatarCapacidadeMinutos(serie.total)}</strong> acumuladas
            {serie.antesDoHistorico > 0 ? (
              <>
                {' · '}
                {formatarCapacidadeMinutos(serie.antesDoHistorico)} antes do histórico
              </>
            ) : null}
          </p>

          {serie.temGrafico ? (
            <>
              <Grafico serie={serie} />
              {serie.marcador ? (
                <p className="objetivo-evolucao__legenda">
                  <span className="objetivo-evolucao__legenda-marca" aria-hidden="true" />
                  {serie.marcador.rotulo}
                </p>
              ) : null}
              <table className="visually-hidden" aria-label="Minutos concluídos por dia">
                <thead>
                  <tr>
                    <th scope="col">Dia</th>
                    <th scope="col">No dia</th>
                    <th scope="col">Acumulado</th>
                  </tr>
                </thead>
                <tbody>
                  {serie.degraus.map((degrau) => (
                    <tr key={degrau.data}>
                      <td>{formatarDataCurtaPtBr(degrau.data)}</td>
                      <td>{formatarCapacidadeMinutos(degrau.noDia)}</td>
                      <td>{formatarCapacidadeMinutos(degrau.acumulado)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
