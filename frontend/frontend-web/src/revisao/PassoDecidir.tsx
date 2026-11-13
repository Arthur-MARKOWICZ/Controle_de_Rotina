import { useEffect, useState } from 'react'

import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { excluirDecidir, listarDecidir, type DecidirResponse } from '../decidir/api/decidirApi'
import { PromoverDecidirForm } from '../decidir/PromoverDecidirForm'
import { mensagemErro } from './mensagemErro'

type Props = {
  onTratou: () => void
}

function vencido(decidirAte: string | null): boolean {
  if (!decidirAte) return false
  const instante = Date.parse(decidirAte)
  return Number.isFinite(instante) && instante < Date.now()
}

/** Passo 3: esvaziar a fila Decidir promovendo ou descartando cada item aberto. */
export function PassoDecidir({ onTratou }: Props) {
  const { confirm, dialog } = useConfirmDialog()
  const [itens, setItens] = useState<DecidirResponse[] | null>(null)
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<string | null>(null)
  const [recarregar, setRecarregar] = useState(0)
  const [promovendo, setPromovendo] = useState<DecidirResponse | null>(null)

  useEffect(() => {
    let ativo = true
    setErroCarga(null)
    listarDecidir({ promovidos: false, page: 0, size: 50 })
      .then((page) => {
        if (ativo) setItens(page.content)
      })
      .catch((error: unknown) => {
        if (ativo) setErroCarga(mensagemErro(error, 'Não foi possível carregar a fila Decidir.'))
      })
    return () => {
      ativo = false
    }
  }, [recarregar])

  async function descartar(item: DecidirResponse) {
    const ok = await confirm({
      title: 'Descartar item?',
      message: `“${item.titulo}” será removido definitivamente da fila Decidir.`,
      confirmLabel: 'Descartar',
    })
    if (!ok) return
    setErroAcao(null)
    try {
      await excluirDecidir(item.id)
      onTratou()
      setRecarregar((n) => n + 1)
    } catch (error) {
      setErroAcao(mensagemErro(error, 'Não foi possível descartar o item.'))
    }
  }

  if (erroCarga) {
    return (
      <div className="revisao-passo__estado">
        <InlineAlert>{erroCarga}</InlineAlert>
        <button type="button" className="btn btn--secondary" onClick={() => setRecarregar((n) => n + 1)}>
          Tentar de novo
        </button>
      </div>
    )
  }

  if (!itens) {
    return <Skeleton variant="tarefas-lista" />
  }

  return (
    <>
      {dialog}
      {erroAcao ? <InlineAlert>{erroAcao}</InlineAlert> : null}
      {itens.length === 0 ? (
        <p className="revisao-passo__vazio" role="status">
          Fila Decidir vazia. Nada pendente de decisão.
        </p>
      ) : (
        <ul className="revisao-lista">
          {itens.map((item) => {
            const atrasado = vencido(item.decidirAte)
            return (
              <li
                key={item.id}
                className={atrasado ? 'revisao-item decidir-card--vencido' : 'revisao-item'}
                data-vencido={atrasado ? 'true' : undefined}
              >
                <span className="revisao-item__titulo">{item.titulo}</span>
                {atrasado ? <span className="decidir-card__aviso">Prazo de decisão vencido</span> : null}
                <div className="revisao-item__acoes">
                  <button type="button" className="btn btn--primary" onClick={() => setPromovendo(item)}>
                    Promover
                  </button>
                  <button type="button" className="btn btn--secondary" onClick={() => void descartar(item)}>
                    Descartar
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {promovendo ? (
        <PromoverDecidirForm
          item={promovendo}
          onPromovido={() => {
            setPromovendo(null)
            onTratou()
            setRecarregar((n) => n + 1)
          }}
          onCancelar={() => setPromovendo(null)}
        />
      ) : null}
    </>
  )
}
