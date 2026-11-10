import { FunnelSimpleIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'

import { Popover } from '../feedback/Popover'
import { listarTags, type TagResponse } from './api/tagApi'
import { TagChip } from './TagChip'

function toggleValor(lista: string[], valor: string): string[] {
  return lista.includes(valor) ? lista.filter((item) => item !== valor) : [...lista, valor]
}

type Props = {
  tagIds: string[]
  onAplicar: (tagIds: string[]) => void
  triggerClassName?: string
}

export function TagFiltroPopover({ tagIds, onAplicar, triggerClassName }: Props) {
  const [catalogo, setCatalogo] = useState<TagResponse[]>([])
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    let ativo = true
    async function carregar() {
      setCarregando(true)
      try {
        const lista = await listarTags()
        if (ativo) {
          setCatalogo(lista)
        }
      } catch {
        if (ativo) {
          setCatalogo([])
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

  const ativo = tagIds.length > 0
  const classeGatilho =
    triggerClassName ??
    (ativo ? 'btn btn--secondary btn--sm filtro-gatilho filtro-gatilho--ativo' : 'btn btn--secondary btn--sm filtro-gatilho')

  return (
    <Popover label={<><FunnelSimpleIcon aria-hidden />Tags</>} triggerClassName={classeGatilho}>
      <fieldset className="tag-filtro-fieldset">
        <legend className="visually-hidden">Filtrar por tags</legend>
        {carregando ? (
          <p role="status">Carregando…</p>
        ) : catalogo.length === 0 ? (
          <p role="status">Nenhuma tag ainda.</p>
        ) : (
          catalogo.map((tag) => (
            <label key={tag.id} className="tag-filtro-opcao">
              <input
                type="checkbox"
                checked={tagIds.includes(tag.id)}
                onChange={() => onAplicar(toggleValor(tagIds, tag.id))}
              />
              <TagChip tag={tag} />
            </label>
          ))
        )}
      </fieldset>
    </Popover>
  )
}
