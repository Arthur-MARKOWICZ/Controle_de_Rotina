import { useEffect, useState, type FormEvent } from 'react'

import { ApiError } from '../auth/api/authApi'
import {
  atualizarCapacidadeOverride,
  criarCapacidadeOverride,
  listarCapacidadesOverride,
} from '../capacidade/api/capacidadeOverrideApi'
import {
  consultarCapacidadeResolvida,
  type CapacidadeResolvidaResponse,
} from '../capacidade/api/capacidadeResolvidaApi'
import {
  abortarExecucao,
  concluirExecucao,
  iniciarExecucao,
  listarExecucoes,
  type ItemExecucaoResponse,
} from '../execucao/api/execucaoApi'
import { minutosRestantes } from '../execucao/data'
import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { useToast } from '../feedback/ToastProvider'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { mensagensFeedbackXp } from '../xp/feedbackXp'
import { useXpProgress } from '../xp/XpProgressContext'
import { AdicionarTarefaForm } from '../home/AdicionarTarefaForm'
import { AjustarCapacidadeHoje } from '../home/AjustarCapacidadeHoje'
import { BlocoAtivo } from '../home/BlocoAtivo'
import { BlocoCard } from '../home/BlocoCard'
import { CapacidadeBar } from '../home/CapacidadeBar'
import { ConflitoBanner } from '../home/ConflitoBanner'
import { EmptyStatePlano } from '../home/EmptyStatePlano'
import { formatarDataPorExtenso } from '../home/formatacao'
import { OnboardingChecklist } from '../home/OnboardingChecklist'
import { escolherBlocoMenorPrioridade } from '../home/justificativa'
import { labelStatusPlano } from '../home/labels'
import {
  aceitarProposta,
  adicionarBloco,
  aprovarPlano,
  buscarPlano,
  descartarProposta,
  gerarPlano,
  listarDatasDaProposta,
  removerBloco,
  type BlocoPlanoResponse,
  type PlanoResponse,
} from '../planner/api/planoApi'
import {
  dataHojeIso,
  filtrarElegiveisForaDoPlano,
  formatarCapacidadeMinutos,
  minutosDisponiveis,
  minutosUsados,
  statusElegiveisParaPlano,
} from '../planner/data'
import { listarTarefas, type TarefaResponse } from '../tarefa/api/tarefaApi'
import { QuickAddTarefaDialog } from '../tarefa/QuickAddTarefaDialog'

type SnapshotBlocos = {
  /** IDs de tarefa (bloco.id muda a cada versão). */
  tarefaIds: Set<string>
  titulosPorTarefaId: Map<string, string>
}

function capacidadeDoDia(
  resolvidas: CapacidadeResolvidaResponse[],
  data: string,
): CapacidadeResolvidaResponse | undefined {
  return resolvidas.find((item) => item.data === data)
}

function mensagemErro(error: unknown, fallback: string): string {
  // 409 (e demais erros) já vêm com detail/title do ProblemDetail via lerErroApi.
  if (error instanceof ApiError) {
    return error.message || fallback
  }
  if (error instanceof Error) {
    return error.message || fallback
  }
  return fallback
}

function snapshotDeBlocos(blocos: BlocoPlanoResponse[]): SnapshotBlocos {
  return {
    tarefaIds: new Set(blocos.map((bloco) => bloco.tarefa.id)),
    titulosPorTarefaId: new Map(blocos.map((bloco) => [bloco.tarefa.id, bloco.tarefa.titulo])),
  }
}

