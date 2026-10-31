import { useCallback, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { Drawer } from '../feedback/Drawer'
import { InlineAlert } from '../feedback/InlineAlert'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import { criarProjeto } from './api/projetoApi'
import { ProjetoCampos } from './ProjetoCampos'
import {
  PROJETO_FORM_VAZIO,
  validarProjetoForm,
  type ProjetoFormValues,
} from './validation/projetoForm'

/** `location.state` aceito por `/projetos/nova`. */
export type ProjetoDrawerState = {
  /** Aberto a partir da lista: fechar volta no histórico em vez de empilhar `/projetos`. */
  daLista?: boolean
}

const FORM_ID = 'projeto-drawer-form'

export function ProjetoDrawer() {
  const navigate = useNavigate()
  const location = useLocation()
  const estado = (location.state ?? {}) as ProjetoDrawerState
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [values, setValues] = useState<ProjetoFormValues>(PROJETO_FORM_VAZIO)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aberto, setAberto] = useState(true)
  const confirmandoRef = useRef(false)

  const alterado = values.nome !== '' || values.descricao !== ''

  const voltarParaLista = useCallback(() => {
    if (estado.daLista) {
      navigate(-1)
      return
    }
    navigate({ pathname: '/projetos', search: location.search }, { replace: true })
  }, [estado.daLista, location.search, navigate])

  async function pedirFechar() {
    if (!aberto || confirmandoRef.current) {
      return
    }
    if (alterado) {
      confirmandoRef.current = true
      const descartar = await confirm({
        title: 'Descartar projeto?',
        message: 'O que você preencheu neste projeto será perdido.',
        confirmLabel: 'Descartar',
        cancelLabel: 'Continuar editando',
      })
      confirmandoRef.current = false
      if (!descartar) {
        return
      }
    }
    setAberto(false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    const validacao = validarProjetoForm(values)
    if (validacao) {
      setErro(validacao)
      return
    }

    const descricao = values.descricao.trim()
    setEnviando(true)
    try {
      const criado = await criarProjeto({
        nome: values.nome.trim(),
        descricao: descricao || null,
      })
      toast.success('Projeto criado')
      navigate(`/projetos/${criado.id}`, { replace: true })
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível criar o projeto. Tente novamente.',
      )
    } finally {
      setEnviando(false)
    }
  }

  const rodape = (
    <>
      <button type="button" className="btn btn--secondary" onClick={() => void pedirFechar()}>
        Cancelar
      </button>
      <button
        type="submit"
        form={FORM_ID}
        className="btn btn--primary"
        disabled={enviando}
        aria-busy={enviando}
      >
        {enviando ? 'Salvando…' : 'Criar projeto'}
      </button>
    </>
  )

  return (
    <>
      <Drawer
        open={aberto}
        title="Novo projeto"
        onClose={() => void pedirFechar()}
        onExited={voltarParaLista}
        footer={rodape}
      >
        <p className="tarefa-drawer__subtitulo">
          O projeto inicia como ativo. Você pode arquivá-lo depois na lista.
        </p>
        <form id={FORM_ID} className="projeto-form" onSubmit={(e) => void handleSubmit(e)} noValidate>
          <ProjetoCampos values={values} onChange={setValues} />
          {erro ? <InlineAlert>{erro}</InlineAlert> : null}
        </form>
      </Drawer>
      {confirmDialog}
    </>
  )
}
