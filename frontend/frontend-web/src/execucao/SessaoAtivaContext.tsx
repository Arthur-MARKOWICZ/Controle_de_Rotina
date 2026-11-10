import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import { BlocoAtivo } from '../home/BlocoAtivo'
import { dataHojeIso } from '../planner/data'
import { mensagensFeedbackXp } from '../xp/feedbackXp'
import { useXpProgress } from '../xp/XpProgressContext'
import {
  abortarExecucao,
  concluirExecucao,
  listarExecucoes,
  tituloDoItemExecucao,
  type ExecucaoResponse,
  type ItemExecucaoResponse,
} from './api/execucaoApi'

export type SessaoAtiva = {
  execucaoId: string | null
  blocoId: string
  titulo: string
  duracaoMinutos: number
  iniciadoEm: string | null
}

type SessaoAtivaContextValue = {
  sessao: SessaoAtiva | null
  ocupado: boolean
  definirSessao: (sessao: SessaoAtiva) => void
  registrarInicio: (titulo: string, duracaoMinutos: number, execucao: ExecucaoResponse) => void
  refrescarDoDia: () => Promise<void>
  limpar: () => void
}

const SessaoAtivaContext = createContext<SessaoAtivaContextValue | null>(null)

function escolherMaisRecente(itens: ItemExecucaoResponse[]): ItemExecucaoResponse | null {
  const andamento = itens.filter((item) => item.execucao.status === 'EM_ANDAMENTO')
  if (andamento.length === 0) {
    return null
  }
  return andamento.reduce((melhor, atual) => {
    const a = melhor.execucao.iniciadoEm ?? ''
    const b = atual.execucao.iniciadoEm ?? ''
    return b >= a ? atual : melhor
  })
}

function itemParaSessao(item: ItemExecucaoResponse): SessaoAtiva | null {
  if (!item.execucao.id) {
    return null
  }
  return {
    execucaoId: item.execucao.id,
    blocoId: item.bloco.id,
    titulo: tituloDoItemExecucao(item),
    duracaoMinutos: item.bloco.duracaoMinutos,
    iniciadoEm: item.execucao.iniciadoEm,
  }
}

export function SessaoAtivaProvider({ children }: { children: ReactNode }) {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const { progresso, aplicarProgresso } = useXpProgress()
  const [sessao, setSessao] = useState<SessaoAtiva | null>(null)
  const [ocupado, setOcupado] = useState(false)

  const refrescarDoDia = useCallback(async () => {
    try {
      const dia = await listarExecucoes(dataHojeIso())
      const ativo = escolherMaisRecente(dia.itens)
      setSessao(ativo ? itemParaSessao(ativo) : null)
    } catch {
      // Mantém sessão local se a listagem falhar (ex.: avulso ainda não listável).
    }
  }, [])

  useEffect(() => {
    void refrescarDoDia()
  }, [refrescarDoDia])

  const definirSessao = useCallback((proxima: SessaoAtiva) => {
    setSessao(proxima)
  }, [])

  const limpar = useCallback(() => {
    setSessao(null)
  }, [])

  const registrarInicio = useCallback(
    (titulo: string, duracaoMinutos: number, execucao: ExecucaoResponse) => {
      if (!execucao.blocoPlanoId) {
        return
      }
      setSessao({
        execucaoId: execucao.id,
        blocoId: execucao.blocoPlanoId,
        titulo,
        duracaoMinutos,
        iniciadoEm: execucao.iniciadoEm,
      })
    },
    [],
  )

  const handleConcluir = useCallback(async () => {
    if (!sessao?.execucaoId) {
      return
    }
    setOcupado(true)
    try {
      const resultado = await concluirExecucao(sessao.execucaoId)
      const xp = resultado.xp
      if (xp) {
        const nivelAnterior = progresso?.nivel ?? null
        for (const mensagem of mensagensFeedbackXp(xp, nivelAnterior)) {
          toast.success(mensagem)
        }
        aplicarProgresso(xp.progresso)
      } else {
        toast.success('Sessão concluída')
      }
      setSessao(null)
      window.dispatchEvent(
        new CustomEvent('sessao-ativa:atualizada', { detail: { xp: xp ?? null } }),
      )
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível concluir a sessão.')
    } finally {
      setOcupado(false)
    }
  }, [aplicarProgresso, progresso?.nivel, sessao, toast])

  const handleAbortar = useCallback(async () => {
    if (!sessao?.execucaoId) {
      return
    }
    const confirmado = await confirm({
      title: 'Abortar execução?',
      message: 'Abortar interrompe o bloco em andamento. Esta ação não pode ser desfeita na UI.',
      confirmLabel: 'Abortar',
    })
    if (!confirmado) {
      return
    }
    setOcupado(true)
    try {
      await abortarExecucao(sessao.execucaoId)
      toast.success('Sessão abortada')
      setSessao(null)
      window.dispatchEvent(new CustomEvent('sessao-ativa:atualizada', { detail: {} }))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível abortar a sessão.')
    } finally {
      setOcupado(false)
    }
  }, [confirm, sessao, toast])

  const value = useMemo(
    () => ({
      sessao,
      ocupado,
      definirSessao,
      registrarInicio,
      refrescarDoDia,
      limpar,
    }),
    [definirSessao, limpar, ocupado, refrescarDoDia, registrarInicio, sessao],
  )

  return (
    <SessaoAtivaContext.Provider value={value}>
      {confirmDialog}
      {children}
      {sessao ? (
        <div className="app-shell__sessao-ativa">
          <BlocoAtivo
            titulo={sessao.titulo}
            duracaoMinutos={sessao.duracaoMinutos}
            iniciadoEm={sessao.iniciadoEm}
            ocupado={ocupado || sessao.execucaoId == null}
            onConcluir={() => void handleConcluir()}
            onAbortar={() => void handleAbortar()}
          />
        </div>
      ) : null}
    </SessaoAtivaContext.Provider>
  )
}

export function useSessaoAtiva(): SessaoAtivaContextValue {
  const ctx = useContext(SessaoAtivaContext)
  if (!ctx) {
    throw new Error('useSessaoAtiva deve ser usado dentro de SessaoAtivaProvider')
  }
  return ctx
}
