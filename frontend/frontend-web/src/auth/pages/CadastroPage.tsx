import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { InlineAlert } from '../../feedback/InlineAlert'
import { ApiError, cadastrarUsuario } from '../api/authApi'
import { AuthLayout } from '../components/AuthLayout'
import { CampoSenha } from '../components/CampoSenha'
import { CampoTexto } from '../components/CampoTexto'
import { focarPrimeiroInvalido, temErro } from '../components/focarPrimeiroInvalido'
import { isEmailValido } from '../validation/email'
import { isSenhaForte } from '../validation/senhaForte'

type ErrosCadastro = {
  nome?: string
  email?: string
  senha?: string
  confirmacao?: string
}

type ValoresCadastro = { nome: string; email: string; senha: string; confirmacao: string }

function validarCadastro({ nome, email, senha, confirmacao }: ValoresCadastro): ErrosCadastro {
  return {
    nome: nome.trim() ? undefined : 'Informe o nome.',
    email: isEmailValido(email) ? undefined : 'Informe um email válido.',
    senha: isSenhaForte(senha)
      ? undefined
      : 'Use uma senha forte: 8 a 72 caracteres, com maiúscula, minúscula, dígito e caractere especial.',
    confirmacao: senha === confirmacao ? undefined : 'As senhas não conferem.',
  }
}

export function CadastroPage() {
  const navigate = useNavigate()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [erros, setErros] = useState<ErrosCadastro>({})
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const nomeRef = useRef<HTMLInputElement>(null)
  const emailRef = useRef<HTMLInputElement>(null)
  const senhaRef = useRef<HTMLInputElement>(null)
  const confirmacaoRef = useRef<HTMLInputElement>(null)

  function mostrarErros(proximos: ErrosCadastro) {
    setErros(proximos)
    focarPrimeiroInvalido([
      [proximos.nome, nomeRef],
      [proximos.email, emailRef],
      [proximos.senha, senhaRef],
      [proximos.confirmacao, confirmacaoRef],
    ])
  }

  function limparErro(campo: keyof ErrosCadastro) {
    setErros((atual) => ({ ...atual, [campo]: undefined }))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErroGeral(null)

    const validacao = validarCadastro({ nome, email, senha, confirmacao })
    if (temErro(validacao)) {
      mostrarErros(validacao)
      return
    }
    setErros({})

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
        mostrarErros({ email: error.message || 'Este email já está cadastrado.' })
      } else {
        setErroGeral('Não foi possível concluir o cadastro. Tente novamente.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <AuthLayout
      titulo="Criar conta"
      subtitulo="Cadastre-se para planejar sua rotina."
      rodape={
        <>
          Já tem conta? <Link to="/login">Entrar</Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <CampoTexto
          ref={nomeRef}
          id="nome"
          label="Nome"
          autoComplete="name"
          value={nome}
          onChange={(valor) => {
            setNome(valor)
            limparErro('nome')
          }}
          erro={erros.nome}
          required
        />

        <CampoTexto
          ref={emailRef}
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(valor) => {
            setEmail(valor)
            limparErro('email')
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
            limparErro('senha')
          }}
          autoComplete="new-password"
          erro={erros.senha}
          required
          mostrarChecklist
        />

        <CampoSenha
          ref={confirmacaoRef}
          id="confirmacao"
          label="Confirmar senha"
          value={confirmacao}
          onChange={(valor) => {
            setConfirmacao(valor)
            limparErro('confirmacao')
          }}
          autoComplete="new-password"
          erro={erros.confirmacao}
          required
        />

        {erroGeral ? <InlineAlert>{erroGeral}</InlineAlert> : null}

        <button
          type="submit"
          className="btn btn--primary"
          disabled={enviando}
          aria-busy={enviando}
        >
          {enviando ? 'Criando…' : 'Criar conta'}
        </button>
      </form>
    </AuthLayout>
  )
}
