import { formatarDataCurtaPtBr, formatarDiaSemanaCurto } from './formatacao'

export type PontoXpDia = {
  data: string
  xp: number
}

type GraficoXpSemanaProps = {
  pontos: PontoXpDia[]
  hojeIso?: string
}

function classeBarra(ponto: PontoXpDia, hoje: boolean): string {
  if (ponto.xp <= 0) {
    return 'historico-grafico__barra historico-grafico__barra--vazia'
  }
  return hoje ? 'historico-grafico__barra historico-grafico__barra--hoje' : 'historico-grafico__barra'
}

export function GraficoXpSemana({ pontos, hojeIso }: GraficoXpSemanaProps) {
  const maxXp = Math.max(1, ...pontos.map((p) => p.xp))

  return (
    <section className="historico-grafico card" aria-labelledby="historico-grafico-titulo">
      <h2 id="historico-grafico-titulo">XP por dia</h2>
      <p className="historico-periodo">Semana selecionada</p>

      <div className="historico-grafico__plot" role="img" aria-hidden="true">
        {pontos.map((ponto) => {
          const hoje = ponto.data === hojeIso
          const alturaPct = ponto.xp > 0 ? Math.max((ponto.xp / maxXp) * 100, 4) : 0
          return (
            <div
              key={ponto.data}
              className={
                hoje ? 'historico-grafico__coluna historico-grafico__coluna--hoje' : 'historico-grafico__coluna'
              }
              title={`${ponto.xp} XP`}
            >
              <span className="historico-grafico__valor">{ponto.xp}</span>
              <div className="historico-grafico__trilha">
                <div
                  className={classeBarra(ponto, hoje)}
                  style={{ height: ponto.xp > 0 ? `${alturaPct}%` : '2px' }}
                />
              </div>
              <span className="historico-grafico__label">{formatarDiaSemanaCurto(ponto.data)}</span>
            </div>
          )
        })}
      </div>

      <table className="visually-hidden" aria-label="XP por dia da semana">
        <thead>
          <tr>
            <th scope="col">Dia</th>
            <th scope="col">XP</th>
          </tr>
        </thead>
        <tbody>
          {pontos.map((ponto) => (
            <tr key={ponto.data}>
              <td>{formatarDataCurtaPtBr(ponto.data)}</td>
              <td>{ponto.xp} XP</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
