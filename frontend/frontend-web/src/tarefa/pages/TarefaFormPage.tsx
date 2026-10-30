import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { PRIORIDADE_LABEL, STATUS_TAREFA_LABEL } from '../../home/labels'
import { listarProjetos, type ProjetoResponse } from '../../projeto/api/projetoApi'
import {
  atualizarTarefa,
  buscarTarefa,
  criarTarefa,
  excluirTarefa,
  restaurarTarefa,
} from '../api/tarefaApi'
import { datetimeLocalParaInstant, instantParaDatetimeLocal } from '../datetime'
import {
  validarTarefaForm,
  type Prioridade,
  type StatusTarefa,
  type TarefaFormValues,
} from '../validation/tarefaForm'

const PRIORIDADES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']
const STATUS: StatusTarefa[] = [
  'PENDENTE',
  'EM_PROGRESSO',
  'FINALIZADA',
  'ATRASADA',
  'CANCELADA',
]

const VAZIO: TarefaFormValues = {
  titulo: '',
  descricao: '',
  prioridade: '',
  prazo: '',
  duracaoMinutos: '',
  status: '',
}

export function TarefaFormPage() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [values, setValues] = useState<TarefaFormValues>(VAZIO)
  const [projetoId, setProjetoId] = useState('')
  const [projetoNomeVinculado, setProjetoNomeVinculado] = useState<string | null>(null)
  const [projetosAtivos, setProjetosAtivos] = useState<ProjetoResponse[]>([])
  const [deletada, setDeletada] = useState(false)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [desvinculando, setDesvinculando] = useState(false)
  const [excluindo, setExcluindo] = useState(false)
  const [restaurando, setRestaurando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

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
    if (modo !== 'editar' || !id) {
      return
    }

    const tarefaId = id
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const tarefa = await buscarTarefa(tarefaId)
        if (!ativo) {
          return
        }
        setValues({
          titulo: tarefa.titulo,
          descricao: tarefa.descricao,
          prioridade: tarefa.prioridade,
          prazo: instantParaDatetimeLocal(tarefa.prazo),
          duracaoMinutos: tarefa.duracaoMinutos,
          status: tarefa.status,
        })
        setProjetoId(tarefa.projetoId ?? '')
        setProjetoNomeVinculado(tarefa.projetoNome ?? null)
        setDeletada(tarefa.deletada)
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

    const prioridade = values.prioridade as Prioridade
    const prazo = datetimeLocalParaInstant(values.prazo)
    const duracaoMinutos = values.duracaoMinutos as number

    setEnviando(true)
    try {
      if (modo === 'criar') {
        await criarTarefa({
          titulo: values.titulo.trim(),
          descricao: values.descricao.trim(),
          prioridade,
          prazo,
          duracaoMinutos,
          projetoId: projetoId || null,
        })
      } else if (id) {
        await atualizarTarefa(id, {
          titulo: values.titulo.trim(),
          descricao: values.descricao.trim(),
          prioridade,
          prazo,
          duracaoMinutos,
          status: values.status as StatusTarefa,
          ...(projetoId ? { projetoId } : {}),
        })
      }
      navigate('/tarefas', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        setErro(error.message)
      } else {
        setErro(
          modo === 'criar'
            ? 'Não foi possível criar a tarefa. Tente novamente.'
            : 'Não foi possível atualizar a tarefa. Tente novamente.',
        )
      }
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
      setProjetoId(atualizada.projetoId ?? '')
      setProjetoNomeVinculado(atualizada.projetoNome ?? null)
      toast.success('Projeto desvinculado')
    } catch (error) {
      if (error instanceof ApiError) {
        setErro(error.message)
      } else {
        setErro('Não foi possível desvincular o projeto. Tente novamente.')
      }
    } finally {
      setDesvinculando(false)
    }
  }

  async function handleExcluir() {
    if (!id || deletada) {
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
      await excluirTarefa(id)
      setDeletada(true)
    } catch (error) {
      if (error instanceof ApiError) {
        setErro(error.message)
      } else {
        setErro('Não foi possível excluir a tarefa. Tente novamente.')
      }
    } finally {
      setExcluindo(false)
    }
  }

  async function handleRestaurar() {
    if (!id || !deletada) {
      return
    }
    setErro(null)
    setRestaurando(true)
    try {
      const restaurada = await restaurarTarefa(id)
      setDeletada(restaurada.deletada)
      setValues({
        titulo: restaurada.titulo,
        descricao: restaurada.descricao,
        prioridade: restaurada.prioridade,
        prazo: instantParaDatetimeLocal(restaurada.prazo),
        duracaoMinutos: restaurada.duracaoMinutos,
        status: restaurada.status,
      })
      setProjetoId(restaurada.projetoId ?? '')
      setProjetoNomeVinculado(restaurada.projetoNome ?? null)
    } catch (error) {
      if (error instanceof ApiError) {
        setErro(error.message)
      } else {
        setErro('Não foi possível restaurar a tarefa. Tente novamente.')
      }
    } finally {
      setRestaurando(false)
    }
  }

  const opcoesProjeto = [...projetosAtivos]
  if (
    projetoId &&
    projetoNomeVinculado &&
    !opcoesProjeto.some((p) => p.id === projetoId)
  ) {
    opcoesProjeto.unshift({
      id: projetoId,
      nome: `${projetoNomeVinculado} (arquivado)`,
      descricao: null,
      status: 'ARQUIVADO',
      createdAt: '',
      updatedAt: '',
    })
  }

  if (carregando) {
    return (
      <main className="auth-page">
        <section className="auth-panel card" aria-busy="true">
          <p>Carregando tarefa…</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-page">
      {confirmDialog}
      <section className="auth-panel card tarefa-form-panel">
        <h1>{modo === 'criar' ? 'Nova tarefa' : 'Editar tarefa'}</h1>
        <p className="auth-subtitle">
          {modo === 'criar'
            ? 'O status inicia como pendente automaticamente.'
            : 'Atualize os campos. Status e prazo seguem as regras do servidor.'}
        </p>

        {deletada ? (
          <p className="tarefa-deletada-aviso" role="status">
            Esta tarefa está excluída. Restaure para editar novamente.
          </p>
        ) : null}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="field">
            <label htmlFor="titulo">Título</label>
            <input
              id="titulo"
              name="titulo"
              type="text"
              maxLength={120}
              value={values.titulo}
              onChange={(e) => setValues((a) => ({ ...a, titulo: e.target.value }))}
              disabled={deletada}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="descricao">Descrição (opcional)</label>
            <textarea
              id="descricao"
              name="descricao"
              maxLength={2000}
              rows={4}
              value={values.descricao}
              onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
              disabled={deletada}
            />
          </div>

          <div className="field">
            <label htmlFor="prioridade">Prioridade</label>
            <select
              id="prioridade"
              name="prioridade"
              value={values.prioridade}
              onChange={(e) =>
                setValues((a) => ({ ...a, prioridade: e.target.value as Prioridade | '' }))
              }
              disabled={deletada}
              required
            >
              <option value="">Selecione</option>
              {PRIORIDADES.map((p) => (
                <option key={p} value={p}>
                  {PRIORIDADE_LABEL[p]}
                </option>
              ))}
            </select>
          </div>

          <div className="field">
            <label htmlFor="projetoId">Projeto (opcional)</label>
            <select
              id="projetoId"
              name="projetoId"
              value={projetoId}
              onChange={(e) => {
                const novoId = e.target.value
                setProjetoId(novoId)
                setProjetoNomeVinculado(
                  opcoesProjeto.find((p) => p.id === novoId)?.nome ?? null,
                )
              }}
              disabled={deletada}
            >
              {!projetoId ? <option value="">Nenhum</option> : null}
              {opcoesProjeto.map((projeto) => (
                <option
                  key={projeto.id}
                  value={projeto.id}
                  disabled={projeto.status === 'ARQUIVADO'}
                >
                  {projeto.nome}
                </option>
              ))}
            </select>
            {modo === 'editar' && projetoId ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm tarefa-desvincular"
                disabled={deletada || desvinculando}
                onClick={() => void handleDesvincular()}
              >
                {desvinculando ? 'Desvinculando…' : 'Desvincular'}
              </button>
            ) : null}
          </div>

          <div className="field">
            <label htmlFor="prazo">Prazo (opcional)</label>
            <input
              id="prazo"
              name="prazo"
              type="datetime-local"
              value={values.prazo}
              onChange={(e) => setValues((a) => ({ ...a, prazo: e.target.value }))}
              disabled={deletada}
            />
          </div>

          <div className="field">
            <label htmlFor="duracaoMinutos">Duração (minutos)</label>
            <input
              id="duracaoMinutos"
              name="duracaoMinutos"
              type="number"
              min={15}
              step={15}
              value={values.duracaoMinutos}
              onChange={(e) =>
                setValues((a) => ({
                  ...a,
                  duracaoMinutos: e.target.value === '' ? '' : Number(e.target.value),
                }))
              }
              disabled={deletada}
              required
            />
          </div>

          {modo === 'editar' ? (
            <div className="field">
              <label htmlFor="status">Status</label>
              <select
                id="status"
                name="status"
                value={values.status}
                onChange={(e) =>
                  setValues((a) => ({ ...a, status: e.target.value as StatusTarefa | '' }))
                }
                disabled={deletada}
                required
              >
                <option value="">Selecione</option>
                {STATUS.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_TAREFA_LABEL[s]}
                  </option>
                ))}
              </select>
            </div>
          ) : null}

          {erro ? <InlineAlert>{erro}</InlineAlert> : null}

          {!deletada ? (
            <button type="submit" className="btn btn--primary" disabled={enviando}>
              {enviando ? 'Salvando…' : modo === 'criar' ? 'Criar tarefa' : 'Salvar'}
            </button>
          ) : null}
        </form>

        {modo === 'editar' ? (
          <div className="tarefa-acoes-secundarias">
            {deletada ? (
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
                className="btn btn--danger"
                disabled={excluindo}
                onClick={() => void handleExcluir()}
              >
                {excluindo ? 'Excluindo…' : 'Excluir tarefa'}
              </button>
            )}
          </div>
        ) : null}

      </section>
    </main>
  )
}
