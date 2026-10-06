import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../api/authApi'
import { atualizarMe, buscarMe } from '../api/meApi'
import { isEmailValido } from '../validation/email'
import { isSenhaForte } from '../validation/senhaForte'

export function PerfilPage() {
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senhaAtual, setSenhaAtual] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true

    async function carregar() {
      try {
        const me = await buscarMe()
        if (!ativo) {
          return
        }
        setNome(me.nome)
        setEmail(me.email)
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar o perfil.')
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
  }, [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)
    setSucesso(null)

    if (!senhaAtual) {
      setErro('Informe a senha atual para confirmar a alteração.')
      return
    }
    if (!nome.trim()) {
      setErro('Informe o nome.')
      return
    }
    if (!isEmailValido(email)) {
      setErro('Informe um email válido.')
      return
    }
    if (novaSenha || confirmacao) {
      if (!isSenhaForte(novaSenha)) {
        setErro(
          'Use uma senha forte: 8 a 72 caracteres, com maiúscula, minúscula, dígito e caractere especial.',
        )
        return
      }
      if (novaSenha !== confirmacao) {
        setErro('As senhas não conferem.')
        return
      }
    }

    setEnviando(true)
    try {
      const atualizado = await atualizarMe({
        senhaAtual,
        nome: nome.trim(),
        email: email.trim(),
        ...(novaSenha ? { novaSenha } : {}),
      })
      setNome(atualizado.nome)
      setEmail(atualizado.email)
      setSenhaAtual('')
      setNovaSenha('')
      setConfirmacao('')
      setSucesso('Perfil atualizado com sucesso.')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setErro('Senha atual incorreta.')
      } else if (error instanceof ApiError && error.status === 409) {
        setErro('Este email já está cadastrado.')
      } else {
        setErro('Não foi possível atualizar o perfil. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  if (carregando) {
    return (
      <main className="auth-page">
        <section className="auth-panel" aria-busy="true">
          <p>Carregando perfil…</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <h1>Meu perfil</h1>
        <p className="auth-subtitle">Atualize seus dados. A senha atual é obrigatória.</p>

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

          <label htmlFor="senhaAtual">Senha atual</label>
          <input
            id="senhaAtual"
            name="senhaAtual"
            type="password"
            autoComplete="current-password"
            value={senhaAtual}
            onChange={(e) => setSenhaAtual(e.target.value)}
            required
          />

          <label htmlFor="novaSenha">Nova senha</label>
          <input
            id="novaSenha"
            name="novaSenha"
            type="password"
            autoComplete="new-password"
            value={novaSenha}
            onChange={(e) => setNovaSenha(e.target.value)}
          />

          <label htmlFor="confirmacao">Confirmar nova senha</label>
          <input
            id="confirmacao"
            name="confirmacao"
            type="password"
            autoComplete="new-password"
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.target.value)}
          />

          {erro ? (
            <p className="auth-error" role="alert">
              {erro}
            </p>
          ) : null}

          {sucesso ? (
            <p className="auth-success" role="status">
              {sucesso}
            </p>
          ) : null}

          <button type="submit" disabled={enviando}>
            {enviando ? 'Salvando…' : 'Salvar'}
          </button>
        </form>

        <p className="auth-footer">
          <Link to="/">Voltar</Link>
        </p>
      </section>
    </main>
  )
}
