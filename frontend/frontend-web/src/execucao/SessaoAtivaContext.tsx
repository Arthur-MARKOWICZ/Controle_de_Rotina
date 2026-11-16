import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useToast } from '../feedback/ToastProvider'
import { BlocoAtivo } from '../home/BlocoAtivo'
import { mensagensFeedbackXp } from '../xp/feedbackXp'
import { useXpProgress } from '../xp/XpProgressContext'
import {
  abortarExecucao,
  buscarSessaoAtiva,
  concluirExecucao,
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
  habitoId?: string | null
}

type OrigemSessao = {
  habitoId?: string
}

type SessaoAtivaContextValue = {
  sessao: SessaoAtiva | null
  ocupado: boolean
  definirSessao: (sessao: SessaoAtiva) => void
  registrarInicio: (
    titulo: string,
    duracaoMinutos: number,
    execucao: ExecucaoResponse,
    origem?: OrigemSessao,
  ) => void
  refrescarDoDia: () => Promise<void>
  limpar: () => void
}

const SessaoAtivaContext = createContext<SessaoAtivaContextValue | null>(null)

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
    habitoId: item.habitoResumo?.id ?? null,
  }
}

export function SessaoAtivaProvider({ children }: { children: ReactNode }) {
  const toast = useToast()
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const { progresso, aplicarProgresso } = useXpProgress()
  const [sessao, setSessao] = useState<SessaoAtiva | null>(null)
  const [ocupado, setOcupado] = useState(false)
  // Cada consulta e cada mudança local avança a versão; resposta de versão antiga é descartada,
  // para uma consulta lenta não trazer de volta uma sessão já encerrada (ou apagar uma nova).
  const versaoRef = useRef(0)

  const aplicarLocal = useCallback((proxima: SessaoAtiva | null) => {
    versaoRef.current += 1
    setSessao(proxima)
  }, [])

  /**
   * Reconstrói a sessão a partir do servidor (qualquer plano, sem filtro de data), para que
   * F5 / nova aba mostrem a sessão em andamento. Falha transitória mantém o estado atual.
   */
  const refrescarDoDia = useCallback(async () => {
    const versao = ++versaoRef.current
    try {
      const ativo = await buscarSessaoAtiva()
      if (versao !== versaoRef.current) {
        return
      }
      setSessao(ativo ? itemParaSessao(ativo) : null)
    } catch {
      // Mantém a sessão já exibida se a consulta falhar (rede, 5xx, HTML do proxy).
    }
  }, [])

  useEffect(() => {
    void refrescarDoDia()
  }, [refrescarDoDia])

  const definirSessao = useCallback(
    (proxima: SessaoAtiva) => {
      aplicarLocal(proxima)
    },
    [aplicarLocal],
  )

  const limpar = useCallback(() => {
    aplicarLocal(null)
  }, [aplicarLocal])

  const registrarInicio = useCallback(
    (
      titulo: string,
      duracaoMinutos: number,
      execucao: ExecucaoResponse,
      origem?: OrigemSessao,
    ) => {
      if (!execucao.blocoPlanoId) {
        return
      }
      aplicarLocal({
        execucaoId: execucao.id,
        blocoId: execucao.blocoPlanoId,
        titulo,
        duracaoMinutos,
        iniciadoEm: execucao.iniciadoEm,
        habitoId: origem?.habitoId ?? null,
      })
    },
    [aplicarLocal],
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
      aplicarLocal(null)
      window.dispatchEvent(
        new CustomEvent('sessao-ativa:atualizada', { detail: { xp: xp ?? null } }),
      )
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível concluir a sessão.')
    } finally {
      setOcupado(false)
    }
  }, [aplicarLocal, aplicarProgresso, progresso?.nivel, sessao, toast])

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
      aplicarLocal(null)
      window.dispatchEvent(new CustomEvent('sessao-ativa:atualizada', { detail: {} }))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível abortar a sessão.')
    } finally {
      setOcupado(false)
    }
  }, [aplicarLocal, confirm, sessao, toast])

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
