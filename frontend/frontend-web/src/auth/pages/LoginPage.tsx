import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'

import { InlineAlert } from '../../feedback/InlineAlert'
import { ApiError, login } from '../api/authApi'
import { AuthLayout } from '../components/AuthLayout'
import { CampoSenha } from '../components/CampoSenha'
import { CampoTexto } from '../components/CampoTexto'
import { focarPrimeiroInvalido, temErro } from '../components/focarPrimeiroInvalido'
import { useAuth } from '../session/AuthContext'
import { isEmailValido } from '../validation/email'

type ErrosLogin = { email?: string; senha?: string }

function validarLogin(email: string, senha: string): ErrosLogin {
  return {
    email: isEmailValido(email) ? undefined : 'Informe um email válido.',
    senha: senha ? undefined : 'Informe a senha.',
  }
}

export function LoginPage() {
  const navigate = useNavigate()
  const { isAuthenticated, loginWithToken } = useAuth()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erros, setErros] = useState<ErrosLogin>({})
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const emailRef = useRef<HTMLInputElement>(null)
  const senhaRef = useRef<HTMLInputElement>(null)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  function mostrarErros(proximos: ErrosLogin) {
    setErros(proximos)
    focarPrimeiroInvalido([
      [proximos.email, emailRef],
      [proximos.senha, senhaRef],
    ])
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErroGeral(null)

    const validacao = validarLogin(email, senha)
    if (temErro(validacao)) {
      mostrarErros(validacao)
      return
    }
    setErros({})

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
        mostrarErros({ senha: 'Credenciais inválidas.' })
      } else {
        setErroGeral('Não foi possível entrar. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <AuthLayout
      titulo="Entrar"
      subtitulo="Acesse sua conta para continuar."
      rodape={
        <>
          Não tem conta? <Link to="/cadastro">Criar conta</Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <CampoTexto
          ref={emailRef}
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(valor) => {
            setEmail(valor)
            setErros((atual) => ({ ...atual, email: undefined }))
          }}
          erro={erros.email}
          required
        />

        <CampoSenha
          ref={senhaRef}
          id="senha"
          label="Senha"
          value={senha}
          onChange={(valor) => {
            setSenha(valor)
            setErros((atual) => ({ ...atual, senha: undefined }))
          }}
          autoComplete="current-password"
          erro={erros.senha}
          required
        />

        {erroGeral ? <InlineAlert>{erroGeral}</InlineAlert> : null}

        <button
          type="submit"
          className="btn btn--primary"
          disabled={enviando}
          aria-busy={enviando}
        >
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </AuthLayout>
  )
}
