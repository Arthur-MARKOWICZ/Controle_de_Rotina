import { PlusIcon, TagIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { Popover } from '../feedback/Popover'
import {
  TAG_COR_PADRAO,
  criarTag,
  listarTags,
  type TagResumo,
  type TagResponse,
} from './api/tagApi'
import { TagChip } from './TagChip'

type Props = {
  selectedIds: string[]
  selectedTags?: TagResumo[]
  onChange: (tagIds: string[], tags: TagResumo[]) => void
  disabled?: boolean
}

function mesclarTags(...listas: (TagResumo[] | undefined)[]): Map<string, TagResumo> {
  const map = new Map<string, TagResumo>()
  for (const lista of listas) {
    for (const tag of lista ?? []) {
      map.set(tag.id, tag)
    }
  }
  return map
}

export function TagPicker({ selectedIds, selectedTags, onChange, disabled = false }: Props) {
  const [catalogo, setCatalogo] = useState<TagResponse[]>([])
  const [busca, setBusca] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [criando, setCriando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const recarregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const lista = await listarTags()
      setCatalogo(lista)
    } catch {
      setErro('Não foi possível carregar as tags.')
      setCatalogo([])
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    void recarregar()
  }, [recarregar])

  const porId = useMemo(
    () => mesclarTags(catalogo, selectedTags),
    [catalogo, selectedTags],
  )

  const selecionadas = useMemo(
    () =>
      selectedIds
        .map((id) => porId.get(id))
        .filter((tag): tag is TagResumo => tag != null),
    [porId, selectedIds],
  )

  const buscaNorm = busca.trim().toLowerCase()
  const filtradas = useMemo(() => {
    if (!buscaNorm) {
      return catalogo
    }
    return catalogo.filter((tag) => tag.nome.toLowerCase().includes(buscaNorm))
  }, [buscaNorm, catalogo])

  const nomeNovo = busca.trim()
  const podeCriar =
    nomeNovo.length > 0 &&
    !catalogo.some((tag) => tag.nome.toLowerCase() === nomeNovo.toLowerCase())

  function emitir(proximosIds: string[]) {
    const tags = proximosIds
      .map((id) => porId.get(id))
      .filter((tag): tag is TagResumo => tag != null)
    onChange(proximosIds, tags)
  }

  function alternar(id: string) {
    if (disabled) {
      return
    }
    const proximos = selectedIds.includes(id)
      ? selectedIds.filter((item) => item !== id)
      : [...selectedIds, id]
    emitir(proximos)
  }

  async function handleCriar() {
    if (!podeCriar || disabled || criando) {
      return
    }
    setCriando(true)
    setErro(null)
    try {
      const criada = await criarTag({ nome: nomeNovo, cor: TAG_COR_PADRAO })
      setCatalogo((atual) => [...atual, criada].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')))
      const proximos = [...selectedIds, criada.id]
      const tags = proximos
        .map((id) => (id === criada.id ? criada : porId.get(id)))
        .filter((tag): tag is TagResumo => tag != null)
      onChange(proximos, tags)
      setBusca('')
    } catch {
      setErro('Não foi possível criar a tag.')
    } finally {
      setCriando(false)
    }
  }

  return (
    <div className="field tag-picker">
      <span className="field-label" id="tag-picker-label">
        Tags
      </span>
      {selecionadas.length ? (
        <div className="tag-picker__selecionadas" aria-live="polite">
          {selecionadas.map((tag) => (
            <button
              key={tag.id}
              type="button"
              className="tag-picker__chip-remover"
              disabled={disabled}
              aria-label={`Remover tag ${tag.nome}`}
              onClick={() => alternar(tag.id)}
            >
              <TagChip tag={tag} />
            </button>
          ))}
        </div>
      ) : null}
      <Popover
        label={
          <>
            <TagIcon aria-hidden />
            Adicionar tags
          </>
        }
        triggerClassName="btn btn--secondary btn--sm"
        ariaLabel="Escolher tags"
      >
        <div className="tag-picker__painel" role="group" aria-labelledby="tag-picker-label">
          <input
            type="search"
            className="tag-picker__busca"
            placeholder="Buscar ou criar…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            disabled={disabled || carregando}
            aria-label="Buscar tag"
          />
          {erro ? <p role="alert">{erro}</p> : null}
          {carregando ? (
            <p role="status">Carregando tags…</p>
          ) : (
            <ul className="tag-picker__opcoes">
              {filtradas.map((tag) => {
                const ativa = selectedIds.includes(tag.id)
                return (
                  <li key={tag.id}>
                    <button
                      type="button"
                      className={
                        ativa
                          ? 'tag-picker__opcao tag-picker__opcao--ativa'
                          : 'tag-picker__opcao'
                      }
                      onClick={() => alternar(tag.id)}
                      disabled={disabled}
                    >
                      <TagChip tag={tag} />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
          {podeCriar ? (
            <button
              type="button"
              className="btn btn--secondary btn--sm tag-picker__criar"
              disabled={disabled || criando}
              onClick={() => void handleCriar()}
            >
              <PlusIcon aria-hidden />
              {criando ? 'Criando…' : `Criar “${nomeNovo}”`}
            </button>
          ) : null}
          <div className="tag-picker__rodape">
            <Link to="/tags" className="btn btn--ghost btn--sm" onClick={() => setBusca('')}>
              Gerenciar
            </Link>
          </div>
        </div>
      </Popover>
    </div>
  )
}
