import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { Drawer } from '../feedback/Drawer'
import { InlineAlert } from '../feedback/InlineAlert'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import {
  atualizarTarefa,
  buscarTarefa,
  criarTarefa,
  excluirTarefa,
  restaurarTarefa,
  type TarefaResponse,
} from './api/tarefaApi'
import { datetimeLocalParaInstant, instantParaDatetimeLocal } from './datetime'
import { TarefaForm } from './TarefaForm'
import {
  validarTarefaForm,
  type Prioridade,
  type StatusTarefa,
  type TarefaFormValues,
} from './validation/tarefaForm'

export type TarefasOutletContext = {
  projetosAtivos: ProjetoResponse[]
  /** Tarefa alterada pelo drawer (salvar, excluir, restaurar, desvincular). */
  aoAtualizar: (tarefa: TarefaResponse) => void
  aoCriar: (tarefa: TarefaResponse) => void
}

/** `location.state` aceito pelas rotas do drawer. */
export type TarefaDrawerState = {
  /** Aberto a partir da lista: fechar volta no histórico em vez de empilhar `/tarefas`. */
  daLista?: boolean
  /** Título pré-preenchido (ex.: vindo do quick-add). */
  titulo?: string
}

type Snapshot = { values: TarefaFormValues; projetoId: string }

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
  }))
  const [values, setValues] = useState<TarefaFormValues>(inicial.values)
  const [projetoId, setProjetoId] = useState('')
  const [projetoNomeVinculado, setProjetoNomeVinculado] = useState<string | null>(null)
  const [tarefa, setTarefa] = useState<TarefaResponse | null>(null)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [desvinculando, setDesvinculando] = useState(false)
  const [excluindo, setExcluindo] = useState(false)
  const [restaurando, setRestaurando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aberto, setAberto] = useState(true)
  const confirmandoRef = useRef(false)

  const deletada = tarefa?.deletada ?? false
  const alterado =
    JSON.stringify(values) !== JSON.stringify(inicial.values) || projetoId !== inicial.projetoId

  function aplicarTarefa(carregada: TarefaResponse) {
    const valores = valoresDaTarefa(carregada)
    setTarefa(carregada)
    setValues(valores)
    setProjetoId(carregada.projetoId ?? '')
    setProjetoNomeVinculado(carregada.projetoNome ?? null)
    setInicial({ values: valores, projetoId: carregada.projetoId ?? '' })
  }

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

  async function pedirFechar() {
    if (!aberto || confirmandoRef.current) {
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
    setAberto(false)
  }

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

    const corpo = {
      titulo: values.titulo.trim(),
      descricao: values.descricao.trim(),
      prioridade: values.prioridade as Prioridade,
      prazo: datetimeLocalParaInstant(values.prazo),
      duracaoMinutos: values.duracaoMinutos as number,
    }

    setEnviando(true)
    try {
      if (modo === 'criar') {
        const criada = await criarTarefa({ ...corpo, projetoId: projetoId || null })
        toast.success('Tarefa criada', {
          action: { label: 'Abrir', onClick: () => navigate(`/tarefas/${criada.id}`) },
        })
        aoCriar(criada)
      } else if (id) {
        const atualizada = await atualizarTarefa(id, {
          ...corpo,
          status: values.status as StatusTarefa,
          ...(projetoId ? { projetoId } : {}),
        })
        toast.success('Tarefa salva')
        aoAtualizar(atualizada)
      }
      setAberto(false)
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

  async function handleExcluir() {
    if (!tarefa || deletada) {
      return
    }
    const confirmado = await confirm({
      title: 'Excluir tarefa?',
      message: 'A tarefa será excluída. Você poderá restaurá-la nesta tela.',
      confirmLabel: 'Excluir',
    })
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

  const rodape = carregando ? null : (
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
            className="btn btn--ghost btn--danger tarefa-drawer__excluir"
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

  return (
    <>
      <Drawer
        open={aberto}
        title={modo === 'criar' ? 'Nova tarefa' : 'Editar tarefa'}
        onClose={() => void pedirFechar()}
        onExited={voltarParaLista}
        busy={carregando}
        footer={rodape}
      >
        {carregando ? (
          <p role="status">Carregando tarefa…</p>
        ) : (
          <>
            <p className="tarefa-drawer__subtitulo">
              {modo === 'criar'
                ? 'O status inicia como pendente automaticamente.'
                : 'Status e prazo seguem as regras do servidor.'}
            </p>
            {deletada ? (
              <p className="tarefa-deletada-aviso" role="status">
                Esta tarefa está excluída. Restaure para editar novamente.
              </p>
            ) : null}
            {modo === 'editar' && !tarefa ? null : (
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
                onDesvincular={() => void handleDesvincular()}
                desvinculando={desvinculando}
              />
            )}
            {modo === 'editar' && !tarefa && erro ? <InlineAlert>{erro}</InlineAlert> : null}
          </>
        )}
      </Drawer>
      {confirmDialog}
    </>
  )
}
