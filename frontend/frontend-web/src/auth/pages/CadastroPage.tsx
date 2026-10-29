import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { InlineAlert } from '../../feedback/InlineAlert'
import { ApiError, cadastrarUsuario } from '../api/authApi'
import { CampoSenha } from '../components/CampoSenha'
import { isEmailValido } from '../validation/email'
import { isSenhaForte } from '../validation/senhaForte'

const PROPOSTA_VALOR = 'Diga o que precisa fazer; o sistema monta o plano.'

export function CadastroPage() {
  const navigate = useNavigate()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    if (!nome.trim()) {
      setErro('Informe o nome.')
      return
    }
    if (!isEmailValido(email)) {
      setErro('Informe um email válido.')
      return
    }
    if (!isSenhaForte(senha)) {
      setErro(
        'Use uma senha forte: 8 a 72 caracteres, com maiúscula, minúscula, dígito e caractere especial.',
      )
      return
    }
    if (senha !== confirmacao) {
      setErro('As senhas não conferem.')
      return
    }

    setEnviando(true)
    try {
      await cadastrarUsuario({
        nome: nome.trim(),
        email: email.trim(),
        senha,
      })
      navigate('/login', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setErro(error.message || 'Este email já está cadastrado.')
      } else {
        setErro('Não foi possível concluir o cadastro. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel card">
        <h1>Criar conta</h1>
        <p className="auth-value">{PROPOSTA_VALOR}</p>
        <p className="auth-subtitle">Cadastre-se para planejar sua rotina.</p>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="field">
            <label htmlFor="nome">Nome</label>
            <input
              id="nome"
              name="nome"
              type="text"
              autoComplete="name"
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <CampoSenha
            id="senha"
            label="Senha"
            value={senha}
            onChange={setSenha}
            autoComplete="new-password"
            required
            mostrarChecklist
          />

          <CampoSenha
            id="confirmacao"
            label="Confirmar senha"
            value={confirmacao}
            onChange={setConfirmacao}
            autoComplete="new-password"
            required
          />

          {erro ? <InlineAlert>{erro}</InlineAlert> : null}

          <button
            type="submit"
            className="btn btn--primary"
            disabled={enviando}
            aria-busy={enviando}
          >
            {enviando ? 'Criando…' : 'Criar conta'}
          </button>
        </form>

        <p className="auth-footer">
          Já tem conta? <Link to="/login">Entrar</Link>
        </p>
      </section>
    </main>
  )
}
