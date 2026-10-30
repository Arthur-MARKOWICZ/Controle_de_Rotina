import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import {
  atualizarProjeto,
  buscarProjeto,
  criarProjeto,
} from '../api/projetoApi'

type FormValues = {
  nome: string
  descricao: string
}

const VAZIO: FormValues = {
  nome: '',
  descricao: '',
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

  const [values, setValues] = useState<FormValues>(VAZIO)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
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
        const projeto = await buscarProjeto(projetoId)
        if (!ativo) {
          return
        }
        setValues({
          nome: projeto.nome,
          descricao: projeto.descricao ?? '',
        })
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar o projeto.')
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
        await criarProjeto({
          nome,
          descricao: descricao || null,
        })
      } else if (id) {
        await atualizarProjeto(id, {
          nome,
          descricao: descricao || null,
        })
      }
      navigate('/projetos', { replace: true })
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

  if (carregando) {
    return (
      <main className="auth-page">
        <section className="auth-panel card" aria-busy="true">
          <p>Carregando projeto…</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-page">
      <section className="auth-panel card projeto-form-panel">
        <h1>{modo === 'criar' ? 'Novo projeto' : 'Editar projeto'}</h1>
        <p className="auth-subtitle">
          {modo === 'criar'
            ? 'O projeto inicia como ativo. Você pode arquivá-lo depois na lista.'
            : 'Atualize nome e descrição. O status muda só por arquivar/reativar.'}
        </p>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
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
              {enviando ? 'Salvando…' : modo === 'criar' ? 'Criar projeto' : 'Salvar'}
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
