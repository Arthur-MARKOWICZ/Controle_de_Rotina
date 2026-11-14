import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom'

import { ApiError } from '../auth/api/authApi'
import { Drawer } from '../feedback/Drawer'
import { InlineAlert } from '../feedback/InlineAlert'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import type { TagResumo } from '../tag/api/tagApi'
import {
  atualizarHabito,
  buscarHabito,
  criarHabito,
  excluirHabito,
  restaurarHabito,
  type HabitoResponse,
} from './api/habitoApi'
import { HabitoCampos } from './HabitoCampos'
import { HabitoHistoricoSecao } from './HabitoHistoricoSecao'
import {
  HABITO_FORM_VAZIO,
  primeiroErroHabito,
  validarHabitoForm,
  type HabitoFormValues,
} from './validation/habitoForm'

export type HabitosOutletContext = {
  aoAtualizar: (habito: HabitoResponse) => void
  aoCriar: (habito: HabitoResponse) => void
}

/** `location.state` aceito pelas rotas do drawer. */
export type HabitoDrawerState = {
  /** Aberto a partir da lista: fechar volta no histórico em vez de empilhar `/habitos`. */
  daLista?: boolean
}

const FORM_ID = 'habito-drawer-form'

function valoresDoHabito(habito: HabitoResponse): HabitoFormValues {
  return {
    titulo: habito.titulo,
    descricao: habito.descricao ?? '',
    duracaoMinutos: habito.duracaoMinutos,
    diasSemana: [...habito.diasSemana],
    horarioPreferencial: habito.horarioPreferencial
      ? habito.horarioPreferencial.slice(0, 5)
      : '',
  }
}

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

function tagsDoHabito(habito: HabitoResponse): { ids: string[]; tags: TagResumo[] } {
  const tags = habito.tags ?? []
  return { ids: tags.map((tag) => tag.id), tags }
}

