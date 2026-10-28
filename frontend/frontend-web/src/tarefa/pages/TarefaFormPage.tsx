import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { PRIORIDADE_LABEL, STATUS_TAREFA_LABEL } from '../../home/labels'
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
  duracaoHoras: '',
  status: '',
}

export function TarefaFormPage() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()

  const [values, setValues] = useState<TarefaFormValues>(VAZIO)
  const [deletada, setDeletada] = useState(false)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [excluindo, setExcluindo] = useState(false)
  const [restaurando, setRestaurando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

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
          duracaoHoras: tarefa.duracaoHoras,
          status: tarefa.status,
        })
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
    const duracaoHoras = values.duracaoHoras as number

    setEnviando(true)
    try {
      if (modo === 'criar') {
        await criarTarefa({
          titulo: values.titulo.trim(),
          descricao: values.descricao.trim(),
          prioridade,
          prazo,
          duracaoHoras,
        })
      } else if (id) {
        await atualizarTarefa(id, {
          titulo: values.titulo.trim(),
          descricao: values.descricao.trim(),
          prioridade,
          prazo,
          duracaoHoras,
          status: values.status as StatusTarefa,
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

  async function handleExcluir() {
    if (!id || deletada) {
      return
    }
    setErro(null)
    setExcluindo(true)
    try {
      await excluirTarefa(id)
      navigate('/tarefas', { replace: true })
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
        duracaoHoras: restaurada.duracaoHoras,
        status: restaurada.status,
      })
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
            <label htmlFor="prazo">Prazo</label>
            <input
              id="prazo"
              name="prazo"
              type="datetime-local"
              value={values.prazo}
              onChange={(e) => setValues((a) => ({ ...a, prazo: e.target.value }))}
              disabled={deletada}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="duracaoHoras">Duração (horas)</label>
            <input
              id="duracaoHoras"
              name="duracaoHoras"
              type="number"
              min={1}
              step={1}
              value={values.duracaoHoras}
              onChange={(e) =>
                setValues((a) => ({
                  ...a,
                  duracaoHoras: e.target.value === '' ? '' : Number(e.target.value),
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
