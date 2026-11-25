import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import { CampoSenha } from './CampoSenha'

/** Palavra que o usuário digita para liberar a exclusão. */
export const PALAVRA_CONFIRMACAO_EXCLUSAO = 'EXCLUIR'

type Props = {
  open: boolean
  ocupado: boolean
  /** Erro vindo da API (ex.: senha incorreta), mostrado no campo de senha. */
  erroSenha: string | null
  /** Erro geral (servidor/rede), mostrado acima das ações. */
  erroGeral: string | null
  onConfirm: (senhaAtual: string) => void
  onExportar: () => void
  onClose: () => void
}

/** Confirmação forte da exclusão de conta: explica, sugere exportar, pede senha e `EXCLUIR`. */
export function ExcluirContaDialog({
  open,
  ocupado,
  erroSenha,
  erroGeral,
  onConfirm,
  onExportar,
  onClose,
}: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const messageId = useId()
  const confirmacaoId = useId()
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    if (open) {
      if (!dialog.open) {
        dialog.showModal()
      }
    } else if (dialog.open) {
      dialog.close()
    }
  }, [open])

  useEffect(() => {
    if (open) {
      setSenha('')
      setConfirmacao('')
    }
  }, [open])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    function handleCancel(event: Event) {
      event.preventDefault()
      if (!ocupado) {
        onClose()
      }
    }
    dialog.addEventListener('cancel', handleCancel)
    return () => dialog.removeEventListener('cancel', handleCancel)
  }, [onClose, ocupado])

  const liberado = senha.length > 0 && confirmacao === PALAVRA_CONFIRMACAO_EXCLUSAO && !ocupado

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (liberado) {
      onConfirm(senha)
    }
  }

  if (!open) {
    return null
  }

  return (
    <dialog
      ref={dialogRef}
      className="confirm-dialog excluir-conta-dialog"
      aria-labelledby={titleId}
      aria-describedby={messageId}
    >
      <form method="dialog" className="confirm-dialog__form" onSubmit={handleSubmit} noValidate>
        <h2 id={titleId} className="confirm-dialog__title">
          Excluir conta
        </h2>
        <div id={messageId} className="confirm-dialog__message">
          <p>
            Isso apaga sua conta e todos os seus dados: tarefas, projetos, hábitos, planos, histórico
            e XP. <strong>Não dá para desfazer.</strong>
          </p>
          <p>Quer guardar uma cópia antes?</p>
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            onClick={onExportar}
            disabled={ocupado}
          >
            Exportar meus dados
          </button>
        </div>

        <CampoSenha
          id="excluir-conta-senha"
          label="Senha atual"
          value={senha}
          onChange={setSenha}
          autoComplete="current-password"
          erro={erroSenha ?? undefined}
          required
        />

        <div className="field">
          <label htmlFor={confirmacaoId}>
            Digite <strong>{PALAVRA_CONFIRMACAO_EXCLUSAO}</strong> para confirmar
          </label>
          <input
            id={confirmacaoId}
            type="text"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.target.value)}
            disabled={ocupado}
          />
        </div>

        {erroGeral ? (
          <p className="inline-alert inline-alert--error" role="alert">
            {erroGeral}
          </p>
        ) : null}

        <div className="confirm-dialog__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={ocupado}>
            Cancelar
          </button>
          <button
            type="submit"
            className="btn btn--danger"
            disabled={!liberado}
            aria-busy={ocupado}
          >
            {ocupado ? 'Excluindo…' : 'Excluir conta definitivamente'}
          </button>
        </div>
      </form>
    </dialog>
  )
}
