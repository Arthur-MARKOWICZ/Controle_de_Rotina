import { useEffect, useRef, useState, type FormEvent } from 'react'

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
import type { DiaSemana } from '../capacidade/api/capacidadeSemanalApi'
import {
  abortarExecucao,
  concluirExecucao,
  iniciarExecucao,
  listarExecucoes,
  type ExecucaoResponse,
  type ItemExecucaoResponse,
} from '../execucao/api/execucaoApi'
import { useToast } from '../feedback/ToastProvider'
import { useConfirmDialog } from '../feedback/useConfirmDialog'
import { useQuickAdd } from '../layout/QuickAddContext'
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
  statusElegiveisParaPlano,
} from '../planner/data'
import { listarProjetos } from '../projeto/api/projetoApi'
import { listarReservas, type ReservaProjetoResponse } from '../projeto/api/reservaProjetoApi'
import { listarTarefas, type TarefaResponse } from '../tarefa/api/tarefaApi'
import { mensagensFeedbackXp } from '../xp/feedbackXp'
import { useXpProgress } from '../xp/XpProgressContext'
import { diaSemanaDeDataIso, formatarExplicacaoReservas } from './explicacaoReservas'
import { escolherBlocoMenorPrioridade } from './justificativa'
import {
  aplicarAbortar,
  aplicarConcluir,
  aplicarIniciar,
  aplicarRemoverBloco,
  encontrarBloco,
  mesclarExecucao,
  restaurarBloco,
  restaurarItem,
} from './otimista'

type SnapshotBlocos = {
  /** IDs de tarefa (bloco.id muda a cada versão). */
  tarefaIds: Set<string>
  titulosPorTarefaId: Map<string, string>
}

export type TarefaEmAdicao = { id: string; titulo: string }

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

function titulosRemovidos(anterior: SnapshotBlocos, novo: PlanoResponse): string[] {
  const tarefaIdsNovos = new Set(novo.blocos.map((bloco) => bloco.tarefa.id))
  const removidos: string[] = []
  for (const tarefaId of anterior.tarefaIds) {
    if (!tarefaIdsNovos.has(tarefaId)) {
      removidos.push(anterior.titulosPorTarefaId.get(tarefaId) ?? 'Tarefa removida')
    }
  }
  return removidos
}

