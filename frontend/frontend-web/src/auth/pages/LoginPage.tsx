import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'

import { InlineAlert } from '../../feedback/InlineAlert'
import { ApiError, login } from '../api/authApi'
import { CampoSenha } from '../components/CampoSenha'
import { useAuth } from '../session/AuthContext'
import { isEmailValido } from '../validation/email'

const PROPOSTA_VALOR = 'Diga o que precisa fazer; o sistema monta o plano.'

export function LoginPage() {
  const navigate = useNavigate()
  const { isAuthenticated, loginWithToken } = useAuth()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    if (!isEmailValido(email)) {
      setErro('Informe um email válido.')
      return
    }
    if (!senha) {
      setErro('Informe a senha.')
      return
    }

    setEnviando(true)
    try {
      const response = await login({
        email: email.trim(),
        senha,
      })
      loginWithToken(response.accessToken)
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        setErro('Credenciais inválidas.')
      } else {
        setErro('Não foi possível entrar. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel card">
        <h1>Entrar</h1>
        <p className="auth-value">{PROPOSTA_VALOR}</p>
        <p className="auth-subtitle">Acesse sua conta para continuar.</p>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
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
            autoComplete="current-password"
            required
          />

          {erro ? <InlineAlert>{erro}</InlineAlert> : null}

          <button
            type="submit"
            className="btn btn--primary"
            disabled={enviando}
            aria-busy={enviando}
          >
            {enviando ? 'Entrando…' : 'Entrar'}
          </button>
        </form>

        <p className="auth-footer">
          Não tem conta? <Link to="/cadastro">Criar conta</Link>
        </p>
      </section>
    </main>
  )
}
