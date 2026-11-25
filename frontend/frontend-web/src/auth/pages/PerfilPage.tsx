import { DownloadSimpleIcon, LightningIcon, SignOutIcon, TrashIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { PageHeader } from '../../layout/PageHeader'
import { ThemePreferenceField } from '../../theme/ThemePreferenceField'
import type { XpProgresso } from '../../xp/types'
import { ApiError, type UsuarioResponse } from '../api/authApi'
import { atualizarMe, buscarMe, excluirConta, exportarDados } from '../api/meApi'
import { CampoSenha } from '../components/CampoSenha'
import { ExcluirContaDialog } from '../components/ExcluirContaDialog'
import { useAuth } from '../session/AuthContext'
import { encerrarSessaoContaExcluida } from '../session/tokenStore'
import { isEmailValido } from '../validation/email'
import { isSenhaForte } from '../validation/senhaForte'

function progressoDe(usuario: UsuarioResponse): XpProgresso {
  return {
    xpTotal: usuario.xpTotal,
    nivel: usuario.nivel,
    xpNoNivel: usuario.xpNoNivel,
    xpParaProximoNivel: usuario.xpParaProximoNivel,
  }
}

function ProgressoXp({ progresso }: { progresso: XpProgresso }) {
  const { nivel, xpNoNivel, xpParaProximoNivel, xpTotal } = progresso
  const pct =
    xpParaProximoNivel > 0 ? Math.min(100, Math.round((xpNoNivel / xpParaProximoNivel) * 100)) : 0
  const faltam = Math.max(0, xpParaProximoNivel - xpNoNivel)

  return (
    <section className="card perfil-card" aria-labelledby="perfil-progresso-titulo">
      <h2 id="perfil-progresso-titulo" className="perfil-card__titulo">
        Progresso
      </h2>
      <div className="perfil-xp">
        <p className="perfil-xp__nivel">
          <LightningIcon aria-hidden weight="fill" />
          Nível {nivel}
        </p>
        <div
          className="perfil-xp__track"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={xpParaProximoNivel}
          aria-valuenow={xpNoNivel}
          aria-label={`${xpNoNivel} de ${xpParaProximoNivel} XP no nível ${nivel}`}
        >
          <div className="perfil-xp__fill" style={{ width: `${pct}%` }} />
        </div>
        <dl className="perfil-xp__numeros">
          <div>
            <dt>No nível</dt>
            <dd>
              {xpNoNivel} / {xpParaProximoNivel} XP
            </dd>
          </div>
          <div>
            <dt>Para o nível {nivel + 1}</dt>
            <dd>{faltam} XP</dd>
          </div>
          <div>
            <dt>XP total</dt>
            <dd>{xpTotal} XP</dd>
          </div>
        </dl>
      </div>
    </section>
  )
}

/** Dispara o download de um Blob no navegador. */
function baixarArquivo(blob: Blob, nomeArquivo: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = nomeArquivo
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/** Exportar dados e excluir conta (LGPD). */
function SeusDados() {
  const exportandoRef = useRef(false)
  const [exportando, setExportando] = useState(false)
  const [erroExport, setErroExport] = useState<string | null>(null)
  const [dialogAberto, setDialogAberto] = useState(false)
  const [excluindo, setExcluindo] = useState(false)
  const [erroSenha, setErroSenha] = useState<string | null>(null)
  const [erroExclusao, setErroExclusao] = useState<string | null>(null)

  async function exportar() {
    if (exportandoRef.current) {
      return
    }
    exportandoRef.current = true
    setExportando(true)
    setErroExport(null)
    try {
      const { blob, nomeArquivo } = await exportarDados()
      baixarArquivo(blob, nomeArquivo)
    } catch (error) {
      setErroExport(
        error instanceof ApiError && error.message
          ? error.message
          : 'Não foi possível exportar seus dados. Tente novamente.',
      )
    } finally {
      exportandoRef.current = false
      setExportando(false)
    }
  }

  const fecharDialog = useCallback(() => {
    setDialogAberto(false)
    setErroSenha(null)
    setErroExclusao(null)
  }, [])

  async function confirmarExclusao(senhaAtual: string) {
    setExcluindo(true)
    setErroSenha(null)
    setErroExclusao(null)
    try {
      await excluirConta(senhaAtual)
      encerrarSessaoContaExcluida()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setErroSenha('Senha atual incorreta.')
      } else {
        setErroExclusao('Não foi possível excluir a conta. Tente novamente.')
      }
      setExcluindo(false)
    }
  }

  return (
    <section className="card perfil-card perfil-card--dados" aria-labelledby="perfil-dados-titulo">
      <h2 id="perfil-dados-titulo" className="perfil-card__titulo">
        Seus dados
      </h2>
      <p className="perfil-card__descricao">
        Baixe uma cópia de tudo que você registrou, em JSON.
      </p>
      <button
        type="button"
        className="btn btn--secondary"
        onClick={() => void exportar()}
        disabled={exportando}
        aria-busy={exportando}
      >
        <DownloadSimpleIcon aria-hidden />
        {exportando ? 'Exportando…' : 'Exportar meus dados'}
      </button>
      {erroExport ? <InlineAlert>{erroExport}</InlineAlert> : null}

      <div className="perfil-perigo">
        <h3 className="perfil-perigo__titulo">Excluir conta</h3>
        <p className="perfil-card__descricao">
          Apaga a conta e todos os dados para sempre. Não dá para desfazer.
        </p>
        <button type="button" className="btn btn--ghost btn--danger" onClick={() => setDialogAberto(true)}>
          <TrashIcon aria-hidden />
          Excluir conta
        </button>
      </div>

      <ExcluirContaDialog
        open={dialogAberto}
        ocupado={excluindo}
        erroSenha={erroSenha}
        erroGeral={erroExclusao}
        onConfirm={(senha) => void confirmarExclusao(senha)}
        onExportar={() => void exportar()}
        onClose={fecharDialog}
      />
    </section>
  )
}

export function PerfilPage() {
  const { logout } = useAuth()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [progresso, setProgresso] = useState<XpProgresso | null>(null)
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
        setProgresso(progressoDe(me))
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
      setProgresso(progressoDe(atualizado))
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

  return (
    <main className="perfil-page">
      <PageHeader title="Meu perfil" description="Sua conta, a aparência do app e o seu progresso." />

      {carregando ? (
        <Skeleton variant="perfil-secoes" />
      ) : (
        <div className="perfil-grid">
          <section className="card perfil-card" aria-labelledby="perfil-conta-titulo">
            <header className="perfil-card__header">
              <h2 id="perfil-conta-titulo" className="perfil-card__titulo">
                Conta
              </h2>
              <button
                type="button"
                className="btn btn--ghost btn--danger btn--sm"
                onClick={() => void logout()}
              >
                <SignOutIcon aria-hidden />
                Sair
              </button>
            </header>
            <p className="perfil-card__descricao">
              A senha atual é obrigatória para confirmar qualquer alteração.
            </p>

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
                <legend>Confirmar identidade</legend>
                <CampoSenha
                  id="senhaAtual"
                  label="Senha atual"
                  value={senhaAtual}
                  onChange={setSenhaAtual}
                  autoComplete="current-password"
                  required
                />
              </fieldset>

              <fieldset className="perfil-secao">
                <legend>Nova senha (opcional)</legend>
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

              <button
                type="submit"
                className="btn btn--primary"
                disabled={enviando}
                aria-busy={enviando}
              >
                {enviando ? 'Salvando…' : 'Salvar'}
              </button>
            </form>
          </section>

          <div className="perfil-grid__lateral">
            {progresso ? <ProgressoXp progresso={progresso} /> : null}

            <section className="card perfil-card perfil-card--aparencia">
              <ThemePreferenceField />
            </section>

            <SeusDados />
          </div>
        </div>
      )}
    </main>
  )
}
