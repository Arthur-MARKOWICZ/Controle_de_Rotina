import { useEffect, useState } from 'react'

import { ApiError } from '../auth/api/authApi'
import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { TagChip } from '../tag/TagChip'
import { consultarCalibracao, type CalibracaoResponse } from './api/historicoApi'
import { formatarRazao, fraseCalibracao } from './formatacao'

/** Quantos recortes por tag e por projeto o cartão mostra. */
export const MAX_RECORTES = 3

const ERRO_PADRAO = 'Não foi possível carregar sua calibração.'

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; dados: CalibracaoResponse }

function mensagemErro(error: unknown): string {
  if ((error instanceof ApiError || error instanceof Error) && error.message) {
    return error.message
  }
  return ERRO_PADRAO
}

function textoFaltam(faltam: number): string {
  return faltam === 1
    ? 'falta 1 bloco concluído com timer'
    : `faltam ${faltam} blocos concluídos com timer`
}

/**
 * Cartão "Seu ritmo real": razão tempo real ÷ estimado dos últimos 90 dias.
 * Carrega uma vez por montagem e não depende do período navegado na página.
 */
export function CartaoCalibracao() {
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' })

  useEffect(() => {
    let ativo = true
    consultarCalibracao()
      .then((dados) => {
        if (ativo) {
          setEstado({ tipo: 'pronto', dados })
        }
      })
      .catch((error: unknown) => {
        if (ativo) {
          setEstado({ tipo: 'erro', mensagem: mensagemErro(error) })
        }
      })
    return () => {
      ativo = false
    }
  }, [])

  return (
    <section
      className="historico-card historico-calibracao card"
      aria-labelledby="historico-calibracao-titulo"
    >
      <header>
        <h2 id="historico-calibracao-titulo">Seu ritmo real</h2>
        <p className="historico-periodo">Últimos 90 dias</p>
      </header>
      {estado.tipo === 'carregando' ? <Skeleton variant="historico-calibracao" /> : null}
      {estado.tipo === 'erro' ? <InlineAlert>{estado.mensagem}</InlineAlert> : null}
      {estado.tipo === 'pronto' ? <ConteudoCalibracao dados={estado.dados} /> : null}
    </section>
  )
}

function ConteudoCalibracao({ dados }: { dados: CalibracaoResponse }) {
  const { geral, minimoAmostras } = dados
  const frase = geral.suficiente ? fraseCalibracao(geral.razao) : null

  if (!frase) {
    const faltam = Math.max(1, minimoAmostras - geral.amostras)
    return (
      <p className="historico-vazio" role="status">
        Ainda sem dados suficientes — {textoFaltam(faltam)}.
      </p>
    )
  }

  const tags = (dados.porTag ?? []).filter((r) => r.suficiente).slice(0, MAX_RECORTES)
  const projetos = (dados.porProjeto ?? []).filter((r) => r.suficiente).slice(0, MAX_RECORTES)

  return (
    <div className="historico-calibracao__conteudo">
      <p className="historico-calibracao__frase">{frase}</p>
      <p className="historico-periodo">{geral.amostras} blocos</p>
      {tags.length > 0 ? (
        <div className="historico-calibracao__grupo">
          <h3>Por tag</h3>
          <ul className="historico-calibracao__recortes" aria-label="Ritmo por tag">
            {tags.map((r) => (
              <li key={r.tagId}>
                <TagChip tag={{ id: r.tagId, nome: `#${r.nome}`, cor: r.cor }} />
                <span className="historico-calibracao__razao">{formatarRazao(r.razao)}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {projetos.length > 0 ? (
        <div className="historico-calibracao__grupo">
          <h3>Por projeto</h3>
          <ul className="historico-calibracao__recortes" aria-label="Ritmo por projeto">
            {projetos.map((r) => (
              <li key={r.projetoId}>
                <span className="historico-calibracao__projeto">{r.nome}</span>
                <span className="historico-calibracao__razao">{formatarRazao(r.razao)}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}