/** Estado e ações da Home (plano do dia). Sem cache: tudo vive enquanto a tela está montada. */
export function usePlanoDoDia() {
  const { confirm, dialog: confirmDialog } = useConfirmDialog()
  const toast = useToast()
  const { progresso, aplicarProgresso } = useXpProgress()
  const { abrir: abrirQuickAdd, assinarCriada } = useQuickAdd()
  const dataHoje = dataHojeIso()

  const [plano, setPlano] = useState<PlanoResponse | null>(null)
  const [capacidade, setCapacidade] = useState<CapacidadeResolvidaResponse | undefined>()
  const [itensExecucao, setItensExecucao] = useState<ItemExecucaoResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [trocandoDia, setTrocandoDia] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aprovando, setAprovando] = useState(false)
  const [resolvendoProposta, setResolvendoProposta] = useState(false)
  const [gerando, setGerando] = useState(false)
  const [pendentes, setPendentes] = useState<ReadonlySet<string>>(() => new Set())
  const [elegiveis, setElegiveis] = useState<TarefaResponse[]>([])
  const [tarefaSelecionadaId, setTarefaSelecionadaId] = useState('')
  const [tarefaEmAdicao, setTarefaEmAdicao] = useState<TarefaEmAdicao | null>(null)
  const [carregandoElegiveis, setCarregandoElegiveis] = useState(false)
  const [elegiveisTick, setElegiveisTick] = useState(0)
  const [conflitoDismissed, setConflitoDismissed] = useState(false)
  const [snapshotAnterior, setSnapshotAnterior] = useState<SnapshotBlocos | null>(null)
  const [titulosRemovidosDiff, setTitulosRemovidosDiff] = useState<string[]>([])
  const [xpGanhoSessao, setXpGanhoSessao] = useState(0)
  const [ajustarCapacidadeAberto, setAjustarCapacidadeAberto] = useState(false)
  const [salvandoCapacidadeHoje, setSalvandoCapacidadeHoje] = useState(false)
  const [ctaGerarAposCapacidade, setCtaGerarAposCapacidade] = useState(false)
  const [dataSelecionada, setDataSelecionada] = useState(dataHoje)
  /** `null` enquanto as datas da proposta carregam. */
  const [datasProposta, setDatasProposta] = useState<string[] | null>([])
  /** `undefined` enquanto a explicação de reservas não chegou. */
  const [explicacaoReservas, setExplicacaoReservas] = useState<string | null | undefined>()

  /** Id da última leitura/mutação do plano; respostas de ids antigos são descartadas. */
  const requisicaoPlano = useRef(0)
  const datasCarregadasDe = useRef<string | null>(null)
  const remocoesEmVoo = useRef(0)

  const ehRascunho = plano?.status === 'RASCUNHO'
  const propostaPendente = Boolean(plano?.propostaPendente && plano.propostaId)
  const podeGerar = !plano || (ehRascunho && !propostaPendente)

  function novaRequisicao(): () => boolean {
    requisicaoPlano.current += 1
    const id = requisicaoPlano.current
    return () => id === requisicaoPlano.current
  }

  function marcarPendente(id: string, pendente: boolean) {
    setPendentes((atual) => {
      const proximo = new Set(atual)
      if (pendente) {
        proximo.add(id)
      } else {
        proximo.delete(id)
      }
      return proximo
    })
  }

  function erroComRetry(mensagem: string, tentarDeNovo: () => void) {
    toast.error(mensagem, { action: { label: 'Tentar de novo', onClick: tentarDeNovo } })
  }

  async function carregarExplicacaoReservas(data: string, atual: () => boolean) {
    try {
      const dia: DiaSemana = diaSemanaDeDataIso(data)
      const [reservas, projetosPage] = await Promise.all([
        listarReservas({ diaSemana: dia }),
        listarProjetos({ status: 'ATIVO', page: 0, size: 100 }),
      ])
      if (!atual()) {
        return
      }
      const nomes: Record<string, string> = {}
      for (const projeto of projetosPage.content) {
        nomes[projeto.id] = projeto.nome
      }
      const doDia: ReservaProjetoResponse[] = reservas.filter((r) => r.diaSemana === dia)
      setExplicacaoReservas(formatarExplicacaoReservas(doDia, nomes))
    } catch {
      if (atual()) {
        setExplicacaoReservas(null)
      }
    }
  }

  /** Datas da proposta não bloqueiam a renderização; só recarrega quando a proposta muda. */
  async function carregarDatasProposta(planoDia: PlanoResponse | null) {
    const propostaId = planoDia?.propostaId ?? null
    if (propostaId === datasCarregadasDe.current) {
      return
    }
    datasCarregadasDe.current = propostaId
    if (!propostaId) {
      setDatasProposta([])
      return
    }
    setDatasProposta(null)
    try {
      const datas = await listarDatasDaProposta(dataHoje, propostaId)
      if (datasCarregadasDe.current === propostaId) {
        setDatasProposta(datas)
      }
    } catch {
      if (datasCarregadasDe.current === propostaId) {
        datasCarregadasDe.current = null
        setDatasProposta([])
      }
    }
  }

  /**
   * Rodada 1 (paralela): plano + capacidade; reservas em segundo plano.
   * Rodada 2: execuções (se aprovado); datas da proposta sem bloquear.
   * Retorna `false` quando a resposta foi descartada por uma requisição mais nova.
   */
  async function carregarDia(data: string): Promise<boolean> {
    const atual = novaRequisicao()
    void carregarExplicacaoReservas(data, atual)
    try {
      const [planoDia, resolvidas] = await Promise.all([
        buscarPlano({ data }),
        consultarCapacidadeResolvida({ dataDe: data, dataAte: data }),
      ])
      if (!atual()) {
        return false
      }
      void carregarDatasProposta(planoDia)
      const execucoes =
        planoDia?.status === 'APROVADO' ? (await listarExecucoes(data)).itens : []
      if (!atual()) {
        return false
      }
      setPlano(planoDia)
      setCapacidade(capacidadeDoDia(resolvidas, data))
      setItensExecucao(execucoes)
      setDataSelecionada(data)
      return true
    } catch (error) {
      if (!atual()) {
        return false
      }
      throw error
    }
  }

  function encerrarCargas() {
    setCarregando(false)
    setTrocandoDia(false)
    setErro(null)
  }

  useEffect(() => assinarCriada(() => setElegiveisTick((n) => n + 1)), [assinarCriada])

  useEffect(() => {
    async function carregarInicial() {
      try {
        if (await carregarDia(dataHojeIso())) {
          setCarregando(false)
        }
      } catch (error) {
        setErro(mensagemErro(error, 'Não foi possível carregar o plano.'))
        setPlano(null)
        setItensExecucao([])
        setCarregando(false)
      }
    }

    void carregarInicial()
    return () => {
      requisicaoPlano.current += 1
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

  async function handleGerar() {
    if (!podeGerar) {
      return
    }
    const anterior = plano ? snapshotDeBlocos(plano.blocos) : null
    const atual = novaRequisicao()
    setGerando(true)
    try {
      const gerado = await gerarPlano(dataHoje)
      const resolvidas = gerado.propostaId
        ? await consultarCapacidadeResolvida({ dataDe: gerado.data, dataAte: gerado.data })
        : null
      if (!atual()) {
        return
      }
      setSnapshotAnterior(anterior)
      setTitulosRemovidosDiff(anterior ? titulosRemovidos(anterior, gerado) : [])
      setPlano(gerado)
      setDataSelecionada(gerado.data)
      setItensExecucao([])
      setCtaGerarAposCapacidade(false)
      if (resolvidas) {
        setCapacidade(capacidadeDoDia(resolvidas, gerado.data))
      }
      encerrarCargas()
      datasCarregadasDe.current = null
      void carregarDatasProposta(gerado)
    } catch (error) {
      erroComRetry(mensagemErro(error, 'Não foi possível gerar o plano.'), () => void handleGerar())
    } finally {
      setGerando(false)
    }
  }

  async function handleSelecionarDiaProposta(data: string) {
    if (data === dataSelecionada) {
      return
    }
    setTrocandoDia(true)
    try {
      if (await carregarDia(data)) {
        setTrocandoDia(false)
      }
    } catch (error) {
      setTrocandoDia(false)
      erroComRetry(
        mensagemErro(error, 'Não foi possível carregar o dia da proposta.'),
        () => void handleSelecionarDiaProposta(data),
      )
    }
  }

  async function handleSalvarCapacidadeHoje(horas: number) {
    const data = dataHojeIso()
    setSalvandoCapacidadeHoje(true)
    try {
      const existentes = await listarCapacidadesOverride({ dataDe: data, dataAte: data })
      const ativo = existentes.find((item) => item.ativo && item.data === data)
      if (ativo) {
        await atualizarCapacidadeOverride(ativo.id, { horas })
      } else {
        await criarCapacidadeOverride({ data, horas })
      }
      const atual = novaRequisicao()
      const [resolvidas, planoAtualizado] = await Promise.all([
        consultarCapacidadeResolvida({ dataDe: data, dataAte: data }),
        buscarPlano({ data }),
      ])
      if (atual()) {
        setCapacidade(capacidadeDoDia(resolvidas, data))
        setPlano(planoAtualizado)
        void carregarDatasProposta(planoAtualizado)
      }
      setAjustarCapacidadeAberto(false)
      setCtaGerarAposCapacidade(!planoAtualizado?.propostaPendente)
    } catch (error) {
      erroComRetry(
        mensagemErro(error, 'Não foi possível ajustar a capacidade de hoje.'),
        () => void handleSalvarCapacidadeHoje(horas),
      )
    } finally {
      setSalvandoCapacidadeHoje(false)
    }
  }

  /** Aplica um plano aprovado junto com suas execuções (evita piscar a lista vazia). */
  async function aplicarPlanoAprovado(aprovado: PlanoResponse, atual: () => boolean) {
    const execucoes = await listarExecucoes(aprovado.data).catch(() => null)
    if (!atual()) {
      return
    }
    setPlano(aprovado)
    setItensExecucao(execucoes?.itens ?? [])
    setSnapshotAnterior(null)
    setTitulosRemovidosDiff([])
    void carregarDatasProposta(aprovado)
    if (!execucoes) {
      erroComRetry(
        'Não foi possível carregar os blocos do plano aprovado.',
        () => void carregarDia(aprovado.data),
      )
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
    const atual = novaRequisicao()
    setResolvendoProposta(true)
    try {
      await aplicarPlanoAprovado(await aceitarProposta(plano.propostaId), atual)
    } catch (error) {
      erroComRetry(
        mensagemErro(error, 'Não foi possível aceitar a proposta.'),
        () => void handleAceitarProposta(),
      )
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
    try {
      await descartarProposta(plano.propostaId)
      await carregarDia(plano.data)
      setSnapshotAnterior(null)
      setTitulosRemovidosDiff([])
    } catch (error) {
      erroComRetry(
        mensagemErro(error, 'Não foi possível descartar a proposta.'),
        () => void handleDescartarProposta(),
      )
    } finally {
      setResolvendoProposta(false)
    }
  }

  async function handleAprovar() {
    if (!plano || !ehRascunho || propostaPendente) {
      return
    }
    const atual = novaRequisicao()
    setAprovando(true)
    try {
      await aplicarPlanoAprovado(await aprovarPlano(plano.id), atual)
    } catch (error) {
      erroComRetry(mensagemErro(error, 'Não foi possível aprovar o plano.'), () => void handleAprovar())
    } finally {
      setAprovando(false)
    }
  }

  /** Leitura silenciosa (sem skeleton) para reconciliar depois de um `DELETE` sem corpo. */
  async function reconciliarPlano(data: string) {
    const atual = novaRequisicao()
    try {
      const atualizado = await buscarPlano({ data })
      if (atual()) {
        setPlano(atualizado)
      }
    } catch {
      // A lista otimista continua válida; a próxima carga corrige divergências.
    }
  }

  async function adicionarTarefaAoPlano(planoId: string, tarefa: TarefaEmAdicao): Promise<boolean> {
    const atual = novaRequisicao()
    setTarefaEmAdicao(tarefa)
    try {
      const atualizado = await adicionarBloco(planoId, tarefa.id)
      if (atual()) {
        setPlano(atualizado)
      }
      return true
    } catch (error) {
      erroComRetry(
        mensagemErro(error, 'Não foi possível adicionar a tarefa ao plano.'),
        () => void adicionarTarefaAoPlano(planoId, tarefa),
      )
      return false
    } finally {
      setTarefaEmAdicao(null)
    }
  }

  async function handleRemover(blocoId: string) {
    if (!plano || !ehRascunho) {
      return
    }
    const { id: planoId, data } = plano
    const bloco = encontrarBloco(plano, blocoId)
    if (!bloco) {
      return
    }
    remocoesEmVoo.current += 1
    marcarPendente(blocoId, true)
    setPlano((atual) =>
      atual && atual.id === planoId ? aplicarRemoverBloco(atual, blocoId) : atual,
    )
    try {
      await removerBloco(planoId, blocoId)
      toast.success(`Removido: ${bloco.tarefa.titulo}`, {
        action: {
          label: 'Desfazer',
          onClick: () =>
            void adicionarTarefaAoPlano(planoId, {
              id: bloco.tarefa.id,
              titulo: bloco.tarefa.titulo,
            }),
        },
      })
      remocoesEmVoo.current -= 1
      if (remocoesEmVoo.current === 0) {
        await reconciliarPlano(data)
      }
    } catch (error) {
      remocoesEmVoo.current -= 1
      setPlano((atual) => (atual && atual.id === planoId ? restaurarBloco(atual, bloco) : atual))
      erroComRetry(
        mensagemErro(error, 'Não foi possível remover o bloco.'),
        () => void handleRemover(blocoId),
      )
    } finally {
      marcarPendente(blocoId, false)
    }
  }

  async function handleRemoverMenorPrioridade() {
    if (!plano || !ehRascunho) {
      return
    }
    const alvo = escolherBlocoMenorPrioridade(plano.blocos)
    if (alvo) {
      await handleRemover(alvo.id)
    }
  }

  async function handleAdicionar(event: FormEvent) {
    event.preventDefault()
    const tarefa = elegiveis.find((item) => item.id === tarefaSelecionadaId)
    if (!plano || !ehRascunho || !tarefa) {
      return
    }
    if (await adicionarTarefaAoPlano(plano.id, { id: tarefa.id, titulo: tarefa.titulo })) {
      setTarefaSelecionadaId('')
    }
  }

  /**
   * Execução otimista: aplica `otimista` já, chama a API, mescla a resposta;
   * em erro restaura o item e oferece “Tentar de novo”.
   */
  async function executarOtimista(
    item: ItemExecucaoResponse,
    otimista: (itens: ItemExecucaoResponse[]) => ItemExecucaoResponse[],
    chamada: () => Promise<ExecucaoResponse>,
    mensagemFalha: string,
    tentarDeNovo: () => void,
  ): Promise<ExecucaoResponse | null> {
    const blocoId = item.bloco.id
    marcarPendente(blocoId, true)
    setItensExecucao(otimista)
    try {
      const resposta = await chamada()
      setItensExecucao((atual) => mesclarExecucao(atual, blocoId, resposta))
      return resposta
    } catch (error) {
      setItensExecucao((atual) => restaurarItem(atual, item))
      erroComRetry(mensagemErro(error, mensagemFalha), tentarDeNovo)
      return null
    } finally {
      marcarPendente(blocoId, false)
    }
  }

  async function handleIniciar(item: ItemExecucaoResponse) {
    const blocoId = item.bloco.id
    await executarOtimista(
      item,
      (itens) => aplicarIniciar(itens, blocoId, new Date()),
      () => iniciarExecucao(blocoId),
      'Não foi possível iniciar a execução.',
      () => void handleIniciar(item),
    )
  }

  async function handleConcluir(item: ItemExecucaoResponse) {
    const execucaoId = item.execucao.id
    if (!execucaoId) {
      return
    }
    const resultado = await executarOtimista(
      item,
      (itens) => aplicarConcluir(itens, item.bloco.id, new Date()),
      () => concluirExecucao(execucaoId),
      'Não foi possível concluir a execução.',
      () => void handleConcluir(item),
    )
    const xp = resultado?.xp
    if (!xp) {
      return
    }
    const nivelAnterior = progresso?.nivel ?? null
    for (const mensagem of mensagensFeedbackXp(xp, nivelAnterior)) {
      toast.success(mensagem)
    }
    if (xp.ganhoNesteRequest > 0) {
      setXpGanhoSessao((atual) => atual + xp.ganhoNesteRequest)
    }
    aplicarProgresso(xp.progresso)
  }

  async function handleAbortar(item: ItemExecucaoResponse) {
    const execucaoId = item.execucao.id
    if (!execucaoId) {
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
    await executarOtimista(
      item,
      (itens) => aplicarAbortar(itens, item.bloco.id, new Date()),
      () => abortarExecucao(execucaoId),
      'Não foi possível abortar a execução.',
      () => void handleAbortar(item),
    )
  }

  return {
    confirmDialog,
    abrirQuickAdd,
    dataHoje,
    plano,
    capacidade,
    itensExecucao,
    carregando,
    trocandoDia,
    erro,
    aprovando,
    resolvendoProposta,
    gerando,
    pendentes,
    elegiveis,
    tarefaSelecionadaId,
    setTarefaSelecionadaId,
    tarefaEmAdicao,
    carregandoElegiveis,
    conflitoDismissed,
    dispensarConflito: () => setConflitoDismissed(true),
    snapshotAnterior,
    titulosRemovidosDiff,
    xpGanhoSessao,
    ajustarCapacidadeAberto,
    setAjustarCapacidadeAberto,
    salvandoCapacidadeHoje,
    ctaGerarAposCapacidade,
    dataSelecionada,
    datasProposta,
    explicacaoReservas,
    ehRascunho,
    propostaPendente,
    podeGerar,
    handleGerar,
    handleSelecionarDiaProposta,
    handleSalvarCapacidadeHoje,
    handleAceitarProposta,
    handleDescartarProposta,
    handleAprovar,
    handleRemover,
    handleRemoverMenorPrioridade,
    handleAdicionar,
    handleIniciar,
    handleConcluir,
    handleAbortar,
  }
}
