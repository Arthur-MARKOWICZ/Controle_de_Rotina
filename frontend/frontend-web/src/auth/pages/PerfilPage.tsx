import { useEffect, useState, type FormEvent } from 'react'

import { InlineAlert } from '../../feedback/InlineAlert'
import { ThemePreferenceField } from '../../theme/ThemePreferenceField'
import { ApiError } from '../api/authApi'
import { atualizarMe, buscarMe } from '../api/meApi'
import { CampoSenha } from '../components/CampoSenha'
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
        setErro(error.message || 'Este email já está cadastrado.')
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
        <section className="auth-panel card" aria-busy="true">
          <h1>Meu perfil</h1>
          <ThemePreferenceField />
          <p>Carregando perfil…</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-page">
      <section className="auth-panel card">
        <h1>Meu perfil</h1>
        <p className="auth-subtitle">
          Atualize seus dados. A senha atual é obrigatória para confirmar qualquer alteração.
        </p>

        <ThemePreferenceField />

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <fieldset className="perfil-secao">
            <legend>Dados</legend>
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
          </fieldset>

          <fieldset className="perfil-secao">
            <legend>Alterar senha</legend>
            <CampoSenha
              id="senhaAtual"
              label="Senha atual"
              value={senhaAtual}
              onChange={setSenhaAtual}
              autoComplete="current-password"
              required
            />

            <CampoSenha
              id="novaSenha"
              label="Nova senha"
              value={novaSenha}
              onChange={setNovaSenha}
              autoComplete="new-password"
              mostrarChecklist={novaSenha.length > 0}
            />

            <CampoSenha
              id="confirmacao"
              label="Confirmar nova senha"
              value={confirmacao}
              onChange={setConfirmacao}
              autoComplete="new-password"
            />
          </fieldset>

          {erro ? <InlineAlert>{erro}</InlineAlert> : null}

          {sucesso ? (
            <p className="auth-success" role="status">
              {sucesso}
            </p>
          ) : null}

          <button type="submit" className="btn btn--primary" disabled={enviando}>
            {enviando ? 'Salvando…' : 'Salvar'}
          </button>
        </form>
      </section>
    </main>
  )
}