export function HomePage() {
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const toast = useToast()
  const { progresso, aplicarProgresso } = useXpProgress()
  const [plano, setPlano] = useState<PlanoResponse | null>(null)
  const [capacidade, setCapacidade] = useState<CapacidadeResolvidaResponse | undefined>()
  const [itensExecucao, setItensExecucao] = useState<ItemExecucaoResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoErro, setAcaoErro] = useState<string | null>(null)
  const [aprovando, setAprovando] = useState(false)
  const [resolvendoProposta, setResolvendoProposta] = useState(false)
  const [removendoBlocoId, setRemovendoBlocoId] = useState<string | null>(null)
  const [acaoBlocoId, setAcaoBlocoId] = useState<string | null>(null)
  const [elegiveis, setElegiveis] = useState<TarefaResponse[]>([])
  const [tarefaSelecionadaId, setTarefaSelecionadaId] = useState('')
  const [adicionando, setAdicionando] = useState(false)
  const [carregandoElegiveis, setCarregandoElegiveis] = useState(false)
  const [gerando, setGerando] = useState(false)
  const [agora, setAgora] = useState(() => new Date())
  const [conflitoDismissed, setConflitoDismissed] = useState(false)
  const [snapshotAnterior, setSnapshotAnterior] = useState<SnapshotBlocos | null>(null)
  const [titulosRemovidosDiff, setTitulosRemovidosDiff] = useState<string[]>([])
  const [xpGanhoSessao, setXpGanhoSessao] = useState(0)
  const [quickAddAberto, setQuickAddAberto] = useState(false)
  const [elegiveisTick, setElegiveisTick] = useState(0)
  const [ajustarCapacidadeAberto, setAjustarCapacidadeAberto] = useState(false)
  const [salvandoCapacidadeHoje, setSalvandoCapacidadeHoje] = useState(false)
  const [ctaGerarAposCapacidade, setCtaGerarAposCapacidade] = useState(false)
  const [dataSelecionada, setDataSelecionada] = useState(() => dataHojeIso())
  const [datasProposta, setDatasProposta] = useState<string[]>([])

  const ehRascunho = plano?.status === 'RASCUNHO'
  const ehAprovado = plano?.status === 'APROVADO'
  const propostaPendente = Boolean(plano?.propostaPendente && plano.propostaId)
  const podeGerar = !plano || (ehRascunho && !propostaPendente)
  const dataHoje = dataHojeIso()

  async function carregarDia(data: string, cancelado?: () => boolean) {
    const [planoDia, resolvidas] = await Promise.all([
      buscarPlano({ data }),
      consultarCapacidadeResolvida({ dataDe: data, dataAte: data }),
    ])
    if (cancelado?.()) {
      return
    }
    setPlano(planoDia)
    setCapacidade(capacidadeDoDia(resolvidas, data))
    setDataSelecionada(data)

    if (planoDia?.propostaId) {
      const datas = await listarDatasDaProposta(dataHoje, planoDia.propostaId)
      if (!cancelado?.()) {
        setDatasProposta(datas)
      }
    } else if (!cancelado?.()) {
      setDatasProposta([])
    }

    if (planoDia?.status === 'APROVADO') {
      const execucoes = await listarExecucoes(data)
      if (!cancelado?.()) {
        setItensExecucao(execucoes.itens)
      }
    } else if (!cancelado?.()) {
      setItensExecucao([])
    }
  }

  useEffect(() => {
    let cancelado = false
    const data = dataHojeIso()

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        await carregarDia(data, () => cancelado)
      } catch (error) {
        if (cancelado) {
          return
        }
        setErro(mensagemErro(error, 'Não foi possível carregar o plano.'))
        setPlano(null)
        setItensExecucao([])
        setDatasProposta([])
      } finally {
        if (!cancelado) {
          setCarregando(false)
        }
      }
    }

    void carregar()
    return () => {
      cancelado = true
    }
  }, [])

  useEffect(() => {
    if (!ehRascunho || !plano) {
      setElegiveis([])
      setTarefaSelecionadaId('')
      return
    }

    let cancelado = false
    const idsNoPlano = plano.blocos.map((bloco) => bloco.tarefa.id)

    async function carregarElegiveis() {
      setCarregandoElegiveis(true)
      try {
        const page = await listarTarefas({
          status: statusElegiveisParaPlano(),
          page: 0,
          size: 100,
          sort: 'prazo',
          direction: 'asc',
        })
        if (cancelado) {
          return
        }
        const disponiveis = filtrarElegiveisForaDoPlano(page.content, idsNoPlano)
        setElegiveis(disponiveis)
        setTarefaSelecionadaId((atual) =>
          disponiveis.some((tarefa) => tarefa.id === atual) ? atual : '',
        )
      } catch {
        if (!cancelado) {
          setElegiveis([])
        }
      } finally {
        if (!cancelado) {
          setCarregandoElegiveis(false)
        }
      }
    }

    void carregarElegiveis()
    return () => {
      cancelado = true
    }
  }, [ehRascunho, plano, elegiveisTick])

  useEffect(() => {
    setConflitoDismissed(false)
  }, [plano?.id, plano?.versao, plano?.mensagem, plano?.temConflito])

  useEffect(() => {
    const temAndamento = itensExecucao.some((item) => item.execucao.status === 'EM_ANDAMENTO')
    if (!temAndamento) {
      return
    }
    const id = window.setInterval(() => setAgora(new Date()), 1000)
    return () => window.clearInterval(id)
  }, [itensExecucao])

  async function recarregarExecucoes(data: string) {
    const execucoes = await listarExecucoes(data)
    setItensExecucao(execucoes.itens)
  }

  async function handleGerar() {
    if (!podeGerar) {
      return
    }
    const anterior = plano ? snapshotDeBlocos(plano.blocos) : null
    setGerando(true)
    setAcaoErro(null)
    setErro(null)
    try {
      const inicio = dataHojeIso()
      const gerado = await gerarPlano(inicio)
      if (anterior) {
        setSnapshotAnterior(anterior)
        const tarefaIdsNovos = new Set(gerado.blocos.map((bloco) => bloco.tarefa.id))
        const removidos: string[] = []
        for (const tarefaId of anterior.tarefaIds) {
          if (!tarefaIdsNovos.has(tarefaId)) {
            removidos.push(anterior.titulosPorTarefaId.get(tarefaId) ?? 'Tarefa removida')
          }
        }
        setTitulosRemovidosDiff(removidos)
      } else {
        setSnapshotAnterior(null)
        setTitulosRemovidosDiff([])
      }
      setPlano(gerado)
      setDataSelecionada(gerado.data)
      setItensExecucao([])
      setCtaGerarAposCapacidade(false)
      if (gerado.propostaId) {
        setDatasProposta(await listarDatasDaProposta(inicio, gerado.propostaId))
        const resolvidas = await consultarCapacidadeResolvida({
          dataDe: gerado.data,
          dataAte: gerado.data,
        })
        setCapacidade(capacidadeDoDia(resolvidas, gerado.data))
      } else {
        setDatasProposta([])
      }
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível gerar o plano.'))
    } finally {
      setGerando(false)
    }
  }

  async function handleSelecionarDiaProposta(data: string) {
    if (data === dataSelecionada) {
      return
    }
    setCarregando(true)
    setAcaoErro(null)
    setErro(null)
    try {
      await carregarDia(data)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível carregar o dia da proposta.'))
    } finally {
      setCarregando(false)
    }
  }

  async function handleSalvarCapacidadeHoje(horas: number) {
    const data = dataHojeIso()
    setSalvandoCapacidadeHoje(true)
    setAcaoErro(null)
    try {
      const existentes = await listarCapacidadesOverride({ dataDe: data, dataAte: data })
      const ativo = existentes.find((item) => item.ativo && item.data === data)
      if (ativo) {
        await atualizarCapacidadeOverride(ativo.id, { horas })
      } else {
        await criarCapacidadeOverride({ data, horas })
      }
      const resolvidas = await consultarCapacidadeResolvida({ dataDe: data, dataAte: data })
      setCapacidade(capacidadeDoDia(resolvidas, data))
      const planoAtualizado = await buscarPlano({ data })
      setPlano(planoAtualizado)
      if (planoAtualizado?.propostaId) {
        setDatasProposta(await listarDatasDaProposta(dataHoje, planoAtualizado.propostaId))
      }
      setAjustarCapacidadeAberto(false)
      setCtaGerarAposCapacidade(!planoAtualizado?.propostaPendente)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível ajustar a capacidade de hoje.'))
    } finally {
      setSalvandoCapacidadeHoje(false)
    }
  }

  async function handleAceitarProposta() {
    if (!plano?.propostaId || !propostaPendente) {
      return
    }
    const confirmado = await confirm({
      title: 'Aceitar proposta?',
      message:
        'O plano aprovado vigente será substituído pela proposta. Blocos já concluídos permanecem.',
      confirmLabel: 'Aceitar',
    })
    if (!confirmado) {
      return
    }
    setResolvendoProposta(true)
    setAcaoErro(null)
    try {
      const atualizado = await aceitarProposta(plano.propostaId)
      setPlano(atualizado)
      setSnapshotAnterior(null)
      setTitulosRemovidosDiff([])
      if (atualizado.propostaId) {
        setDatasProposta(await listarDatasDaProposta(dataHoje, atualizado.propostaId))
      } else {
        setDatasProposta([])
      }
      await recarregarExecucoes(atualizado.data)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível aceitar a proposta.'))
    } finally {
      setResolvendoProposta(false)
    }
  }

  async function handleDescartarProposta() {
    if (!plano?.propostaId || !propostaPendente) {
      return
    }
    const confirmado = await confirm({
      title: 'Descartar proposta?',
      message: 'O plano aprovado vigente permanece como está. A proposta será descartada.',
      confirmLabel: 'Descartar',
    })
    if (!confirmado) {
      return
    }
    setResolvendoProposta(true)
    setAcaoErro(null)
    try {
      await descartarProposta(plano.propostaId)
      await carregarDia(plano.data)
      setSnapshotAnterior(null)
      setTitulosRemovidosDiff([])
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível descartar a proposta.'))
    } finally {
      setResolvendoProposta(false)
    }
  }

  async function handleAprovar() {
    if (!plano || !ehRascunho || propostaPendente) {
      return
    }
    const usadosMin = minutosUsados(plano.blocos)
    const disponiveisMin = minutosDisponiveis(capacidade?.horas, capacidade?.origem)
    const confirmado = await confirm({
      title: 'Aprovar plano?',
      message: `${plano.blocos.length} bloco${plano.blocos.length === 1 ? '' : 's'}, ${formatarCapacidadeMinutos(usadosMin)} de ${formatarCapacidadeMinutos(disponiveisMin)} de capacidade. Após aprovar, o plano entra em execução e deixa de ser editável como rascunho.`,
      confirmLabel: 'Aprovar',
    })
    if (!confirmado) {
      return
    }
    setAprovando(true)
    setAcaoErro(null)
    try {
      const atualizado = await aprovarPlano(plano.id)
      setPlano(atualizado)
      setSnapshotAnterior(null)
      setTitulosRemovidosDiff([])
      await recarregarExecucoes(atualizado.data)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível aprovar o plano.'))
    } finally {
      setAprovando(false)
    }
  }

  async function handleRemover(blocoId: string) {
    if (!plano || !ehRascunho) {
      return
    }
    setRemovendoBlocoId(blocoId)
    setAcaoErro(null)
    try {
      await removerBloco(plano.id, blocoId)
      const atualizado = await buscarPlano({ data: plano.data })
      setPlano(atualizado)
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível remover o bloco.'))
    } finally {
      setRemovendoBlocoId(null)
    }
  }

  async function handleRemoverMenorPrioridade() {
    if (!plano || !ehRascunho) {
      return
    }
    const alvo = escolherBlocoMenorPrioridade(plano.blocos)
    if (!alvo) {
      return
    }
    await handleRemover(alvo.id)
  }

  async function handleAdicionar(event: FormEvent) {
    event.preventDefault()
    if (!plano || !ehRascunho || !tarefaSelecionadaId) {
      return
    }
    setAdicionando(true)
    setAcaoErro(null)
    try {
      const atualizado = await adicionarBloco(plano.id, tarefaSelecionadaId)
      setPlano(atualizado)
      setTarefaSelecionadaId('')
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível adicionar a tarefa ao plano.'))
    } finally {
      setAdicionando(false)
    }
  }

  async function handleIniciar(blocoId: string) {
    setAcaoBlocoId(blocoId)
    setAcaoErro(null)
    try {
      await iniciarExecucao(blocoId)
      await recarregarExecucoes(dataHojeIso())
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível iniciar a execução.'))
    } finally {
      setAcaoBlocoId(null)
    }
  }

  async function handleConcluir(execucaoId: string, blocoId: string) {
    setAcaoBlocoId(blocoId)
    setAcaoErro(null)
    try {
      const resultado = await concluirExecucao(execucaoId)
      const xp = resultado.xp
      if (xp) {
        const nivelAnterior = progresso?.nivel ?? null
        for (const mensagem of mensagensFeedbackXp(xp, nivelAnterior)) {
          toast.success(mensagem)
        }
        if (xp.ganhoNesteRequest > 0) {
          setXpGanhoSessao((atual) => atual + xp.ganhoNesteRequest)
        }
        aplicarProgresso(xp.progresso)
      }
      await recarregarExecucoes(dataHojeIso())
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível concluir a execução.'))
    } finally {
      setAcaoBlocoId(null)
    }
  }

  async function handleAbortar(execucaoId: string, blocoId: string) {
    const confirmado = await confirm({
      title: 'Abortar execução?',
      message: 'Abortar interrompe o bloco em andamento. Esta ação não pode ser desfeita na UI.',
      confirmLabel: 'Abortar',
    })
    if (!confirmado) {
      return
    }
    setAcaoBlocoId(blocoId)
    setAcaoErro(null)
    try {
      await abortarExecucao(execucaoId)
      await recarregarExecucoes(dataHojeIso())
    } catch (error) {
      setAcaoErro(mensagemErro(error, 'Não foi possível abortar a execução.'))
    } finally {
      setAcaoBlocoId(null)
    }
  }

  const usados = plano ? minutosUsados(plano.blocos) : 0
  const disponiveis = minutosDisponiveis(capacidade?.horas, capacidade?.origem)
  const blocoAtivo = itensExecucao.find((item) => item.execucao.status === 'EM_ANDAMENTO')
  const minutosDoAtivo =
    blocoAtivo?.execucao.iniciadoEm != null
      ? minutosRestantes(
          blocoAtivo.execucao.iniciadoEm,
          blocoAtivo.bloco.duracaoMinutos,
          agora,
        )
      : 0

  const diaCompleto =
    ehAprovado &&
    itensExecucao.length > 0 &&
    itensExecucao.every((item) => item.execucao.status === 'CONCLUIDO')

  const mostrarBannerConflito =
    Boolean(plano?.mensagem) && !(plano?.temConflito && conflitoDismissed)

  return (
    <main className="home-page">
      {confirmDialog}
      <QuickAddTarefaDialog
        open={quickAddAberto}
        onClose={() => setQuickAddAberto(false)}
        onCriada={() => setElegiveisTick((n) => n + 1)}
      />
      <div className="home-topo">
        <p className="home-saudacao">{formatarDataPorExtenso()}</p>
        <button
          type="button"
          className="btn btn--secondary btn--sm"
          onClick={() => setQuickAddAberto(true)}
        >
          Nova tarefa
        </button>
      </div>
      <OnboardingChecklist
        temPlano={plano != null}
        onAbrirQuickAdd={() => setQuickAddAberto(true)}
        onGerarPlano={podeGerar ? () => void handleGerar() : undefined}
      />
      <section className="plano-dia card" aria-labelledby="plano-dia-titulo">
        <div className="plano-dia-cabecalho">
          <h2 id="plano-dia-titulo">
            {dataSelecionada === dataHoje ? 'Plano de hoje' : `Plano de ${dataSelecionada}`}
          </h2>
          {plano ? (
            <span className={`badge status-plano-${plano.status.toLowerCase()}`}>
              {labelStatusPlano(plano.status)}
            </span>
          ) : null}
        </div>

        {datasProposta.length > 1 ? (
          <nav className="proposta-dias" aria-label="Dias da proposta">
            <p className="proposta-dias__hint">Proposta multi-dia — escolha o dia para revisar</p>
            <div className="proposta-dias__lista">
              {datasProposta.map((data) => (
                <button
                  key={data}
                  type="button"
                  className={
                    data === dataSelecionada
                      ? 'btn btn--secondary btn--sm proposta-dias__dia proposta-dias__dia--ativo'
                      : 'btn btn--ghost btn--sm proposta-dias__dia'
                  }
                  aria-current={data === dataSelecionada ? 'date' : undefined}
                  onClick={() => void handleSelecionarDiaProposta(data)}
                >
                  {data === dataHoje ? 'Hoje' : data.slice(5)}
                </button>
              ))}
            </div>
          </nav>
        ) : null}

        {diaCompleto ? (
          <p className="plano-dia-completo" role="status">
            {xpGanhoSessao > 0
              ? `Plano de hoje concluído · +${xpGanhoSessao} XP`
              : 'Plano de hoje concluído'}
          </p>
        ) : null}

        {carregando ? <Skeleton variant="plano-blocos" /> : null}

        {erro ? <InlineAlert>{erro}</InlineAlert> : null}

        {acaoErro ? <InlineAlert>{acaoErro}</InlineAlert> : null}

        {!carregando && !erro && !plano ? (
          <>
            <AjustarCapacidadeHoje
              aberto={ajustarCapacidadeAberto}
              horasIniciais={capacidade?.horas ?? 0}
              salvando={salvandoCapacidadeHoje}
              mostrarCtaGerar={ctaGerarAposCapacidade && podeGerar}
              gerando={gerando}
              onAbrir={() => setAjustarCapacidadeAberto(true)}
              onFechar={() => setAjustarCapacidadeAberto(false)}
              onSalvar={(horas) => void handleSalvarCapacidadeHoje(horas)}
              onGerarVersao={() => void handleGerar()}
            />
            <EmptyStatePlano gerando={gerando} onGerar={() => void handleGerar()} />
          </>
        ) : null}

        {!carregando && !erro && plano ? (
          <>
            <CapacidadeBar
              usadosMinutos={usados}
              disponiveisMinutos={disponiveis}
              sobrecarga={plano.temConflito}
              versao={plano.versao}
            />

            <AjustarCapacidadeHoje
              aberto={ajustarCapacidadeAberto}
              horasIniciais={capacidade?.horas ?? 0}
              salvando={salvandoCapacidadeHoje}
              mostrarCtaGerar={ctaGerarAposCapacidade && podeGerar}
              gerando={gerando}
              onAbrir={() => setAjustarCapacidadeAberto(true)}
              onFechar={() => setAjustarCapacidadeAberto(false)}
              onSalvar={(horas) => void handleSalvarCapacidadeHoje(horas)}
              onGerarVersao={() => void handleGerar()}
            />

            {mostrarBannerConflito && plano.mensagem ? (
              <ConflitoBanner
                mensagem={plano.mensagem}
                temConflito={plano.temConflito}
                onRemoverMenorPrioridade={
                  plano.temConflito && ehRascunho
                    ? () => void handleRemoverMenorPrioridade()
                    : undefined
                }
                onAumentarCapacidade={
                  plano.temConflito
                    ? () => setAjustarCapacidadeAberto(true)
                    : undefined
                }
                onManterAssim={
                  plano.temConflito ? () => setConflitoDismissed(true) : undefined
                }
                removendo={removendoBlocoId != null}
              />
            ) : null}

            {titulosRemovidosDiff.length > 0 ? (
              <p className="plano-diff-removidos" role="status">
                Removidos na nova versão: {titulosRemovidosDiff.join(', ')}
              </p>
            ) : null}

            {podeGerar || ehRascunho ? (
              <div className="plano-dia-acoes">
                {podeGerar ? (
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() => void handleGerar()}
                    disabled={gerando || aprovando || resolvendoProposta}
                    aria-busy={gerando}
                  >
                    {gerando ? 'Gerando…' : plano ? 'Gerar nova versão' : 'Gerar plano'}
                  </button>
                ) : null}
                {propostaPendente ? (
                  <>
                    <button
                      type="button"
                      className="btn btn--primary"
                      onClick={() => void handleAceitarProposta()}
                      disabled={resolvendoProposta || gerando}
                      aria-busy={resolvendoProposta}
                    >
                      {resolvendoProposta ? 'Aceitando…' : 'Aceitar proposta'}
                    </button>
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => void handleDescartarProposta()}
                      disabled={resolvendoProposta || gerando}
                    >
                      Descartar
                    </button>
                  </>
                ) : ehRascunho ? (
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => void handleAprovar()}
                    disabled={aprovando || gerando}
                    aria-busy={aprovando}
                  >
                    {aprovando ? 'Aprovando…' : 'Aprovar plano'}
                  </button>
                ) : null}
              </div>
            ) : null}

            {ehAprovado && blocoAtivo && blocoAtivo.execucao.id ? (
              <BlocoAtivo
                titulo={blocoAtivo.tarefaResumo.titulo}
                duracaoMinutos={blocoAtivo.bloco.duracaoMinutos}
                minutosRestantes={minutosDoAtivo}
                ocupado={acaoBlocoId === blocoAtivo.bloco.id}
                onConcluir={() =>
                  void handleConcluir(blocoAtivo.execucao.id!, blocoAtivo.bloco.id)
                }
                onAbortar={() =>
                  void handleAbortar(blocoAtivo.execucao.id!, blocoAtivo.bloco.id)
                }
              />
            ) : null}

            {ehAprovado ? (
              itensExecucao.length === 0 ? (
                <p className="plano-dia-vazio" role="status">
                  Nenhum bloco neste plano.
                </p>
              ) : (
                <ol className="plano-blocos">
                  {itensExecucao.map((item) => {
                    const status = item.execucao.status
                    const ocupado = acaoBlocoId === item.bloco.id

                    const acoes =
                      status === 'NAO_INICIADO' || status === 'ABORTADO' ? (
                        <button
                          type="button"
                          className="btn btn--primary btn--sm"
                          onClick={() => void handleIniciar(item.bloco.id)}
                          disabled={ocupado}
                          aria-busy={ocupado}
                          aria-label={`Iniciar ${item.tarefaResumo.titulo}`}
                        >
                          {ocupado ? 'Iniciando…' : 'Iniciar'}
                        </button>
                      ) : null

                    return (
                      <BlocoCard
                        key={item.bloco.id}
                        ordem={item.bloco.ordem}
                        titulo={item.tarefaResumo.titulo}
                        tarefaId={item.tarefaResumo.id}
                        duracaoMinutos={item.bloco.duracaoMinutos}
                        prioridade={item.tarefaResumo.prioridade}
                        statusExecucao={status}
                        acoes={acoes}
                      />
                    )
                  })}
                </ol>
              )
            ) : plano.blocos.length === 0 ? (
              <p className="plano-dia-vazio" role="status">
                Nenhum bloco neste plano.
              </p>
            ) : (
              <ol className="plano-blocos">
                {plano.blocos.map((bloco) => {
                  const ehNovo =
                    snapshotAnterior != null &&
                    !snapshotAnterior.tarefaIds.has(bloco.tarefa.id)
                  return (
                    <BlocoCard
                      key={bloco.id}
                      ordem={bloco.ordem}
                      titulo={bloco.tarefa.titulo}
                      tarefaId={bloco.tarefa.id}
                      duracaoMinutos={bloco.duracaoMinutos}
                      prioridade={bloco.tarefa.prioridade}
                      prazo={bloco.tarefa.prazo}
                      mostrarJustificativa={ehRascunho}
                      diff={ehNovo ? 'novo' : null}
                      onRemover={
                        ehRascunho ? () => void handleRemover(bloco.id) : undefined
                      }
                      removendo={removendoBlocoId === bloco.id}
                    />
                  )
                })}
              </ol>
            )}

            {ehRascunho ? (
              <AdicionarTarefaForm
                elegiveis={elegiveis}
                tarefaSelecionadaId={tarefaSelecionadaId}
                carregandoElegiveis={carregandoElegiveis}
                adicionando={adicionando}
                onSelecionar={setTarefaSelecionadaId}
                onSubmit={(event) => void handleAdicionar(event)}
              />
            ) : null}
          </>
        ) : null}
      </section>
    </main>
  )
}
