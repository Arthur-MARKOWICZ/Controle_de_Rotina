import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { adicionarDiasIso } from '../capacidade/semana'
import { InlineAlert } from '../feedback/InlineAlert'
import { formatarDiaCurto } from '../home/formatacao'
import {
  aceitarProposta,
  buscarPlano,
  descartarProposta,
  gerarPlano,
  type PlanoResponse,
} from '../planner/api/planoApi'
import { dataHojeIso, formatarCapacidadeMinutos } from '../planner/data'
import { proximaSegunda } from './datas'
import { mensagemErro } from './mensagemErro'
import type { DesfechoPlano } from './tipos'

const DIAS_JANELA = 14

type Props = {
  onDesfecho: (desfecho: Exclude<DesfechoPlano, 'NAO_TRATADO'>) => void
  /** Data civil de hoje em BRT; injetável para teste. */
  hojeIso?: string
}

type Estado =
  | { tipo: 'inicial' }
  | { tipo: 'jaAprovado' }
  | { tipo: 'proposta'; propostaId: string; dias: PlanoResponse[] }

async function diasDaProposta(inicio: string, propostaId: string): Promise<PlanoResponse[]> {
  const datas = Array.from({ length: DIAS_JANELA }, (_, i) => adicionarDiasIso(inicio, i))
  const planos = await Promise.all(datas.map((data) => buscarPlano({ data })))
  return planos.filter((p): p is PlanoResponse => p?.propostaId === propostaId)
}

function rotuloBloco(bloco: PlanoResponse['blocos'][number]): string {
  return bloco.tarefa?.titulo ?? bloco.habito?.titulo ?? 'Bloco'
}

/** Passo 4: gerar a proposta da próxima semana e aprovar ou recusar explicitamente. */
export function PassoPlano({ onDesfecho, hojeIso }: Props) {
  const segunda = proximaSegunda(hojeIso ?? dataHojeIso())
  const [estado, setEstado] = useState<Estado>({ tipo: 'inicial' })
  const [ocupado, setOcupado] = useState<'gerar' | 'aprovar' | 'recusar' | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const emAndamento = useRef(false)

  async function comTrava(tipo: 'gerar' | 'aprovar' | 'recusar', acao: () => Promise<void>) {
    if (emAndamento.current) return
    emAndamento.current = true
    setOcupado(tipo)
    setErro(null)
    try {
      await acao()
    } finally {
      emAndamento.current = false
      setOcupado(null)
    }
  }

  function gerar() {
    return comTrava('gerar', async () => {
      try {
        const plano = await gerarPlano(segunda)
        const propostaId = plano.propostaId
        if (!propostaId) {
          setErro('O planner não devolveu uma proposta.')
          return
        }
        const dias = await diasDaProposta(segunda, propostaId)
        setEstado({ tipo: 'proposta', propostaId, dias: dias.length ? dias : [plano] })
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          const existente = await buscarPlano({ data: segunda }).catch(() => null)
          if (existente?.status === 'APROVADO') {
            setEstado({ tipo: 'jaAprovado' })
            return
          }
        }
        setErro(mensagemErro(error, 'Não foi possível gerar o plano.'))
      }
    })
  }

  function resolver(tipo: 'aprovar' | 'recusar', propostaId: string) {
    return comTrava(tipo, async () => {
      try {
        if (tipo === 'aprovar') {
          await aceitarProposta(propostaId)
          onDesfecho('APROVADO')
        } else {
          await descartarProposta(propostaId)
          onDesfecho('RECUSADO')
        }
      } catch (error) {
        setErro(mensagemErro(error, 'Não foi possível concluir a proposta.'))
        if (error instanceof ApiError && error.status === 409) {
          setEstado({ tipo: 'inicial' })
        }
      }
    })
  }

  if (estado.tipo === 'jaAprovado') {
    return (
      <div className="revisao-passo__estado" role="status">
        <p>A semana que começa em {formatarDiaCurto(segunda)} já tem plano aprovado.</p>
        <Link to="/plano" className="btn btn--secondary">
          Ver plano
        </Link>
      </div>
    )
  }

  if (estado.tipo === 'inicial') {
    return (
      <div className="revisao-passo__estado">
        {erro ? <InlineAlert>{erro}</InlineAlert> : null}
        <p>O planner monta a proposta a partir de {formatarDiaCurto(segunda)}.</p>
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => void gerar()}
          disabled={ocupado !== null}
          aria-busy={ocupado === 'gerar'}
        >
          {ocupado === 'gerar' ? 'Gerando…' : 'Gerar plano'}
        </button>
      </div>
    )
  }

  const { propostaId, dias } = estado
  return (
    <div className="revisao-plano">
      {erro ? <InlineAlert>{erro}</InlineAlert> : null}
      <ul className="revisao-plano__dias">
        {dias.map((dia) => (
          <li key={dia.id} className="revisao-plano__dia">
            <h3 className="revisao-grupo__titulo">{formatarDiaCurto(dia.data)}</h3>
            {dia.blocos.length > 0 ? (
              <ul className="revisao-plano__blocos">
                {dia.blocos.map((bloco) => (
                  <li key={bloco.id}>
                    {rotuloBloco(bloco)} · {formatarCapacidadeMinutos(bloco.duracaoMinutos)}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="revisao-passo__vazio">Sem blocos.</p>
            )}
            {dia.temConflito || dia.mensagem ? (
              <p className={dia.temConflito ? 'revisao-plano__aviso' : 'revisao-item__meta'}>
                {dia.mensagem ?? 'Há conflito de prazo neste dia.'}
              </p>
            ) : null}
            {dia.itensNaoAlocados?.length ? (
              <p className="revisao-item__meta">
                Não coube: {dia.itensNaoAlocados.map((i) => i.titulo).join(', ')}
              </p>
            ) : null}
          </li>
        ))}
      </ul>
      <div className="revisao-plano__acoes">
        <button
          type="button"
          className="btn btn--primary"
          onClick={() => void resolver('aprovar', propostaId)}
          disabled={ocupado !== null}
          aria-busy={ocupado === 'aprovar'}
        >
          {ocupado === 'aprovar' ? 'Aprovando…' : 'Aprovar plano'}
        </button>
        <button
          type="button"
          className="btn btn--secondary"
          onClick={() => void resolver('recusar', propostaId)}
          disabled={ocupado !== null}
          aria-busy={ocupado === 'recusar'}
        >
          Não aprovar agora
        </button>
      </div>
    </div>
  )
}
