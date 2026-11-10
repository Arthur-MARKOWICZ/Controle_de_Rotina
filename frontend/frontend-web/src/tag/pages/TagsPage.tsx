import { PlusIcon, TrashIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '../../auth/api/authApi'
import { InlineAlert } from '../../feedback/InlineAlert'
import { Skeleton } from '../../feedback/Skeleton'
import { useConfirmDialog } from '../../feedback/useConfirmDialog'
import { useToast } from '../../feedback/ToastProvider'
import { PageHeader } from '../../layout/PageHeader'
import {
  TAG_COR_PADRAO,
  atualizarTag,
  criarTag,
  excluirTag,
  listarTags,
  type TagResponse,
} from '../api/tagApi'
import { TagChip } from '../TagChip'

function mensagemErro(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

export function TagsPage() {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const [tags, setTags] = useState<TagResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [nomeNovo, setNomeNovo] = useState('')
  const [corNovo, setCorNovo] = useState(TAG_COR_PADRAO)
  const [criando, setCriando] = useState(false)
  const [salvandoId, setSalvandoId] = useState<string | null>(null)
  const [excluindoId, setExcluindoId] = useState<string | null>(null)

  const recarregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const lista = await listarTags()
      setTags(lista)
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível carregar as tags.'))
      setTags([])
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    void recarregar()
  }, [recarregar])

  async function handleCriar(event: React.FormEvent) {
    event.preventDefault()
    const nome = nomeNovo.trim()
    if (!nome) {
      return
    }
    setCriando(true)
    setErro(null)
    try {
      const criada = await criarTag({ nome, cor: corNovo || TAG_COR_PADRAO })
      setTags((atual) => [...atual, criada].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')))
      setNomeNovo('')
      setCorNovo(TAG_COR_PADRAO)
      toast.success('Tag criada')
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível criar a tag.'))
    } finally {
      setCriando(false)
    }
  }

  async function salvarTag(tag: TagResponse, nome: string, cor: string) {
    const nomeTrim = nome.trim()
    if (!nomeTrim || (nomeTrim === tag.nome && cor === tag.cor)) {
      return
    }
    setSalvandoId(tag.id)
    setErro(null)
    try {
      const atualizada = await atualizarTag(tag.id, { nome: nomeTrim, cor })
      setTags((atual) =>
        atual
          .map((item) => (item.id === tag.id ? atualizada : item))
          .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
      )
      toast.success('Tag atualizada')
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível atualizar a tag.'))
    } finally {
      setSalvandoId(null)
    }
  }

  async function handleExcluir(tag: TagResponse) {
    const confirmado = await confirm({
      title: 'Apagar tag?',
      message: `“${tag.nome}” será removida do catálogo e desvinculada de tarefas, hábitos e projetos.`,
      confirmLabel: 'Apagar',
    })
    if (!confirmado) {
      return
    }
    setExcluindoId(tag.id)
    setErro(null)
    try {
      await excluirTag(tag.id)
      setTags((atual) => atual.filter((item) => item.id !== tag.id))
      toast.success('Tag apagada')
    } catch (error) {
      setErro(mensagemErro(error, 'Não foi possível apagar a tag.'))
    } finally {
      setExcluindoId(null)
    }
  }

  return (
    <main className="tags-page">
      {confirmDialog}
      <PageHeader
        title="Tags"
        description="Organize tarefas, hábitos e projetos com rótulos coloridos."
      />

      {erro ? <InlineAlert>{erro}</InlineAlert> : null}

      <form className="tags-criar card" onSubmit={(e) => void handleCriar(e)}>
        <div className="field tags-item__nome">
          <label htmlFor="tag-nome-nova">Nova tag</label>
          <input
            id="tag-nome-nova"
            type="text"
            maxLength={40}
            value={nomeNovo}
            onChange={(e) => setNomeNovo(e.target.value)}
            disabled={criando}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="tag-cor-nova">Cor</label>
          <input
            id="tag-cor-nova"
            type="color"
            className="tags-item__cor"
            value={corNovo}
            onChange={(e) => setCorNovo(e.target.value)}
            disabled={criando}
          />
        </div>
        <button type="submit" className="btn btn--primary" disabled={criando || !nomeNovo.trim()}>
          <PlusIcon aria-hidden />
          {criando ? 'Criando…' : 'Criar'}
        </button>
      </form>

      {carregando ? <Skeleton variant="tarefas-lista" /> : null}

      {!carregando ? (
        tags.length === 0 ? (
          <p role="status">Nenhuma tag ainda. Crie a primeira acima.</p>
        ) : (
          <ul className="tags-lista" aria-label="Suas tags">
            {tags.map((tag) => (
              <TagLinha
                key={tag.id}
                tag={tag}
                salvando={salvandoId === tag.id}
                excluindo={excluindoId === tag.id}
                onSalvar={salvarTag}
                onExcluir={() => void handleExcluir(tag)}
              />
            ))}
          </ul>
        )
      ) : null}
    </main>
  )
}

type LinhaProps = {
  tag: TagResponse
  salvando: boolean
  excluindo: boolean
  onSalvar: (tag: TagResponse, nome: string, cor: string) => void
  onExcluir: () => void
}

function TagLinha({ tag, salvando, excluindo, onSalvar, onExcluir }: LinhaProps) {
  const [nome, setNome] = useState(tag.nome)
  const [cor, setCor] = useState(tag.cor)

  useEffect(() => {
    setNome(tag.nome)
    setCor(tag.cor)
  }, [tag.nome, tag.cor])

  return (
    <li className="tags-item">
      <TagChip tag={{ ...tag, cor }} />
      <div className="tags-item__campos">
        <div className="field tags-item__nome">
          <label className="visually-hidden" htmlFor={`tag-nome-${tag.id}`}>
            Nome
          </label>
          <input
            id={`tag-nome-${tag.id}`}
            type="text"
            maxLength={40}
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            onBlur={() => void onSalvar(tag, nome, cor)}
            disabled={salvando || excluindo}
          />
        </div>
        <input
          type="color"
          className="tags-item__cor"
          value={cor}
          aria-label={`Cor de ${tag.nome}`}
          onChange={(e) => setCor(e.target.value)}
          onBlur={(e) => void onSalvar(tag, nome, e.currentTarget.value)}
          disabled={salvando || excluindo}
        />
      </div>
      <button
        type="button"
        className="btn btn--ghost btn--danger btn--sm btn--icon"
        aria-label={`Apagar tag ${tag.nome}`}
        disabled={excluindo || salvando}
        onClick={onExcluir}
      >
        <TrashIcon aria-hidden />
      </button>
    </li>
  )
}
