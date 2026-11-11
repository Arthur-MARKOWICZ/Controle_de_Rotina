import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { Drawer } from '../feedback/Drawer'
import { InlineAlert } from '../feedback/InlineAlert'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { TagResumo } from '../tag/api/tagApi'
import { datetimeLocalParaInstant, instantParaDatetimeLocal } from '../tarefa/datetime'
import {
  atualizarDecidir,
  buscarDecidir,
  criarDecidir,
  type DecidirResponse,
} from './api/decidirApi'
import { DecidirForm, type DecidirFormValues } from './DecidirForm'

export type DecidirOutletContext = {
  projetosAtivos: ProjetoResponse[]
  aoAtualizar: (item: DecidirResponse) => void
  aoCriar: (item: DecidirResponse) => void
}

export type DecidirDrawerState = {
  daLista?: boolean
  titulo?: string
}

const FORM_ID = 'decidir-drawer-form'

const VAZIO: DecidirFormValues = {
  titulo: '',
  descricao: '',
  decidirAte: '',
}

function valoresDoItem(item: DecidirResponse): DecidirFormValues {
  return {
    titulo: item.titulo,
    descricao: item.descricao ?? '',
    decidirAte: instantParaDatetimeLocal(item.decidirAte),
  }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function DecidirDrawer() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()
  const location = useLocation()
  const estado = (location.state ?? {}) as DecidirDrawerState
  const { projetosAtivos, aoAtualizar, aoCriar } = useOutletContext<DecidirOutletContext>()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [inicial, setInicial] = useState(() => ({
    values: { ...VAZIO, titulo: estado.titulo ?? '' },
    projetoId: '',
    tagIds: [] as string[],
  }))
  const [values, setValues] = useState<DecidirFormValues>(inicial.values)
  const [projetoId, setProjetoId] = useState('')
  const [tagIds, setTagIds] = useState<string[]>([])
  const [tagsSelecionadas, setTagsSelecionadas] = useState<TagResumo[]>([])
  const [item, setItem] = useState<DecidirResponse | null>(null)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aberto, setAberto] = useState(true)
  const confirmandoRef = useRef(false)

  const somenteLeitura = Boolean(item?.tarefaId)
  const alterado =
    JSON.stringify(values) !== JSON.stringify(inicial.values) ||
    projetoId !== inicial.projetoId ||
    JSON.stringify(tagIds) !== JSON.stringify(inicial.tagIds)

  const voltarParaLista = useCallback(() => {
    if (estado.daLista) {
      navigate(-1)
      return
    }
    navigate({ pathname: '/decidir', search: location.search }, { replace: true })
  }, [estado.daLista, location.search, navigate])

  useEffect(() => {
    if (modo !== 'editar' || !id) {
      return
    }
    const itemId = id
    let ativo = true
    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const carregado = await buscarDecidir(itemId)
        if (!ativo) {
          return
        }
        const form = valoresDoItem(carregado)
        const ids = carregado.tags?.map((tag) => tag.id) ?? []
        setItem(carregado)
        setValues(form)
        setProjetoId(carregado.projetoId ?? '')
        setTagIds(ids)
        setTagsSelecionadas(carregado.tags ?? [])
        setInicial({ values: form, projetoId: carregado.projetoId ?? '', tagIds: ids })
      } catch (error) {
        if (ativo) {
          setErro(mensagemErro(error, 'Não foi possível carregar o item.'))
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
    if (alterado && !somenteLeitura) {
      confirmandoRef.current = true
      const descartar = await confirm({
        title: 'Descartar alterações?',
        message: 'As alterações que você fez neste item serão perdidas.',
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
    if (somenteLeitura) {
      setErro('Item promovido é somente leitura.')
      return
    }
    const titulo = values.titulo.trim()
    if (!titulo) {
      setErro('Informe um título.')
      return
    }

    setEnviando(true)
    try {
      const corpo = {
        titulo,
        descricao: values.descricao.trim() || null,
        decidirAte: datetimeLocalParaInstant(values.decidirAte),
        projetoId: projetoId || null,
        tagIds,
      }
      if (modo === 'criar') {
        const criado = await criarDecidir(corpo)
        toast.success('Item capturado', {
          action: {
            label: 'Abrir',
            onClick: () => navigate(`/decidir/${criado.id}`, { state: { daLista: true } }),
          },
        })
        aoCriar(criado)
      } else if (id) {
        const atualizado = await atualizarDecidir(id, corpo)
        toast.success('Item salvo')
        aoAtualizar(atualizado)
      }
      setAberto(false)
    } catch (error) {
      setErro(
        mensagemErro(
          error,
          modo === 'criar'
            ? 'Não foi possível criar o item. Tente novamente.'
            : 'Não foi possível atualizar o item. Tente novamente.',
        ),
      )
    } finally {
      setEnviando(false)
    }
  }

  const tituloDrawer =
    modo === 'criar' ? 'Novo item' : somenteLeitura ? 'Item decidido' : 'Editar item'

  const rodape = (
    <>
      <button type="button" className="btn btn--secondary" onClick={() => void pedirFechar()}>
        {somenteLeitura ? 'Fechar' : 'Cancelar'}
      </button>
      {!somenteLeitura && (modo === 'criar' || item) ? (
        <button
          type="submit"
          form={FORM_ID}
          className="btn btn--primary"
          disabled={enviando || carregando}
          aria-busy={enviando}
        >
          {enviando ? 'Salvando…' : modo === 'criar' ? 'Criar item' : 'Salvar'}
        </button>
      ) : null}
    </>
  )

  return (
    <>
      <Drawer
        open={aberto}
        title={tituloDrawer}
        onClose={() => void pedirFechar()}
        onExited={voltarParaLista}
        busy={carregando}
        footer={rodape}
      >
        {carregando ? (
          <p role="status">Carregando item…</p>
        ) : (
          <>
            <p className="tarefa-drawer__subtitulo">
              {modo === 'criar'
                ? 'Capture o que ainda precisa de uma decisão antes de virar tarefa.'
                : somenteLeitura
                  ? 'Este item já foi promovido a tarefa e não pode ser editado.'
                  : 'Complete o que faltar antes de promover a tarefa.'}
            </p>
            {modo === 'editar' && !item ? (
              erro ? <InlineAlert>{erro}</InlineAlert> : null
            ) : (
              <DecidirForm
                formId={FORM_ID}
                values={values}
                onValuesChange={setValues}
                projetoId={projetoId}
                onProjetoChange={setProjetoId}
                projetosAtivos={projetosAtivos}
                tagIds={tagIds}
                tagsSelecionadas={tagsSelecionadas}
                onTagsChange={(ids, tags) => {
                  setTagIds(ids)
                  setTagsSelecionadas(tags)
                }}
                disabled={somenteLeitura}
                erro={erro}
                onSubmit={(e) => void handleSubmit(e)}
              />
            )}
          </>
        )}
      </Drawer>
      {confirmDialog}
    </>
  )
}
