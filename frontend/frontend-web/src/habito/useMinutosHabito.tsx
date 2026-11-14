import { useCallback, useState } from 'react'

import { MinutosHabitoDialog } from './MinutosHabitoDialog'

type PedidoMinutos = {
  titulo: string
  duracaoMinutos: number
  resolve: (minutos: number | null) => void
}

/**
 * Abre o diálogo de minutos do concluir direto de hábito.
 * `pedirMinutos` resolve com os minutos confirmados ou `null` se o usuário cancelar.
 */
export function useMinutosHabito() {
  const [pedido, setPedido] = useState<PedidoMinutos | null>(null)

  const pedirMinutos = useCallback((titulo: string, duracaoMinutos: number) => {
    return new Promise<number | null>((resolve) => {
      setPedido({ titulo, duracaoMinutos, resolve })
    })
  }, [])

  const dialog = pedido ? (
    <MinutosHabitoDialog
      open
      tituloHabito={pedido.titulo}
      duracaoMinutos={pedido.duracaoMinutos}
      onConfirm={(minutos) => {
        pedido.resolve(minutos)
        setPedido(null)
      }}
      onClose={() => {
        pedido.resolve(null)
        setPedido(null)
      }}
    />
  ) : null

  return { pedirMinutos, dialog }
}
