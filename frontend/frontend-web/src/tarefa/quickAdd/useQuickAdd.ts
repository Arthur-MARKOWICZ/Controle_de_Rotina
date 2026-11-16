import { useCallback, useEffect, useMemo, useState } from 'react'

import { TAG_COR_PADRAO, criarTag, listarTags, type TagResumo } from '../../tag/api/tagApi'
import type { Prioridade } from '../validation/tarefaForm'
import {
  chaveTag,
  interpretarQuickAdd,
  normalizarToken,
  type ResultadoQuickAdd,
  type TokenQuickAdd,
  type TokenTag,
} from './interpretarQuickAdd'

type Opcoes = {
  texto: string
  /** Carrega o catálogo de tags quando passa a `true` (ex.: ao abrir o diálogo). */
  ativo: boolean
  referencia?: Date
}

export type CamposQuickAdd = { duracaoMinutos?: number; prazo?: string; prioridade?: Prioridade }

/** Campos da criação de tarefa vindos dos chips (sem `tagIds`, que dependem de criar tags). */
export function camposDoResultado(resultado: ResultadoQuickAdd): CamposQuickAdd {
  const campos: CamposQuickAdd = {}
  if (resultado.duracao) {
    campos.duracaoMinutos = resultado.duracao.minutos
  }
  if (resultado.prazo) {
    campos.prazo = resultado.prazo.prazoIso
  }
  if (resultado.prioridade) {
    campos.prioridade = resultado.prioridade.prioridade
  }
  return campos
}

/** Estado compartilhado do quick add e do inline add: catálogo, rejeitados e tags confirmadas. */
export function useQuickAdd({ texto, ativo, referencia }: Opcoes) {
  const [catalogo, setCatalogo] = useState<TagResumo[]>([])
  const [rejeitados, setRejeitados] = useState<ReadonlySet<string>>(() => new Set())
  const [confirmadas, setConfirmadas] = useState<ReadonlySet<string>>(() => new Set())

  useEffect(() => {
    if (!ativo) {
      return
    }
    let cancelado = false
    listarTags()
      .then((tags) => {
        if (!cancelado) {
          setCatalogo(tags)
        }
      })
      .catch(() => {
        // Sem catálogo, toda #tag fica pendente de confirmação.
        if (!cancelado) {
          setCatalogo([])
        }
      })
    return () => {
      cancelado = true
    }
  }, [ativo])

  const resultado = useMemo(
    () => interpretarQuickAdd(texto, { ref: referencia, tagsCatalogo: catalogo, rejeitados }),
    [texto, referencia, catalogo, rejeitados],
  )

  const rejeitar = useCallback((token: TokenQuickAdd) => {
    setRejeitados((atual) => new Set(atual).add(normalizarToken(token.textoOriginal)))
  }, [])

  const alternarTag = useCallback((token: TokenTag) => {
    const chave = chaveTag(token.nome)
    setConfirmadas((atual) => {
      const proximo = new Set(atual)
      if (proximo.has(chave)) {
        proximo.delete(chave)
      } else {
        proximo.add(chave)
      }
      return proximo
    })
  }, [])

  const resetar = useCallback(() => {
    setRejeitados(new Set())
    setConfirmadas(new Set())
  }, [])

  /** Tags existentes dos chips (para levar ao formulário completo). */
  const tagsExistentes = useMemo(
    () => resultado.tags.flatMap((token) => (token.tag ? [token.tag] : [])),
    [resultado],
  )

  /**
   * Ids das tags a vincular: existentes + novas confirmadas (criadas agora, em sequência).
   * Pendentes não confirmadas ficam de fora. Lança o erro de `criarTag` se a criação falhar.
   */
  const resolverTagIds = useCallback(async (): Promise<string[]> => {
    const ids: string[] = []
    for (const token of resultado.tags) {
      if (token.tag) {
        ids.push(token.tag.id)
        continue
      }
      if (!confirmadas.has(chaveTag(token.nome))) {
        continue
      }
      const criada = await criarTag({ nome: token.nome, cor: TAG_COR_PADRAO })
      setCatalogo((atual) => [...atual, criada])
      ids.push(criada.id)
    }
    return ids
  }, [resultado, confirmadas])

  return { resultado, confirmadas, rejeitar, alternarTag, resetar, resolverTagIds, tagsExistentes }
}
