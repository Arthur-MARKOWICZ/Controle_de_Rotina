import { useCallback, useState } from 'react'

import { ConfirmDialog } from './ConfirmDialog'

type ConfirmRequest = {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
}

type PendingConfirm = ConfirmRequest & {
  resolve: (confirmed: boolean) => void
}

export function useConfirmDialog() {
  const [pending, setPending] = useState<PendingConfirm | null>(null)

  const confirm = useCallback((request: ConfirmRequest) => {
    return new Promise<boolean>((resolve) => {
      setPending({ ...request, resolve })
    })
  }, [])

  const dialog = pending ? (
    <ConfirmDialog
      open
      title={pending.title}
      message={pending.message}
      confirmLabel={pending.confirmLabel}
      cancelLabel={pending.cancelLabel}
      onConfirm={() => {
        pending.resolve(true)
        setPending(null)
      }}
      onCancel={() => {
        pending.resolve(false)
        setPending(null)
      }}
    />
  ) : null

  return { confirm, dialog }
}
