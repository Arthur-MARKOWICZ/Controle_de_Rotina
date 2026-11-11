import { CheckIcon, PlusIcon, ScalesIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { Drawer } from '../../feedback/Drawer'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { PageHeader } from '../../layout/PageHeader'
import { listarProjetos, type ProjetoResponse } from '../../projeto/api/projetoApi'
import { TagChipList } from '../../tag/TagChip'
import { TagFiltroPopover } from '../../tag/TagFiltroPopover'
import { lerTagIdsDaUrl, escreverTagIdsNaUrl } from '../../tag/listaTagUrl'
import { ordenarPorTagMatch } from '../../tag/tagMatch'
import type { Prioridade } from '../../tarefa/validation/tarefaForm'
import {
  excluirDecidir,
  listarDecidir,
  promoverDecidir,
  type DecidirResponse,
  type PageDecidir,
  type PromoverDecidirRequest,
} from '../api/decidirApi'
import type { DecidirOutletContext } from '../DecidirDrawer'
import { InlineAddDecidir, type InlineAddDecidirHandle } from '../InlineAddDecidir'

type Visao = 'abertos' | 'promovidos'

const DRAWER_STATE = { daLista: true }

function lerVisaoDaUrl(params: URLSearchParams): Visao {
  return params.get('promovidos') === 'true' ? 'promovidos' : 'abertos'
}

function decidirAteVencido(decidirAte: string | null): boolean {
  if (!decidirAte) {
    return false
  }
  const instant = Date.parse(decidirAte)
  return Number.isFinite(instant) && instant < Date.now()
}

function formatarDecidirAte(decidirAte: string): string {
  try {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(decidirAte))
  } catch {
    return decidirAte
  }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

function inserirNaPagina(pagina: PageDecidir, item: DecidirResponse): PageDecidir {
  if (pagina.content.some((row) => row.id === item.id)) {
    return pagina
  }
  return {
    ...pagina,
    content: [item, ...pagina.content],
    totalElements: pagina.totalElements + 1,
  }
}

function substituirNaPagina(pagina: PageDecidir, item: DecidirResponse): PageDecidir {
  return {
    ...pagina,
    content: pagina.content.map((row) => (row.id === item.id ? item : row)),
  }
}

export function DecidirPage() {
  const toast = useToast()
  const location = useLocation()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const [searchParams, setSearchParams] = useSearchParams()
  const visao = lerVisaoDaUrl(searchParams)
  const tagIds = useMemo(() => lerTagIdsDaUrl(searchParams), [searchParams])
  const inlineAddRef = useRef<InlineAddDecidirHandle>(null)

  const [resultado, setResultado] = useState<PageDecidir | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)
  const [projetosAtivos, setProjetosAtivos] = useState<ProjetoResponse[]>([])

  const [promovendo, setPromovendo] = useState<DecidirResponse | null>(null)
  const [promoverDuracao, setPromoverDuracao] = useState('')
  const [promoverPrioridade, setPromoverPrioridade] = useState<Prioridade | ''>('')
  const [promoverPrazo, setPromoverPrazo] = useState('')
  const [enviandoPromover, setEnviandoPromover] = useState(false)

  function escreverUrl(proximo: { visao: Visao; tagIds: string[] }) {
    let params = new URLSearchParams()
    if (proximo.visao === 'promovidos') {
      params.set('promovidos', 'true')
    }
    params = escreverTagIdsNaUrl(params, proximo.tagIds)
    setSearchParams(params, { replace: true })
  }

  useEffect(() => {
    let ativo = true
    async function carregarProjetos() {
      try {
        const page = await listarProjetos({ status: 'ATIVO', page: 0, size: 100 })
        if (ativo) {
          setProjetosAtivos(page.content)
        }
      } catch {
        if (ativo) {
          setProjetosAtivos([])
        }
      }
    }
    void carregarProjetos()
    return () => {
      ativo = false
    }
  }, [])

  useEffect(() => {
    let ativo = true
    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await listarDecidir({
          promovidos: visao === 'promovidos',
          page: 0,
          size: 50,
          tagIds: tagIds.length ? tagIds : undefined,
        })
        if (ativo) {
          setResultado(data)
        }
      } catch (error) {
        if (ativo) {
          setErro(mensagemErro(error, 'Não foi possível carregar a fila Decidir.'))
          setResultado(null)
        }
      } finally {
        if (ativo) {
          setCarregando(false)
        }
      }
    }
    void carregar()
    return () => {
      ativo = false
    }
  }, [visao, tagIds, recarregarEm])

  const handleCriado = useCallback(
    (item: DecidirResponse) => {
      if (visao === 'abertos') {
        setResultado((atual) => (atual ? inserirNaPagina(atual, item) : atual))
      } else {
        escreverUrl({ visao: 'abertos', tagIds })
      }
    },
    [tagIds, visao],
  )

  async function handleDescartar(item: DecidirResponse) {
    const confirmado = await confirm({
      title: 'Descartar item?',
      message: `“${item.titulo}” será removido definitivamente da fila Decidir.`,
      confirmLabel: 'Descartar',
    })
    if (!confirmado) {
      return
    }
    setErro(null)
    try {
      await excluirDecidir(item.id)
      setRecarregarEm((n) => n + 1)
      toast.success('Item descartado')
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível descartar o item.'))
    }
  }

  function abrirPromover(item: DecidirResponse) {
    setPromovendo(item)
    setPromoverDuracao('')
    setPromoverPrioridade('')
    setPromoverPrazo('')
  }

  async function confirmarPromover(event: FormEvent) {
    event.preventDefault()
    if (!promovendo) {
      return
    }
    setEnviandoPromover(true)
    setErro(null)
    try {
      const body: PromoverDecidirRequest = {}
      if (promoverDuracao.trim()) {
        body.duracaoMinutos = Number(promoverDuracao)
      }
      if (promoverPrioridade) {
        body.prioridade = promoverPrioridade
      }
      if (promoverPrazo) {
        body.prazo = new Date(promoverPrazo).toISOString()
      }
      await promoverDecidir(promovendo.id, body)
      setPromovendo(null)
      setRecarregarEm((n) => n + 1)
      toast.success('Promovido a tarefa')
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível promover o item.'))
    } finally {
      setEnviandoPromover(false)
    }
  }

  const contextoDrawer: DecidirOutletContext = {
    projetosAtivos,
    aoAtualizar: (item) => {
      setResultado((atual) => (atual ? substituirNaPagina(atual, item) : atual))
      setRecarregarEm((n) => n + 1)
    },
    aoCriar: handleCriado,
  }

  const listaVazia = !carregando && resultado != null && resultado.content.length === 0
  const emptyPrimeira = listaVazia && visao === 'abertos' && tagIds.length === 0
  const itens = resultado ? ordenarPorTagMatch(resultado.content, tagIds) : []

  return (
    <main className="decidir-page">
      {confirmDialog}
      <PageHeader
        title="Decidir"
        description="Capture ideias ambíguas e decida depois: promover a tarefa ou descartar."
      />

      <div className="visao-chips" role="group" aria-label="Visão da lista">
        <button
          type="button"
          className={visao === 'abertos' ? 'visao-chip visao-chip--ativo' : 'visao-chip'}
          aria-pressed={visao === 'abertos'}
          onClick={() => escreverUrl({ visao: 'abertos', tagIds })}
        >
          {visao === 'abertos' ? <CheckIcon aria-hidden /> : null}
          Abertos
        </button>
        <button
          type="button"
          className={visao === 'promovidos' ? 'visao-chip visao-chip--ativo' : 'visao-chip'}
          aria-pressed={visao === 'promovidos'}
          onClick={() => escreverUrl({ visao: 'promovidos', tagIds })}
        >
          {visao === 'promovidos' ? <CheckIcon aria-hidden /> : null}
          Já decididos
        </button>
      </div>

      <div className="decidir-filtros">
        <TagFiltroPopover tagIds={tagIds} onAplicar={(ids) => escreverUrl({ visao, tagIds: ids })} />
      </div>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}
      {carregando ? <Skeleton variant="tarefas-lista" /> : null}

      {visao === 'abertos' ? (
        <InlineAddDecidir ref={inlineAddRef} onCriado={handleCriado} />
      ) : null}

      {!carregando && resultado ? (
        <>
          <p className="decidir-meta">
            {resultado.totalElements} item{resultado.totalElements === 1 ? '' : 's'}
          </p>

          {listaVazia ? (
            <div className="decidir-empty" role="status">
              {emptyPrimeira ? (
                <>
                  <ScalesIcon className="decidir-empty__icone" aria-hidden />
                  <p className="decidir-empty__titulo">Nada para decidir</p>
                  <p>Capture um título para começar.</p>
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => inlineAddRef.current?.focus()}
                  >
                    <PlusIcon aria-hidden />
                    Novo item
                  </button>
                </>
              ) : visao === 'abertos' ? (
                <>
                  <p className="decidir-empty__titulo">Nada com estes filtros</p>
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => escreverUrl({ visao, tagIds: [] })}
                  >
                    Limpar filtros
                  </button>
                </>
              ) : (
                <>
                  <p>Nenhum item promovido ainda.</p>
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => escreverUrl({ visao: 'abertos', tagIds })}
                  >
                    Ver abertos
                  </button>
                </>
              )}
            </div>
          ) : (
            <ul className="decidir-cards">
              {itens.map((item) => {
                const vencido = visao === 'abertos' && decidirAteVencido(item.decidirAte)
                const projetoNome = projetosAtivos.find((p) => p.id === item.projetoId)?.nome
                return (
                  <li
                    key={item.id}
                    className={vencido ? 'decidir-card decidir-card--vencido' : 'decidir-card'}
                    data-vencido={vencido ? 'true' : undefined}
                  >
                    <div className="decidir-card__principal">
                      <Link
                        to={{ pathname: `/decidir/${item.id}`, search: location.search }}
                        state={DRAWER_STATE}
                        className="decidir-card__titulo"
                      >
                        {item.titulo}
                      </Link>
                      <div className="decidir-card__meta">
                        {item.decidirAte ? (
                          <span className={vencido ? 'decidir-card__aviso' : undefined}>
                            {vencido ? 'Vencido · ' : 'Decidir até '}
                            {formatarDecidirAte(item.decidirAte)}
                          </span>
                        ) : null}
                        {projetoNome ? <span>{projetoNome}</span> : null}
                        {item.tarefaId ? (
                          <Link to={`/tarefas/${item.tarefaId}`}>Ver tarefa</Link>
                        ) : null}
                        <TagChipList tags={item.tags} />
                      </div>
                    </div>
                    {visao === 'abertos' ? (
                      <div className="decidir-card__acoes">
                        <button
                          type="button"
                          className="btn btn--primary"
                          onClick={() => abrirPromover(item)}
                        >
                          Promover
                        </button>
                        <button
                          type="button"
                          className="btn btn--secondary"
                          onClick={() => void handleDescartar(item)}
                        >
                          Descartar
                        </button>
                      </div>
                    ) : null}
                  </li>
                )
              })}
            </ul>
          )}
        </>
      ) : null}

      {promovendo ? (
        <Drawer
          open
          onClose={() => {
            if (!enviandoPromover) {
              setPromovendo(null)
            }
          }}
          title={`Promover “${promovendo.titulo}”`}
        >
          <form className="decidir-promover" onSubmit={(e) => void confirmarPromover(e)}>
            <p>Cria uma tarefa com o título do item. Campos abaixo são opcionais.</p>
            <div className="field">
              <label htmlFor="promover-duracao">Duração (minutos)</label>
              <input
                id="promover-duracao"
                type="number"
                min={15}
                step={15}
                value={promoverDuracao}
                onChange={(e) => setPromoverDuracao(e.target.value)}
                disabled={enviandoPromover}
              />
            </div>
            <div className="field">
              <label htmlFor="promover-prioridade">Prioridade</label>
              <select
                id="promover-prioridade"
                value={promoverPrioridade}
                onChange={(e) => setPromoverPrioridade(e.target.value as Prioridade | '')}
                disabled={enviandoPromover}
              >
                <option value="">Padrão (média)</option>
                <option value="ALTA">Alta</option>
                <option value="MEDIA">Média</option>
                <option value="BAIXA">Baixa</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="promover-prazo">Prazo</label>
              <input
                id="promover-prazo"
                type="datetime-local"
                value={promoverPrazo}
                onChange={(e) => setPromoverPrazo(e.target.value)}
                disabled={enviandoPromover}
              />
            </div>
            <div className="decidir-promover__acoes">
              <button
                type="button"
                className="btn btn--secondary"
                disabled={enviandoPromover}
                onClick={() => setPromovendo(null)}
              >
                Cancelar
              </button>
              <button type="submit" className="btn btn--primary" disabled={enviandoPromover}>
                Promover
              </button>
            </div>
          </form>
        </Drawer>
      ) : null}

      <Outlet key={location.pathname} context={contextoDrawer} />
    </main>
  )
}
