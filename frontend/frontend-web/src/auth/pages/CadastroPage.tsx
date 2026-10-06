import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { ApiError, cadastrarUsuario } from '../api/authApi'
import { isEmailValido } from '../validation/email'
import { isSenhaForte } from '../validation/senhaForte'

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
        setErro('Este email já está cadastrado.')
      } else {
        setErro('Não foi possível concluir o cadastro. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <h1>Criar conta</h1>
        <p className="auth-subtitle">Cadastre-se para planejar sua rotina.</p>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
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

          <label htmlFor="senha">Senha</label>
          <input
            id="senha"
            name="senha"
            type="password"
            autoComplete="new-password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            required
          />

          <label htmlFor="confirmacao">Confirmar senha</label>
          <input
            id="confirmacao"
            name="confirmacao"
            type="password"
            autoComplete="new-password"
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.target.value)}
            required
          />

          {erro ? (
            <p className="auth-error" role="alert">
              {erro}
            </p>
          ) : null}

          <button type="submit" disabled={enviando}>
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
