import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { useAuth } from '../../auth/session/AuthContext'
import {
  adicionarDiasIso,
  formatarDataIso,
  formatarIntervaloSemana,
  inicioSemana,
} from '../../capacidade/semana'
import {
  consultarHistoricoDia,
  consultarHistoricoSemana,
  type HistoricoResumoResponse,
} from '../api/historicoApi'
import { formatarMinutosTrabalhados } from '../formatacao'

function mensagemErro(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message
  }
  return fallback
}

function ResumoCard({
  titulo,
  subtitulo,
  resumo,
}: {
  titulo: string
  subtitulo: string
  resumo: HistoricoResumoResponse | null
}) {
  return (
    <section className="historico-card" aria-labelledby={`${titulo}-titulo`}>
      <header>
        <h2 id={`${titulo}-titulo`}>{titulo}</h2>
        <p className="historico-periodo">{subtitulo}</p>
      </header>
      {resumo ? (
        <dl className="historico-metricas">
          <div>
            <dt>Tarefas finalizadas</dt>
            <dd>{resumo.tarefasFinalizadas}</dd>
          </div>
          <div>
            <dt>Blocos concluídos</dt>
            <dd>{resumo.blocosConcluidos}</dd>
          </div>
          <div>
            <dt>Tempo trabalhado</dt>
            <dd>{formatarMinutosTrabalhados(resumo.minutosTrabalhados)}</dd>
          </div>
          <div>
            <dt>XP ganho</dt>
            <dd>+{resumo.xpGanho} XP</dd>
          </div>
        </dl>
      ) : (
        <p className="historico-vazio">Carregando…</p>
      )}
    </section>
  )
}

export function HistoricoPage() {
  const { logout } = useAuth()
  const [dataDia, setDataDia] = useState(() => formatarDataIso(new Date()))
  const [semanaInicio, setSemanaInicio] = useState(() => inicioSemana())
  const [dia, setDia] = useState<HistoricoResumoResponse | null>(null)
  const [semana, setSemana] = useState<HistoricoResumoResponse | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro(null)
    Promise.all([consultarHistoricoDia(dataDia), consultarHistoricoSemana(semanaInicio)])
      .then(([resumoDia, resumoSemana]) => {
        if (!ativo) {
          return
        }
        setDia(resumoDia)
        setSemana(resumoSemana)
      })
      .catch((error) => {
        if (!ativo) {
          return
        }
        setErro(mensagemErro(error, 'Não foi possível carregar o histórico.'))
        setDia(null)
        setSemana(null)
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

  return (
    <div className="home-page historico-page">
      <div className="home-panel">
        <header className="home-header">
          <div>
            <h1>Histórico</h1>
            <p className="auth-subtitle">Progresso do dia e da semana</p>
          </div>
          <nav className="home-nav" aria-label="Navegação principal">
            <Link to="/">Home</Link>
            <Link to="/tarefas">Tarefas</Link>
            <Link to="/capacidade">Capacidade</Link>
            <Link to="/perfil">Meu perfil</Link>
            <button type="button" onClick={() => void logout()}>
              Sair
            </button>
          </nav>
        </header>

        {erro ? <p className="form-error" role="alert">{erro}</p> : null}
        {carregando ? <p className="auth-subtitle">Atualizando resumos…</p> : null}

        <div className="historico-nav-periodo" aria-label="Navegação do dia">
          <button type="button" onClick={() => setDataDia(adicionarDiasIso(dataDia, -1))}>
            Dia anterior
          </button>
          <span>{dataDia.split('-').reverse().join('/')}</span>
          <button
            type="button"
            disabled={!podeAvancarDia}
            onClick={() => setDataDia(adicionarDiasIso(dataDia, 1))}
          >
            Próximo dia
          </button>
        </div>

        <ResumoCard
          titulo="Hoje"
          subtitulo={dataDia === hoje ? 'Dia atual' : `Dia ${dataDia}`}
          resumo={dia}
        />

        <div className="historico-nav-periodo" aria-label="Navegação da semana">
          <button
            type="button"
            onClick={() => setSemanaInicio(adicionarDiasIso(semanaInicio, -7))}
          >
            Semana anterior
          </button>
          <span>Semana {formatarIntervaloSemana(semanaInicio)}</span>
          <button
            type="button"
            onClick={() => setSemanaInicio(adicionarDiasIso(semanaInicio, 7))}
          >
            Próxima semana
          </button>
        </div>

        <ResumoCard
          titulo="Esta semana"
          subtitulo={formatarIntervaloSemana(semanaInicio)}
          resumo={semana}
        />
      </div>
    </div>
  )
}
