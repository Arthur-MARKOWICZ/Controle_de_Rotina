import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { Drawer } from '../feedback/Drawer'
import { InlineAlert } from '../feedback/InlineAlert'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import { dataHojeIso } from '../planner/data'
import {
  atualizarObjetivo,
  buscarObjetivo,
  criarObjetivo,
  type ObjetivoResponse,
} from './api/objetivoApi'
import { ObjetivoCampos } from './ObjetivoCampos'
import {
  OBJETIVO_FORM_VAZIO,
  primeiroErroObjetivo,
  validarObjetivoForm,
  type ObjetivoFormErros,
  type ObjetivoFormValues,
} from './validation/objetivoForm'

export type ObjetivosOutletContext = {
  /** Objetivo criado ou editado: a lista recarrega na mesma visão. */
  aoSalvar: () => void
}

/** `location.state` aceito pelas rotas do drawer. */
export type ObjetivoDrawerState = {
  /** Aberto a partir da lista: fechar volta no histórico em vez de empilhar `/objetivos`. */
  daLista?: boolean
  /** Query da lista de origem: o detalhe e a edição a repassam para "Voltar a Objetivos". */
  listaSearch?: string
}

const FORM_ID = 'objetivo-drawer-form'

function valoresDoObjetivo(objetivo: ObjetivoResponse): ObjetivoFormValues {
  return {
    titulo: objetivo.titulo,
    descricao: objetivo.descricao ?? '',
    prazo: objetivo.prazo ?? '',
  }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function ObjetivoDrawer() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()
  const location = useLocation()
  const estado = (location.state ?? {}) as ObjetivoDrawerState
  const { aoSalvar } = useOutletContext<ObjetivosOutletContext>()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const hojeIso = dataHojeIso()

  const [inicial, setInicial] = useState<ObjetivoFormValues>(OBJETIVO_FORM_VAZIO)
  const [values, setValues] = useState<ObjetivoFormValues>(OBJETIVO_FORM_VAZIO)
  const [prazoOriginal, setPrazoOriginal] = useState<string | null>(null)
  const [erros, setErros] = useState<ObjetivoFormErros>({})
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [erroCarga, setErroCarga] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aberto, setAberto] = useState(true)
  const confirmandoRef = useRef(false)
  const criadoRef = useRef(false)

  const alterado = JSON.stringify(values) !== JSON.stringify(inicial)
  const prazoOriginalVencido = prazoOriginal != null && prazoOriginal < hojeIso

  const fechar = useCallback(() => {
    if (modo === 'editar' && id) {
      // A edição abre sobre o detalhe: fecha nele, repassando a visão da lista de origem.
      navigate(`/objetivos/${id}`, { replace: true, state: { listaSearch: estado.listaSearch } })
      return
    }
    if (criadoRef.current) {
      // Objetivo novo nasce ativo: mostra a visão onde ele aparece, venha de onde vier.
      navigate('/objetivos', { replace: true })
      return
    }
    if (estado.daLista) {
      navigate(-1)
      return
    }
    navigate({ pathname: '/objetivos', search: location.search }, { replace: true })
  }, [estado.daLista, estado.listaSearch, id, location.search, modo, navigate])

  useEffect(() => {
    if (modo !== 'editar' || !id) {
      return
    }
    const objetivoId = id
    let ativo = true
    async function carregar() {
      setCarregando(true)
      setErroCarga(null)
      try {
        const data = await buscarObjetivo(objetivoId)
        if (!ativo) {
          return
        }
        const form = valoresDoObjetivo(data)
        setInicial(form)
        setValues(form)
        setPrazoOriginal(data.prazo)
      } catch (error) {
        if (ativo) {
          setErroCarga(mensagemErro(error, 'Não foi possível carregar o objetivo.'))
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

  async function pedirFechar() {
    if (!aberto || confirmandoRef.current) {
      return
    }
    if (alterado) {
      confirmandoRef.current = true
      const descartar = await confirm({
        title: modo === 'criar' ? 'Descartar objetivo?' : 'Descartar alterações?',
        message:
          modo === 'criar'
            ? 'O que você preencheu neste objetivo será perdido.'
            : 'O que você alterou neste objetivo será perdido.',
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

    const validacao = validarObjetivoForm(values, { hojeIso, prazoOriginal })
    setErros(validacao)
    const mensagem = primeiroErroObjetivo(validacao)
    if (mensagem) {
      setErro(mensagem)
      return
    }

    const titulo = values.titulo.trim()
    const descricao = values.descricao.trim()
    const prazo = values.prazo || null

    setEnviando(true)
    try {
      if (modo === 'criar') {
        await criarObjetivo({ titulo, descricao: descricao || null, prazo })
        criadoRef.current = true
        toast.success('Objetivo criado')
      } else if (id) {
        await atualizarObjetivo(id, { titulo, descricao, prazo })
        toast.success('Objetivo atualizado')
      }
      aoSalvar()
      setInicial(values)
      setAberto(false)
    } catch (error) {
      setErro(
        mensagemErro(
          error,
          modo === 'criar'
            ? 'Não foi possível criar o objetivo. Tente novamente.'
            : 'Não foi possível salvar o objetivo. Tente novamente.',
        ),
      )
    } finally {
      setEnviando(false)
    }
  }

  const semFormulario = carregando || erroCarga != null

  const rodape = (
    <>
      <button type="button" className="btn btn--secondary" onClick={() => void pedirFechar()}>
        {modo === 'criar' ? 'Cancelar' : 'Fechar'}
      </button>
      {semFormulario ? null : (
        <button
          type="submit"
          form={FORM_ID}
          className="btn btn--primary"
          disabled={enviando}
          aria-busy={enviando}
        >
          {enviando ? 'Salvando…' : modo === 'criar' ? 'Criar objetivo' : 'Salvar'}
        </button>
      )}
    </>
  )

  return (
    <>
      <Drawer
        open={aberto}
        title={modo === 'criar' ? 'Novo objetivo' : 'Editar objetivo'}
        onClose={() => void pedirFechar()}
        onExited={fechar}
        footer={rodape}
      >
        {modo === 'criar' ? (
          <p className="tarefa-drawer__subtitulo">
            O objetivo começa ativo. Você conclui quando alcançá-lo.
          </p>
        ) : null}
        {carregando ? <p className="plano-dia-meta">Carregando…</p> : null}
        {erroCarga ? <InlineAlert>{erroCarga}</InlineAlert> : null}
        {semFormulario ? null : (
          <form
            id={FORM_ID}
            className="objetivo-form"
            onSubmit={(e) => void handleSubmit(e)}
            noValidate
          >
            <ObjetivoCampos
              values={values}
              onChange={setValues}
              erros={erros}
              prazoMinimo={prazoOriginalVencido ? undefined : hojeIso}
              desabilitado={enviando}
            />
            {erro ? <InlineAlert>{erro}</InlineAlert> : null}
          </form>
        )}
      </Drawer>
      {confirmDialog}
    </>
  )
}
