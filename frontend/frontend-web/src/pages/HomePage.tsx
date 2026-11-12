import { CheckIcon, PlayIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'

import { tituloDoItemExecucao } from '../execucao/api/execucaoApi'
import { useSessaoAtiva } from '../execucao/SessaoAtivaContext'
import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { useToast } from '../feedback/ToastProvider'
import { HabitoDoDiaLista } from '../habito/HabitoDoDiaLista'
import {
  concluirHabitoDireto,
  listarHabitosDoDia,
  type HabitoDoDia,
} from '../habito/api/habitoApi'
import { AdicionarTarefaForm } from '../home/AdicionarTarefaForm'
import { AjustarCapacidadeHoje } from '../home/AjustarCapacidadeHoje'
import { BlocoCard } from '../home/BlocoCard'
import { concluirTarefaDireto } from '../tarefa/api/tarefaApi'
import { CapacidadeBar } from '../home/CapacidadeBar'
import { ConflitoBanner } from '../home/ConflitoBanner'
import { DiaConcluido } from '../home/DiaConcluido'
import { EmptyStatePlano } from '../home/EmptyStatePlano'
import { formatarDataIsoPorExtenso, formatarDiaCurto } from '../home/formatacao'
import { labelStatusPlano } from '../home/labels'
import { OnboardingChecklist } from '../home/OnboardingChecklist'
import { usePlanoDoDia } from '../home/usePlanoDoDia'
import { PageHeader } from '../layout/PageHeader'
import { rotuloEspera } from '../pendencia/api/espera'
import {
  chaveOrigemBloco,
  ehBlocoHabito,
  origemDoBloco,
  tituloDoBloco,
} from '../planner/blocoOrigem'
import { formatarCapacidadeMinutos, minutosDisponiveis, minutosUsados } from '../planner/data'

type VisaoHome = 'plano' | 'habitos'

function rotuloAprovar(totalBlocos: number, usadosMinutos: number): string {
  const blocos = `${totalBlocos} bloco${totalBlocos === 1 ? '' : 's'}`
  return `Aprovar ${blocos} · ${formatarCapacidadeMinutos(usadosMinutos)}`
}

function descricaoCabecalho(visao: VisaoHome, dataSelecionada: string, dataHoje: string): string {
  if (visao === 'habitos') {
    return dataSelecionada === dataHoje ? 'Hábitos de hoje' : 'Hábitos do dia'
  }
  return dataSelecionada === dataHoje ? 'Sugestão para hoje' : 'Dia da proposta'
}

export function HomePage() {
  const toast = useToast()
  const { refrescarDoDia, registrarInicio } = useSessaoAtiva()
  const home = usePlanoDoDia()
  const {
    plano,
    capacidade,
    itensExecucao,
    carregando,
    erro,
    ehRascunho,
    propostaPendente,
    podeGerar,
    dataHoje,
    dataSelecionada,
    datasProposta,
    pendentes,
  } = home

  useEffect(() => {
    function onSessaoAtualizada(event: Event) {
      const detail = (event as CustomEvent<{ xp?: { ganhoNesteRequest?: number } | null }>).detail
      const ganho = detail?.xp?.ganhoNesteRequest ?? 0
      if (ganho > 0) {
        home.somarXpGanhoSessao(ganho)
      }
      void home.recarregarExecucoes()
      void refrescarDoDia()
    }
    window.addEventListener('sessao-ativa:atualizada', onSessaoAtualizada)
    return () => window.removeEventListener('sessao-ativa:atualizada', onSessaoAtualizada)
  }, [home.recarregarExecucoes, home.somarXpGanhoSessao, refrescarDoDia])

  useEffect(() => {
    const ativo = itensExecucao.find((item) => item.execucao.status === 'EM_ANDAMENTO')
    if (!ativo) {
      return
    }
    registrarInicio(tituloDoItemExecucao(ativo), ativo.bloco.duracaoMinutos, {
      ...ativo.execucao,
      blocoPlanoId: ativo.bloco.id,
    })
  }, [itensExecucao, registrarInicio])

  const [visao, setVisao] = useState<VisaoHome>('plano')
  const [habitosDoDia, setHabitosDoDia] = useState<HabitoDoDia[]>([])
  const [erroHabitos, setErroHabitos] = useState<string | null>(null)
  const [carregandoHabitos, setCarregandoHabitos] = useState(true)

  useEffect(() => {
    let ativo = true
    setErroHabitos(null)
    setCarregandoHabitos(true)
    listarHabitosDoDia(dataSelecionada)
      .then((lista) => {
        if (ativo) {
          setHabitosDoDia(lista)
        }
      })
      .catch((error) => {
        if (!ativo) {
          return
        }
        setHabitosDoDia([])
        setErroHabitos(
          error instanceof Error ? error.message : 'Não foi possível carregar os hábitos do dia.',
        )
      })
      .finally(() => {
        if (ativo) {
          setCarregandoHabitos(false)
        }
      })
    return () => {
      ativo = false
    }
  }, [dataSelecionada])

  const ehAprovado = plano?.status === 'APROVADO'
  const usados = plano ? minutosUsados(plano.blocos) : 0
  const disponiveis = minutosDisponiveis(capacidade?.horas, capacidade?.origem)
  const concluidos = itensExecucao.filter((item) => item.execucao.status === 'CONCLUIDO').length
  const diaCompleto = ehAprovado && itensExecucao.length > 0 && concluidos === itensExecucao.length
  const mostrarBannerConflito =
    Boolean(plano?.mensagem) && !(plano?.temConflito && home.conflitoDismissed)
  const conteudoOcupado = home.gerando || home.trocandoDia
  const carregandoDatas = datasProposta === null && Boolean(plano?.propostaId)
  const rotuloHabitos =
    habitosDoDia.length > 0 ? `Hábitos (${habitosDoDia.length})` : 'Hábitos'

  const ajustarCapacidade = (
    <AjustarCapacidadeHoje
      aberto={home.ajustarCapacidadeAberto}
      horasIniciais={capacidade?.horas ?? 0}
      salvando={home.salvandoCapacidadeHoje}
      mostrarCtaGerar={home.ctaGerarAposCapacidade && podeGerar}
      gerando={home.gerando}
      mostrarGatilho={plano != null}
      onAbrir={() => home.setAjustarCapacidadeAberto(true)}
      onFechar={() => home.setAjustarCapacidadeAberto(false)}
      onSalvar={(horas) => void home.handleSalvarCapacidadeHoje(horas)}
      onGerarVersao={() => void home.handleGerar()}
    />
  )

  return (
    <main className="home-page">
      {home.confirmDialog}
      <PageHeader
        title={formatarDataIsoPorExtenso(dataSelecionada)}
        description={descricaoCabecalho(visao, dataSelecionada, dataHoje)}
        actions={
          plano && visao === 'plano' ? (
            <span className={`badge status-plano-${plano.status.toLowerCase()}`}>
              {labelStatusPlano(plano.status)}
            </span>
          ) : null
        }
      />
      <OnboardingChecklist
        temPlano={plano != null}
        onGerarPlano={podeGerar ? () => void home.handleGerar() : undefined}
      />

      <div className="home-visao" role="group" aria-label="Visão do dia">
        <div className="segmented">
          <button
            type="button"
            className={
              visao === 'plano' ? 'segmented__item segmented__item--ativo' : 'segmented__item'
            }
            aria-pressed={visao === 'plano'}
            onClick={() => setVisao('plano')}
          >
            Plano
          </button>
          <button
            type="button"
            className={
              visao === 'habitos' ? 'segmented__item segmented__item--ativo' : 'segmented__item'
            }
            aria-pressed={visao === 'habitos'}
            onClick={() => setVisao('habitos')}
          >
            {rotuloHabitos}
          </button>
        </div>
      </div>

      {visao === 'habitos' ? (
        <section className="plano-dia card home-habitos" aria-label="Hábitos do dia">
          {carregandoHabitos ? <Skeleton variant="plano-blocos" /> : null}
          {erroHabitos ? <InlineAlert>{erroHabitos}</InlineAlert> : null}
          {!carregandoHabitos ? (
            <HabitoDoDiaLista
              habitos={habitosDoDia}
              data={dataSelecionada}
              onAtualizado={setHabitosDoDia}
              tituloSecao="Para executar hoje"
            />
          ) : null}
        </section>
      ) : (
        <section className="plano-dia card" aria-label="Plano do dia">
          {home.explicacaoReservas !== null ? (
            <p className="plano-dia-reservas-hint" role="note">
              {home.explicacaoReservas}
            </p>
          ) : null}

          {carregandoDatas ? (
            <div className="proposta-dias proposta-dias--carregando" aria-hidden="true" />
          ) : datasProposta && datasProposta.length > 1 ? (
            <nav className="proposta-dias" aria-label="Dias da proposta">
              <p className="proposta-dias__hint">Proposta multi-dia — escolha o dia para revisar</p>
              <div className="segmented" role="group" aria-label="Dia da proposta">
                {datasProposta.map((data) => (
                  <button
                    key={data}
                    type="button"
                    className={
                      data === dataSelecionada
                        ? 'segmented__item segmented__item--ativo'
                        : 'segmented__item'
                    }
                    aria-current={data === dataSelecionada ? 'date' : undefined}
                    onClick={() => void home.handleSelecionarDiaProposta(data)}
                  >
                    {data === dataHoje ? 'Hoje' : formatarDiaCurto(data)}
                  </button>
                ))}
              </div>
            </nav>
          ) : null}

          {carregando ? <Skeleton variant="plano-blocos" /> : null}

          {erro ? <InlineAlert>{erro}</InlineAlert> : null}

          {!carregando && !erro && !plano ? (
            <>
              {ajustarCapacidade}
              <EmptyStatePlano
                gerando={home.gerando}
                onGerar={() => void home.handleGerar()}
                onAjustarCapacidade={() => home.setAjustarCapacidadeAberto(true)}
              />
            </>
          ) : null}

          {!carregando && !erro && plano ? (
            <div
              className={
                conteudoOcupado
                  ? 'plano-dia-conteudo plano-dia-conteudo--ocupado'
                  : 'plano-dia-conteudo'
              }
              aria-busy={conteudoOcupado}
            >
              {diaCompleto ? <DiaConcluido xpGanho={home.xpGanhoSessao} /> : null}

              <CapacidadeBar
                usadosMinutos={usados}
                disponiveisMinutos={disponiveis}
                sobrecarga={plano.temConflito}
                versao={plano.versao}
                blocos={
                  ehAprovado
                    ? { total: itensExecucao.length, concluidos }
                    : { total: plano.blocos.length }
                }
              />

              {ajustarCapacidade}

              {mostrarBannerConflito && plano.mensagem ? (
                <ConflitoBanner
                  mensagem={plano.mensagem}
                  temConflito={plano.temConflito}
                  itensNaoAlocados={plano.itensNaoAlocados}
                  onRemoverMenorPrioridade={
                    plano.temConflito && ehRascunho
                      ? () => void home.handleRemoverMenorPrioridade()
                      : undefined
                  }
                  onAumentarCapacidade={
                    plano.temConflito ? () => home.setAjustarCapacidadeAberto(true) : undefined
                  }
                  onManterAssim={plano.temConflito ? home.dispensarConflito : undefined}
                  onAdicionarOmitido={
                    plano.temConflito && ehRascunho
                      ? (item) => void home.handleAdicionarOmitido(item)
                      : undefined
                  }
                  adicionandoId={home.tarefaEmAdicao?.id ?? null}
                  removendo={pendentes.size > 0}
                />
              ) : null}

              {home.titulosRemovidosDiff.length > 0 ? (
                <p className="plano-diff-removidos" role="status">
                  Removidos na nova versão: {home.titulosRemovidosDiff.join(', ')}
                </p>
              ) : null}

              {podeGerar || ehRascunho ? (
                <div className="plano-dia-acoes">
                  {podeGerar ? (
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => void home.handleGerar()}
                      disabled={home.gerando || home.aprovando || home.resolvendoProposta}
                      aria-busy={home.gerando}
                    >
                      {home.gerando ? 'Gerando…' : 'Gerar nova versão'}
                    </button>
                  ) : null}
                  {propostaPendente ? (
                    <>
                      <button
                        type="button"
                        className="btn btn--primary"
                        onClick={() => void home.handleAceitarProposta()}
                        disabled={home.resolvendoProposta || home.gerando}
                        aria-busy={home.resolvendoProposta}
                      >
                        {home.resolvendoProposta ? 'Aceitando…' : 'Aceitar proposta'}
                      </button>
                      <button
                        type="button"
                        className="btn btn--secondary"
                        onClick={() => void home.handleDescartarProposta()}
                        disabled={home.resolvendoProposta || home.gerando}
                      >
                        Descartar
                      </button>
                    </>
                  ) : ehRascunho ? (
                    <button
                      type="button"
                      className="btn btn--primary"
                      onClick={() => void home.handleAprovar()}
                      disabled={home.aprovando || home.gerando}
                      aria-busy={home.aprovando}
                    >
                      {home.aprovando ? 'Aprovando…' : rotuloAprovar(plano.blocos.length, usados)}
                    </button>
                  ) : null}
                </div>
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
                      const pendente = pendentes.has(item.bloco.id)
                      const titulo = tituloDoItemExecucao(item)
                      const origem = item.habitoResumo ? 'HABITO' : 'TAREFA'
                      const habitoId = item.habitoResumo?.id
                      const bloqueada = origem === 'TAREFA' && (item.tarefaResumo?.bloqueada ?? false)
                      const espera = bloqueada
                        ? (rotuloEspera(item.tarefaResumo?.esperas) ?? 'Bloqueada por espera')
                        : null
                      const podeIniciar =
                        (status === 'NAO_INICIADO' || status === 'ABORTADO') && !bloqueada
                      return (
                        <BlocoCard
                          key={item.bloco.id}
                          ordem={item.bloco.ordem}
                          titulo={titulo}
                          origem={origem}
                          tarefaId={item.tarefaResumo?.id}
                          habitoId={habitoId}
                          duracaoMinutos={item.bloco.duracaoMinutos}
                          prioridade={item.tarefaResumo?.prioridade}
                          projetoNome={item.tarefaResumo?.projetoNome}
                          horarioPreferencial={item.habitoResumo?.horarioPreferencial}
                          statusExecucao={status}
                          pendente={pendente}
                          espera={espera}
                          acoes={
                            podeIniciar ? (
                              <>
                                <button
                                  type="button"
                                  className="btn btn--primary btn--sm"
                                  onClick={() => {
                                    void (async () => {
                                      registrarInicio(titulo, item.bloco.duracaoMinutos, {
                                        id: null,
                                        blocoPlanoId: item.bloco.id,
                                        status: 'EM_ANDAMENTO',
                                        iniciadoEm: new Date().toISOString(),
                                        concluidoEm: null,
                                        abortadoEm: null,
                                      })
                                      const resposta = await home.handleIniciar(item)
                                      if (resposta) {
                                        registrarInicio(titulo, item.bloco.duracaoMinutos, resposta)
                                      } else {
                                        await refrescarDoDia()
                                      }
                                    })()
                                  }}
                                  disabled={pendente}
                                  aria-label={`Iniciar ${titulo}`}
                                >
                                  <PlayIcon aria-hidden />
                                  <span className="plano-bloco-acao-rotulo">Iniciar</span>
                                </button>
                                {origem === 'HABITO' && habitoId ? (
                                  <button
                                    type="button"
                                    className="btn btn--secondary btn--sm"
                                    disabled={pendente}
                                    onClick={() => {
                                      void (async () => {
                                        try {
                                          const execucao = await concluirHabitoDireto(
                                            habitoId,
                                            dataSelecionada,
                                          )
                                          const xp = execucao.xp?.ganhoNesteRequest ?? 0
                                          toast.success(xp > 0 ? `+${xp} XP` : 'Hábito concluído')
                                          await home.recarregarExecucoes()
                                          const lista = await listarHabitosDoDia(dataSelecionada)
                                          setHabitosDoDia(lista)
                                        } catch (error) {
                                          toast.error(
                                            error instanceof Error
                                              ? error.message
                                              : 'Não foi possível concluir o hábito.',
                                          )
                                        }
                                      })()
                                    }}
                                    aria-label={`Concluir direto ${titulo}`}
                                  >
                                    <CheckIcon aria-hidden />
                                    <span className="plano-bloco-acao-rotulo">Concluir</span>
                                  </button>
                                ) : null}
                                {origem === 'TAREFA' && item.tarefaResumo?.id ? (
                                  <button
                                    type="button"
                                    className="btn btn--secondary btn--sm"
                                    disabled={pendente}
                                    onClick={() => {
                                      void (async () => {
                                        try {
                                          const execucao = await concluirTarefaDireto(
                                            item.tarefaResumo!.id,
                                            {
                                              data: dataSelecionada,
                                              blocoId: item.bloco.id,
                                            },
                                          )
                                          const xp = execucao.xp?.ganhoNesteRequest ?? 0
                                          toast.success(xp > 0 ? `+${xp} XP` : 'Sessão concluída')
                                          await home.recarregarExecucoes()
                                          await refrescarDoDia()
                                        } catch (error) {
                                          toast.error(
                                            error instanceof Error
                                              ? error.message
                                              : 'Não foi possível concluir a tarefa.',
                                          )
                                        }
                                      })()
                                    }}
                                    aria-label={`Concluir direto ${titulo}`}
                                  >
                                    <CheckIcon aria-hidden />
                                    <span className="plano-bloco-acao-rotulo">Concluir</span>
                                  </button>
                                ) : null}
                              </>
                            ) : null
                          }
                        />
                      )
                    })}
                  </ol>
                )
              ) : plano.blocos.length === 0 && !home.tarefaEmAdicao ? (
                <p className="plano-dia-vazio" role="status">
                  Nenhum bloco neste plano.
                </p>
              ) : (
                <ol className="plano-blocos">
                  {plano.blocos.map((bloco) => {
                    const origem = origemDoBloco(bloco)
                    const ehNovo =
                      home.snapshotAnterior != null &&
                      !home.snapshotAnterior.origemIds.has(chaveOrigemBloco(bloco))
                    return (
                      <BlocoCard
                        key={bloco.id}
                        ordem={bloco.ordem}
                        titulo={tituloDoBloco(bloco)}
                        origem={origem}
                        tarefaId={bloco.tarefaId ?? bloco.tarefa?.id}
                        habitoId={bloco.habitoId ?? bloco.habito?.id}
                        duracaoMinutos={bloco.duracaoMinutos}
                        prioridade={bloco.tarefa?.prioridade}
                        prazo={bloco.tarefa?.prazo}
                        projetoNome={bloco.tarefa?.projetoNome}
                        horarioPreferencial={bloco.habito?.horarioPreferencial}
                        motivoRecorrencia={
                          ehBlocoHabito(bloco) ? 'Recorrente · dias do hábito' : null
                        }
                        mostrarJustificativa={ehRascunho}
                        diff={ehNovo ? 'novo' : null}
                        onRemover={ehRascunho ? () => void home.handleRemover(bloco.id) : undefined}
                      />
                    )
                  })}
                  {home.tarefaEmAdicao ? (
                    <li className="plano-bloco plano-bloco--pendente" aria-busy="true">
                      <span className="plano-bloco-ordem">+</span>
                      <div className="plano-bloco-corpo">
                        <p className="plano-bloco-titulo">{home.tarefaEmAdicao.titulo}</p>
                        <p className="plano-bloco-meta">Adicionando…</p>
                      </div>
                    </li>
                  ) : null}
                </ol>
              )}

              {ehRascunho ? (
                <AdicionarTarefaForm
                  elegiveis={home.elegiveis}
                  tarefaSelecionadaId={home.tarefaSelecionadaId}
                  carregandoElegiveis={home.carregandoElegiveis}
                  adicionando={home.tarefaEmAdicao != null}
                  onSelecionar={home.setTarefaSelecionadaId}
                  onSubmit={(event) => void home.handleAdicionar(event)}
                />
              ) : null}
            </div>
          ) : null}
        </section>
      )}
    </main>
  )
}
