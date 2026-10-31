import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import {
  arquivarProjeto,
  atualizarProjeto,
  buscarProjeto,
  reativarProjeto,
  type ProjetoResponse,
  type StatusProjeto,
} from '../api/projetoApi'
import { ProjetoCampos } from '../ProjetoCampos'
import {
  PROJETO_FORM_VAZIO,
  validarProjetoForm,
  type ProjetoFormValues,
} from '../validation/projetoForm'

const STATUS_LABEL: Record<StatusProjeto, string> = {
  ATIVO: 'Ativo',
  ARQUIVADO: 'Arquivado',
}

/** Hub do projeto (`/projetos/:id`): edição + atalhos. A criação fica em `ProjetoDrawer`. */
export function ProjetoFormPage() {
  const { id } = useParams<{ id: string }>()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [values, setValues] = useState<ProjetoFormValues>(PROJETO_FORM_VAZIO)
  const [projeto, setProjeto] = useState<ProjetoResponse | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [enviando, setEnviando] = useState(false)
  const [acaoCiclo, setAcaoCiclo] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    if (!id) {
      return
    }

    const projetoId = id
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const carregado = await buscarProjeto(projetoId)
        if (!ativo) {
          return
        }
        setProjeto(carregado)
        setValues({
          nome: carregado.nome,
          descricao: carregado.descricao ?? '',
        })
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar o projeto.')
          setProjeto(null)
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
  }, [id])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    const validacao = validarProjetoForm(values)
    if (validacao) {
      setErro(validacao)
      return
    }
    if (!id) {
      return
    }

    const descricao = values.descricao.trim()

    setEnviando(true)
    try {
      const atualizado = await atualizarProjeto(id, {
        nome: values.nome.trim(),
        descricao: descricao || null,
      })
      setProjeto(atualizado)
      setValues({
        nome: atualizado.nome,
        descricao: atualizado.descricao ?? '',
      })
      toast.success('Projeto atualizado')
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível atualizar o projeto. Tente novamente.',
      )
    } finally {
      setEnviando(false)
    }
  }

  async function handleArquivar() {
    if (!id || !projeto) {
      return
    }

    const confirmado = await confirm({
      title: 'Arquivar projeto?',
      message: `“${projeto.nome}” ficará arquivado. As tarefas vinculadas permanecem; novas associações só a projetos ativos.`,
      confirmLabel: 'Arquivar',
    })
    if (!confirmado) {
      return
    }

    setErro(null)
    setAcaoCiclo(true)
    try {
      const atualizado = await arquivarProjeto(id)
      setProjeto(atualizado)
      toast.success('Projeto arquivado')
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível arquivar o projeto.',
      )
    } finally {
      setAcaoCiclo(false)
    }
  }

  async function handleReativar() {
    if (!id) {
      return
    }

    setErro(null)
    setAcaoCiclo(true)
    try {
      const atualizado = await reativarProjeto(id)
      setProjeto(atualizado)
      toast.success('Projeto reativado')
    } catch (error) {
      setErro(
        error instanceof ApiError ? error.message : 'Não foi possível reativar o projeto.',
      )
    } finally {
      setAcaoCiclo(false)
    }
  }

  if (carregando) {
    return (
      <main className="projetos-page">
        <Skeleton variant="tarefas-lista" />
      </main>
    )
  }

  return (
    <main className="projetos-page">
      {confirmDialog}
      <header className="projetos-header">
        <div>
          <h1>{projeto?.nome ?? 'Projeto'}</h1>
          {projeto ? (
            <div className="projeto-hub__meta">
              <span className={`badge status-${projeto.status.toLowerCase()}`}>
                {STATUS_LABEL[projeto.status]}
              </span>
            </div>
          ) : null}
          {projeto?.descricao ? (
            <p className="projetos-subtitle">{projeto.descricao}</p>
          ) : (
            <p className="projetos-subtitle">
              Atalhos para tarefas e capacidade; edite nome e descrição abaixo.
            </p>
          )}
        </div>
        <div className="projetos-header-actions">
          {id ? (
            <>
              <Link
                to={`/tarefas?projetoId=${id}`}
                className="btn btn--secondary"
              >
                Ver tarefas
              </Link>
              <Link to="/capacidade" className="btn btn--secondary">
                Reservar tempo
              </Link>
            </>
          ) : null}
          {projeto?.status === 'ARQUIVADO' ? (
            <button
              type="button"
              className="btn btn--ghost"
              disabled={acaoCiclo}
              onClick={() => void handleReativar()}
            >
              {acaoCiclo ? 'Reativando…' : 'Reativar'}
            </button>
          ) : projeto ? (
            <button
              type="button"
              className="btn btn--ghost"
              disabled={acaoCiclo}
              onClick={() => void handleArquivar()}
            >
              {acaoCiclo ? 'Arquivando…' : 'Arquivar'}
            </button>
          ) : null}
        </div>
      </header>

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      <section className="projeto-form-panel">
        <h2 className="projeto-hub__form-titulo">Editar</h2>
        <form className="projeto-form" onSubmit={handleSubmit} noValidate>
          <ProjetoCampos values={values} onChange={setValues} />

          <div className="projeto-form-acoes">
            <button type="submit" className="btn btn--primary" disabled={enviando || acaoCiclo}>
              {enviando ? 'Salvando…' : 'Salvar'}
            </button>
            <Link to="/projetos" className="btn btn--secondary">
              Voltar à lista
            </Link>
          </div>
        </form>
      </section>
    </main>
  )
}
