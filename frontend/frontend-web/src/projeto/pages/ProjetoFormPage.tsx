import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import {
  arquivarProjeto,
  atualizarProjeto,
  buscarProjeto,
  criarProjeto,
  reativarProjeto,
  type ProjetoResponse,
  type StatusProjeto,
} from '../api/projetoApi'

type FormValues = {
  nome: string
  descricao: string
}

const VAZIO: FormValues = {
  nome: '',
  descricao: '',
}

const STATUS_LABEL: Record<StatusProjeto, string> = {
  ATIVO: 'Ativo',
  ARQUIVADO: 'Arquivado',
}

function validarForm(values: FormValues): string | null {
  const nome = values.nome.trim()
  if (!nome) {
    return 'Informe o nome do projeto.'
  }
  if (nome.length > 80) {
    return 'O nome deve ter no máximo 80 caracteres.'
  }
  if (values.descricao.length > 500) {
    return 'A descrição deve ter no máximo 500 caracteres.'
  }
  return null
}

export function ProjetoFormPage() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [values, setValues] = useState<FormValues>(VAZIO)
  const [projeto, setProjeto] = useState<ProjetoResponse | null>(null)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [acaoCiclo, setAcaoCiclo] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    if (modo !== 'editar' || !id) {
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
  }, [id, modo])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    const validacao = validarForm(values)
    if (validacao) {
      setErro(validacao)
      return
    }

    const nome = values.nome.trim()
    const descricao = values.descricao.trim()

    setEnviando(true)
    try {
      if (modo === 'criar') {
        const criado = await criarProjeto({
          nome,
          descricao: descricao || null,
        })
        toast.success('Projeto criado')
        navigate(`/projetos/${criado.id}`, { replace: true })
      } else if (id) {
        const atualizado = await atualizarProjeto(id, {
          nome,
          descricao: descricao || null,
        })
        setProjeto(atualizado)
        setValues({
          nome: atualizado.nome,
          descricao: atualizado.descricao ?? '',
        })
        toast.success('Projeto atualizado')
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setErro(error.message)
      } else {
        setErro(
          modo === 'criar'
            ? 'Não foi possível criar o projeto. Tente novamente.'
            : 'Não foi possível atualizar o projeto. Tente novamente.',
        )
      }
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

  if (modo === 'criar') {
    return (
      <main className="projetos-page">
        <header className="projetos-header">
          <div>
            <h1>Novo projeto</h1>
            <p className="projetos-subtitle">
              O projeto inicia como ativo. Você pode arquivá-lo depois na lista.
            </p>
          </div>
        </header>

        <section className="projeto-form-panel">
          <form className="projeto-form" onSubmit={handleSubmit} noValidate>
            <div className="field">
              <label htmlFor="nome">Nome</label>
              <input
                id="nome"
                name="nome"
                type="text"
                maxLength={80}
                value={values.nome}
                onChange={(e) => setValues((a) => ({ ...a, nome: e.target.value }))}
                required
              />
            </div>

            <div className="field">
              <label htmlFor="descricao">Descrição (opcional)</label>
              <textarea
                id="descricao"
                name="descricao"
                maxLength={500}
                rows={4}
                value={values.descricao}
                onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
              />
            </div>

            {erro ? <InlineAlert>{erro}</InlineAlert> : null}

            <div className="projeto-form-acoes">
              <button type="submit" className="btn btn--primary" disabled={enviando}>
                {enviando ? 'Salvando…' : 'Criar projeto'}
              </button>
              <Link to="/projetos" className="btn btn--secondary">
                Cancelar
              </Link>
            </div>
          </form>
        </section>
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
          <div className="field">
            <label htmlFor="nome">Nome</label>
            <input
              id="nome"
              name="nome"
              type="text"
              maxLength={80}
              value={values.nome}
              onChange={(e) => setValues((a) => ({ ...a, nome: e.target.value }))}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="descricao">Descrição (opcional)</label>
            <textarea
              id="descricao"
              name="descricao"
              maxLength={500}
              rows={4}
              value={values.descricao}
              onChange={(e) => setValues((a) => ({ ...a, descricao: e.target.value }))}
            />
          </div>

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