export function HabitoDrawer() {
  const { id } = useParams<{ id: string }>()
  const modo: 'criar' | 'editar' = id ? 'editar' : 'criar'
  const navigate = useNavigate()
  const location = useLocation()
  const estado = (location.state ?? {}) as HabitoDrawerState
  const { aoAtualizar, aoCriar } = useOutletContext<HabitosOutletContext>()
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()

  const [inicial, setInicial] = useState<HabitoFormValues>(HABITO_FORM_VAZIO)
  const [values, setValues] = useState<HabitoFormValues>(HABITO_FORM_VAZIO)
  const [tagIds, setTagIds] = useState<string[]>([])
  const [tagsSelecionadas, setTagsSelecionadas] = useState<TagResumo[]>([])
  const [tagIdsInicial, setTagIdsInicial] = useState<string[]>([])
  const [habito, setHabito] = useState<HabitoResponse | null>(null)
  const [carregando, setCarregando] = useState(modo === 'editar')
  const [enviando, setEnviando] = useState(false)
  const [excluindo, setExcluindo] = useState(false)
  const [restaurando, setRestaurando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aberto, setAberto] = useState(true)
  const confirmandoRef = useRef(false)

  const deletada = habito?.deletada ?? false
  const alterado =
    JSON.stringify(values) !== JSON.stringify(inicial) ||
    JSON.stringify(tagIds) !== JSON.stringify(tagIdsInicial)

  const voltarParaLista = useCallback(() => {
    if (estado.daLista) {
      navigate(-1)
      return
    }
    navigate({ pathname: '/habitos', search: location.search }, { replace: true })
  }, [estado.daLista, location.search, navigate])

  useEffect(() => {
    if (modo !== 'editar' || !id) {
      return
    }
    const habitoId = id
    let ativo = true
    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const data = await buscarHabito(habitoId)
        if (!ativo) {
          return
        }
        const form = valoresDoHabito(data)
        setHabito(data)
        const ids = data.tags?.map((tag) => tag.id) ?? []
        setInicial(form)
        setValues(form)
        setTagIds(ids)
        setTagIdsInicial(ids)
        setTagsSelecionadas(data.tags ?? [])
      } catch (error) {
        if (ativo) {
          setErro(mensagemErro(error, 'Não foi possível carregar o hábito.'))
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
    if (alterado && !deletada) {
      confirmandoRef.current = true
      const descartar = await confirm({
        title: 'Descartar alterações?',
        message: 'O que você alterou neste hábito será perdido.',
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

    const validacao = validarHabitoForm(values)
    const mensagem = primeiroErroHabito(validacao)
    if (mensagem) {
      setErro(mensagem)
      return
    }

    const descricao = values.descricao.trim()
    const horario = values.horarioPreferencial.trim() || null
    const body = {
      titulo: values.titulo.trim(),
      descricao: descricao || null,
      duracaoMinutos: values.duracaoMinutos as number,
      diasSemana: values.diasSemana,
      horarioPreferencial: horario,
      tagIds,
    }

    setEnviando(true)
    try {
      if (modo === 'criar') {
        const criado = await criarHabito(body)
        toast.success('Hábito criado')
        aoCriar(criado)
        navigate(`/habitos/${criado.id}`, { replace: true, state: { daLista: true } })
        return
      }
      if (!id) {
        return
      }
      const atualizado = await atualizarHabito(id, body)
      toast.success('Hábito atualizado')
      aoAtualizar(atualizado)
      const form = valoresDoHabito(atualizado)
      setHabito(atualizado)
      const { ids, tags } = tagsDoHabito(atualizado)
      setInicial(form)
      setValues(form)
      setTagIds(ids)
      setTagIdsInicial(ids)
      setTagsSelecionadas(tags)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível salvar o hábito. Tente novamente.'))
    } finally {
      setEnviando(false)
    }
  }

  async function handleExcluir() {
    if (!id || !habito || deletada) {
      return
    }
    const confirmado = await confirm({
      title: 'Excluir hábito?',
      message: `“${habito.titulo}” deixará de entrar no plano. Você pode restaurá-lo depois.`,
      confirmLabel: 'Excluir',
    })
    if (!confirmado) {
      return
    }
    setExcluindo(true)
    setErro(null)
    try {
      await excluirHabito(id)
      toast.success('Hábito excluído')
      aoAtualizar({ ...habito, deletada: true, deletedAt: new Date().toISOString() })
      setAberto(false)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível excluir o hábito.'))
    } finally {
      setExcluindo(false)
    }
  }

  async function handleRestaurar() {
    if (!id) {
      return
    }
    setRestaurando(true)
    setErro(null)
    try {
      const restaurado = await restaurarHabito(id)
      toast.success('Hábito restaurado')
      aoAtualizar(restaurado)
      const form = valoresDoHabito(restaurado)
      setHabito(restaurado)
      const { ids, tags } = tagsDoHabito(restaurado)
      setInicial(form)
      setValues(form)
      setTagIds(ids)
      setTagIdsInicial(ids)
      setTagsSelecionadas(tags)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível restaurar o hábito.'))
    } finally {
      setRestaurando(false)
    }
  }

  const tituloDrawer =
    modo === 'criar' ? 'Novo hábito' : deletada ? 'Hábito excluído' : 'Editar hábito'

  const rodape = (
    <>
      <button type="button" className="btn btn--secondary" onClick={() => void pedirFechar()}>
        {modo === 'criar' ? 'Cancelar' : 'Fechar'}
      </button>
      {modo === 'editar' && deletada ? (
        <button
          type="button"
          className="btn btn--primary"
          disabled={restaurando}
          aria-busy={restaurando}
          onClick={() => void handleRestaurar()}
        >
          {restaurando ? 'Restaurando…' : 'Restaurar'}
        </button>
      ) : (
        <button
          type="submit"
          form={FORM_ID}
          className="btn btn--primary"
          disabled={enviando || carregando || deletada}
          aria-busy={enviando}
        >
          {enviando ? 'Salvando…' : modo === 'criar' ? 'Criar hábito' : 'Salvar'}
        </button>
      )}
    </>
  )

  return (
    <>
      <Drawer
        open={aberto}
        title={tituloDrawer}
        onClose={() => void pedirFechar()}
        onExited={voltarParaLista}
        footer={rodape}
      >
        {modo === 'criar' ? (
          <p className="tarefa-drawer__subtitulo">
            O hábito inicia ativo e entra no plano nos dias escolhidos.
          </p>
        ) : null}
        {carregando ? <p className="plano-dia-meta">Carregando…</p> : null}
        {!carregando ? (
          <form
            id={FORM_ID}
            className="habito-form"
            onSubmit={(e) => void handleSubmit(e)}
            noValidate
          >
            <HabitoCampos
              values={values}
              onChange={setValues}
              desabilitado={deletada}
              tagIds={tagIds}
              tagsSelecionadas={tagsSelecionadas}
              onTagsChange={(ids, tags) => {
                setTagIds(ids)
                setTagsSelecionadas(tags)
              }}
            />
            {habito && !deletada ? (
              <p className="habito-streak-detalhe" role="status">
                Sequência atual: {habito.streakAtual} · Melhor: {habito.melhorStreak}
              </p>
            ) : null}
            {erro ? <InlineAlert>{erro}</InlineAlert> : null}
            {modo === 'editar' && !deletada ? (
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                disabled={excluindo}
                aria-busy={excluindo}
                onClick={() => void handleExcluir()}
              >
                {excluindo ? 'Excluindo…' : 'Excluir hábito'}
              </button>
            ) : null}
          </form>
        ) : null}
        {modo === 'editar' && id ? <HabitoHistoricoSecao habitoId={id} /> : null}
      </Drawer>
      {confirmDialog}
    </>
  )
}
