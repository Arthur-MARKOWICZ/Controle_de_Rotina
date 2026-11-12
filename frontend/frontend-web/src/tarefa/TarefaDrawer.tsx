import { CaretLeftIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { InlineAlert } from '../feedback/InlineAlert'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import { EsperaSection } from '../pendencia/EsperaSection'
import type { CriarEsperaRequest, EsperaResumo } from '../pendencia/api/espera'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { TagResumo } from '../tag/api/tagApi'
import {
  atualizarTarefa,
  buscarTarefa,
  criarEsperaTarefa,
  criarTarefa,
  excluirEsperaTarefa,
  excluirTarefa,
  restaurarTarefa,
  type TarefaResponse,
} from './api/tarefaApi'
import { datetimeLocalParaInstant, instantParaDatetimeLocal } from './datetime'
import { confirmacaoCascataPai } from './cascataConfirmacao'
import { TarefaForm } from './TarefaForm'
import { TarefaSubtarefasSection } from './TarefaSubtarefasSection'
import {
  validarTarefaForm,
  type Prioridade,
  type StatusTarefa,
  type TarefaFormValues,
} from './validation/tarefaForm'

type DrawerView = 'meta' | 'nota'

export type TarefasOutletContext = {
  projetosAtivos: ProjetoResponse[]
  /** Tarefa alterada na ficha (salvar, excluir, restaurar, desvincular). */
  aoAtualizar: (tarefa: TarefaResponse) => void
  aoCriar: (tarefa: TarefaResponse) => void
}

/** `location.state` aceito pelas rotas da ficha. */
export type TarefaDrawerState = {
  /** Aberto a partir da lista: voltar usa o histórico em vez de empilhar `/tarefas`. */
  daLista?: boolean
  /** Título pré-preenchido (ex.: vindo do quick-add). */
  titulo?: string
}

type Snapshot = { values: TarefaFormValues; projetoId: string; tagIds: string[] }

const FORM_ID = 'tarefa-drawer-form'

const VAZIO: TarefaFormValues = {
  titulo: '',
  descricao: '',
  prioridade: '',
  prazo: '',
  duracaoMinutos: '',
  status: '',
}

function valoresDaTarefa(tarefa: TarefaResponse): TarefaFormValues {
  return {
    titulo: tarefa.titulo,
    descricao: tarefa.descricao,
    prioridade: tarefa.prioridade,
    prazo: instantParaDatetimeLocal(tarefa.prazo),
    duracaoMinutos: tarefa.duracaoMinutos,
    status: tarefa.status,
  }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function TarefaDrawer() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()
  const location = useLocation()
  const estado = (location.state ?? {}) as TarefaDrawerState
  const { projetosAtivos, aoAtualizar, aoCriar } = useOutletContext<TarefasOutletContext>()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [inicial, setInicial] = useState<Snapshot>(() => ({
    values: { ...VAZIO, titulo: estado.titulo ?? '' },
    projetoId: '',
    tagIds: [],
  }))
  const [values, setValues] = useState<TarefaFormValues>(inicial.values)
  const [projetoId, setProjetoId] = useState('')
  const [projetoNomeVinculado, setProjetoNomeVinculado] = useState<string | null>(null)
  const [tagIds, setTagIds] = useState<string[]>([])
  const [tagsSelecionadas, setTagsSelecionadas] = useState<TagResumo[]>([])
  const [tarefa, setTarefa] = useState<TarefaResponse | null>(null)
  const [esperas, setEsperas] = useState<EsperaResumo[]>([])
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [desvinculando, setDesvinculando] = useState(false)
  const [excluindo, setExcluindo] = useState(false)
  const [restaurando, setRestaurando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [view, setView] = useState<DrawerView>('meta')
  const confirmandoRef = useRef(false)
  const notaTextareaRef = useRef<HTMLTextAreaElement>(null)
  const descricaoEntradaRef = useRef<HTMLButtonElement>(null)
  const focarDescricaoAoVoltarRef = useRef(false)

  const deletada = tarefa?.deletada ?? false
  const naNota = view === 'nota'
  const alterado =
    JSON.stringify(values) !== JSON.stringify(inicial.values) ||
    projetoId !== inicial.projetoId ||
    JSON.stringify(tagIds) !== JSON.stringify(inicial.tagIds)

  const voltarAoMeta = useCallback(() => {
    focarDescricaoAoVoltarRef.current = true
    setView('meta')
  }, [])

  function abrirNota() {
    if (deletada) {
      return
    }
    setView('nota')
  }

  function aplicarTarefa(carregada: TarefaResponse) {
    const valores = valoresDaTarefa(carregada)
    setTarefa(carregada)
    setValues(valores)
    setProjetoId(carregada.projetoId ?? '')
    setProjetoNomeVinculado(carregada.projetoNome ?? null)
    const ids = carregada.tags?.map((tag) => tag.id) ?? []
    setTagIds(ids)
    setTagsSelecionadas(carregada.tags ?? [])
    setEsperas(carregada.esperas ?? [])
    setInicial({ values: valores, projetoId: carregada.projetoId ?? '', tagIds: ids })
  }

  useEffect(() => {
    setView('meta')
    focarDescricaoAoVoltarRef.current = false
  }, [id, modo])

  useEffect(() => {
    if (modo !== 'editar' || !id) {
      return
    }
    const tarefaId = id
    let ativo = true
    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const carregada = await buscarTarefa(tarefaId)
        if (ativo) {
          aplicarTarefa(carregada)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar a tarefa.')
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
  }, [id, modo])

  const voltarParaLista = useCallback(() => {
    if (estado.daLista) {
      navigate(-1)
      return
    }
    navigate({ pathname: '/tarefas', search: location.search }, { replace: true })
  }, [estado.daLista, location.search, navigate])

  useEffect(() => {
    if (view !== 'nota') {
      return
    }
    notaTextareaRef.current?.focus()
  }, [view])

  useEffect(() => {
    if (view !== 'meta' || !focarDescricaoAoVoltarRef.current) {
      return
    }
    focarDescricaoAoVoltarRef.current = false
    descricaoEntradaRef.current?.focus()
  }, [view])

  const pedirFechar = useCallback(async () => {
    if (confirmandoRef.current) {
      return
    }
    if (naNota) {
      voltarAoMeta()
      return
    }
    if (alterado) {
      confirmandoRef.current = true
      const descartar = await confirm({
        title: 'Descartar alterações?',
        message: 'As alterações que você fez nesta tarefa serão perdidas.',
        confirmLabel: 'Descartar',
        cancelLabel: 'Continuar editando',
      })
      confirmandoRef.current = false
      if (!descartar) {
        return
      }
    }
    voltarParaLista()
  }, [alterado, confirm, naNota, voltarAoMeta, voltarParaLista])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key !== 'Escape') {
        return
      }
      if (document.querySelector('dialog[open]')) {
        return
      }
      event.preventDefault()
      void pedirFechar()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [pedirFechar])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    if (deletada) {
      setErro('Restaure a tarefa antes de editar.')
      return
    }

    const validacao = validarTarefaForm(values, { modo })
    if (validacao) {
      setErro(validacao)
      return
    }

    if (modo === 'editar' && tarefa?.temFilhos) {
      const novoStatus = values.status as StatusTarefa
      if (novoStatus === 'FINALIZADA' && tarefa.status !== 'FINALIZADA') {
        const confirmado = await confirm(confirmacaoCascataPai('finalizar'))
        if (!confirmado) {
          return
        }
      } else if (novoStatus === 'CANCELADA' && tarefa.status !== 'CANCELADA') {
        const confirmado = await confirm(confirmacaoCascataPai('cancelar'))
        if (!confirmado) {
          return
        }
      }
    }

    const corpo = {
      titulo: values.titulo.trim(),
      descricao: values.descricao.trim(),
      prioridade: values.prioridade as Prioridade,
      prazo: datetimeLocalParaInstant(values.prazo),
      ...(tarefa?.temFilhos ? {} : { duracaoMinutos: values.duracaoMinutos as number }),
    }

    setEnviando(true)
    try {
      if (modo === 'criar') {
        const criada = await criarTarefa({
          ...corpo,
          projetoId: projetoId || null,
          tagIds,
        })
        toast.success('Tarefa criada', {
          action: { label: 'Abrir', onClick: () => navigate(`/tarefas/${criada.id}`) },
        })
        aoCriar(criada)
      } else if (id) {
        const atualizada = await atualizarTarefa(id, {
          ...corpo,
          status: values.status as StatusTarefa,
          ...(projetoId ? { projetoId } : {}),
          tagIds,
        })
        toast.success('Tarefa salva')
        aoAtualizar(atualizada)
      }
      voltarParaLista()
    } catch (error) {
      setErro(
        mensagemErro(
          error,
          modo === 'criar'
            ? 'Não foi possível criar a tarefa. Tente novamente.'
            : 'Não foi possível atualizar a tarefa. Tente novamente.',
        ),
      )
    } finally {
      setEnviando(false)
    }
  }

  async function handleDesvincular() {
    if (!id || !projetoId || deletada) {
      return
    }
    setErro(null)
    setDesvinculando(true)
    try {
      const atualizada = await atualizarTarefa(id, { projetoId: null })
      setTarefa(atualizada)
      setProjetoId(atualizada.projetoId ?? '')
      setProjetoNomeVinculado(atualizada.projetoNome ?? null)
      setInicial((atual) => ({ ...atual, projetoId: atualizada.projetoId ?? '' }))
      aoAtualizar(atualizada)
      toast.success('Projeto desvinculado')
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível desvincular o projeto. Tente novamente.'))
    } finally {
      setDesvinculando(false)
    }
  }

  async function recarregarEsperas(tarefaId: string) {
    try {
      const atualizada = await buscarTarefa(tarefaId)
      setTarefa(atualizada)
      setEsperas(atualizada.esperas ?? [])
      aoAtualizar(atualizada)
    } catch {
      /* a lista local já reflete a operação; o bloqueio atualiza no próximo load */
    }
  }

  async function handleCriarEspera(body: CriarEsperaRequest) {
    if (!id) {
      return
    }
    const criada = await criarEsperaTarefa(id, body)
    setEsperas((atual) => [...atual, criada])
    await recarregarEsperas(id)
  }

  async function handleRemoverEspera(esperaId: string) {
    if (!id) {
      return
    }
    await excluirEsperaTarefa(id, esperaId)
    setEsperas((atual) => atual.filter((espera) => espera.id !== esperaId))
    await recarregarEsperas(id)
  }

  async function handleExcluir() {
    if (!tarefa || deletada) {
      return
    }
    const confirmado = await confirm(
      tarefa.temFilhos
        ? confirmacaoCascataPai('excluir')
        : {
            title: 'Excluir tarefa?',
            message: 'A tarefa será excluída. Você poderá restaurá-la nesta tela.',
            confirmLabel: 'Excluir',
          },
    )
    if (!confirmado) {
      return
    }
    setErro(null)
    setExcluindo(true)
    try {
      await excluirTarefa(tarefa.id)
      const excluida = { ...tarefa, deletada: true, deletedAt: new Date().toISOString() }
      setTarefa(excluida)
      aoAtualizar(excluida)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível excluir a tarefa. Tente novamente.'))
    } finally {
      setExcluindo(false)
    }
  }

  async function handleRestaurar() {
    if (!tarefa || !deletada) {
      return
    }
    setErro(null)
    setRestaurando(true)
    try {
      const restaurada = await restaurarTarefa(tarefa.id)
      aplicarTarefa(restaurada)
      aoAtualizar(restaurada)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível restaurar a tarefa. Tente novamente.'))
    } finally {
      setRestaurando(false)
    }
  }

  const rodapeMeta = carregando ? null : (
    <>
      {modo === 'editar' && tarefa ? (
        deletada ? (
          <button
            type="button"
            className="btn btn--primary"
            disabled={restaurando}
            onClick={() => void handleRestaurar()}
          >
            {restaurando ? 'Restaurando…' : 'Restaurar tarefa'}
          </button>
        ) : (
          <button
            type="button"
            className="btn btn--ghost btn--danger tarefa-ficha__excluir"
            disabled={excluindo}
            onClick={() => void handleExcluir()}
          >
            {excluindo ? 'Excluindo…' : 'Excluir tarefa'}
          </button>
        )
      ) : null}
      <button type="button" className="btn btn--secondary" onClick={() => void pedirFechar()}>
        Cancelar
      </button>
      {!deletada && (modo === 'criar' || tarefa) ? (
        <button
          type="submit"
          form={FORM_ID}
          className="btn btn--primary"
          disabled={enviando}
          aria-busy={enviando}
        >
          {enviando ? 'Salvando…' : modo === 'criar' ? 'Criar tarefa' : 'Salvar'}
        </button>
      ) : null}
    </>
  )

  const rodapeNota = (
    <button type="button" className="btn btn--primary" onClick={voltarAoMeta}>
      Pronto
    </button>
  )

  const tituloPagina = naNota ? 'Descrição' : modo === 'criar' ? 'Nova tarefa' : 'Editar tarefa'
  const mostrarSubtarefas = modo === 'editar' && tarefa != null && !tarefa.parentId && !deletada
  const classeFicha = ['tarefa-ficha', naNota ? 'tarefa-ficha--nota' : '']
    .filter(Boolean)
    .join(' ')

  return (
    <>
      <section className={classeFicha} aria-labelledby="tarefa-ficha-titulo" aria-busy={carregando || undefined}>
        <header className="tarefa-ficha__header">
          <div className="tarefa-ficha__heading">
            <button type="button" className="btn btn--ghost" onClick={() => void pedirFechar()}>
              <CaretLeftIcon aria-hidden />
              Voltar
            </button>
            <div>
              <h1 id="tarefa-ficha-titulo" className="tarefa-ficha__titulo">
                {tituloPagina}
              </h1>
              {naNota || carregando ? null : (
                <p className="tarefa-ficha__subtitulo">
                  {modo === 'criar'
                    ? 'O status inicia como pendente automaticamente.'
                    : 'Status e prazo seguem as regras do servidor.'}
                </p>
              )}
            </div>
          </div>
        </header>

        {carregando ? (
          <p role="status">Carregando tarefa…</p>
        ) : naNota ? (
          <div className="tarefa-nota">
            <label className="visually-hidden" htmlFor="tarefa-nota-descricao">
              Descrição
            </label>
            <textarea
              ref={notaTextareaRef}
              id="tarefa-nota-descricao"
              className="tarefa-nota__textarea"
              maxLength={2000}
              value={values.descricao}
              onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
              placeholder="Escreva a descrição da tarefa…"
            />
            <p className="tarefa-nota__contador" aria-live="polite">
              {values.descricao.length}/2000
            </p>
          </div>
        ) : (
          <>
            {deletada ? (
              <p className="tarefa-deletada-aviso" role="status">
                Esta tarefa está excluída. Restaure para editar novamente.
              </p>
            ) : null}
            {modo === 'editar' && !tarefa ? null : (
              <div className="tarefa-ficha__corpo">
                <div className="tarefa-ficha__principal">
                  <TarefaForm
                    formId={FORM_ID}
                    modo={modo}
                    values={values}
                    onValuesChange={setValues}
                    projetoId={projetoId}
                    projetoNomeVinculado={projetoNomeVinculado}
                    onProjetoChange={(novoId, nome) => {
                      setProjetoId(novoId)
                      setProjetoNomeVinculado(nome)
                    }}
                    projetosAtivos={projetosAtivos}
                    disabled={deletada}
                    erro={erro}
                    onSubmit={(e) => void handleSubmit(e)}
                    onAbrirDescricao={abrirNota}
                    descricaoEntradaRef={descricaoEntradaRef}
                    onDesvincular={() => void handleDesvincular()}
                    desvinculando={desvinculando}
                    tagIds={tagIds}
                    tagsSelecionadas={tagsSelecionadas}
                    onTagsChange={(ids, tags) => {
                      setTagIds(ids)
                      setTagsSelecionadas(tags)
                    }}
                    duracaoSomenteLeitura={Boolean(tarefa?.temFilhos)}
                  />
                </div>
                {mostrarSubtarefas && tarefa ? (
                  <TarefaSubtarefasSection
                    parent={tarefa}
                    onPaiAtualizado={(atualizada) => {
                      setTarefa(atualizada)
                      setValues((atual) => ({ ...atual, duracaoMinutos: atualizada.duracaoMinutos }))
                      setInicial((atual) => ({
                        ...atual,
                        values: { ...atual.values, duracaoMinutos: atualizada.duracaoMinutos },
                      }))
                      aoAtualizar(atualizada)
                    }}
                  />
                ) : null}
                {modo === 'editar' && tarefa ? (
                  <EsperaSection
                    origemTipo="TAREFA"
                    origemId={tarefa.id}
                    esperas={esperas}
                    somenteLeitura={deletada}
                    onCriar={handleCriarEspera}
                    onRemover={handleRemoverEspera}
                  />
                ) : null}
              </div>
            )}
            {modo === 'editar' && !tarefa && erro ? <InlineAlert>{erro}</InlineAlert> : null}
          </>
        )}

        {carregando ? null : (
          <div className="tarefa-ficha__rodape">{naNota ? rodapeNota : rodapeMeta}</div>
        )}
      </section>
      {confirmDialog}
    </>
  )
}
