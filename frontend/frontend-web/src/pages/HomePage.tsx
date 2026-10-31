import { PlayIcon } from '@phosphor-icons/react'

import { InlineAlert } from '../feedback/InlineAlert'
import { Skeleton } from '../feedback/Skeleton'
import { AdicionarTarefaForm } from '../home/AdicionarTarefaForm'
import { AjustarCapacidadeHoje } from '../home/AjustarCapacidadeHoje'
import { BlocoAtivo } from '../home/BlocoAtivo'
import { BlocoCard } from '../home/BlocoCard'
import { CapacidadeBar } from '../home/CapacidadeBar'
import { ConflitoBanner } from '../home/ConflitoBanner'
import { DiaConcluido } from '../home/DiaConcluido'
import { EmptyStatePlano } from '../home/EmptyStatePlano'
import { formatarDataIsoPorExtenso, formatarDiaCurto } from '../home/formatacao'
import { labelStatusPlano } from '../home/labels'
import { OnboardingChecklist } from '../home/OnboardingChecklist'
import { usePlanoDoDia } from '../home/usePlanoDoDia'
import { PageHeader } from '../layout/PageHeader'
import { formatarCapacidadeMinutos, minutosDisponiveis, minutosUsados } from '../planner/data'

function rotuloAprovar(totalBlocos: number, usadosMinutos: number): string {
  const blocos = `${totalBlocos} bloco${totalBlocos === 1 ? '' : 's'}`
  return `Aprovar ${blocos} · ${formatarCapacidadeMinutos(usadosMinutos)}`
}

export function HomePage() {
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

  const ehAprovado = plano?.status === 'APROVADO'
  const usados = plano ? minutosUsados(plano.blocos) : 0
  const disponiveis = minutosDisponiveis(capacidade?.horas, capacidade?.origem)
  const itemAtivo = ehAprovado
    ? itensExecucao.find((item) => item.execucao.status === 'EM_ANDAMENTO')
    : undefined
  const concluidos = itensExecucao.filter((item) => item.execucao.status === 'CONCLUIDO').length
  const diaCompleto = ehAprovado && itensExecucao.length > 0 && concluidos === itensExecucao.length
  const mostrarBannerConflito =
    Boolean(plano?.mensagem) && !(plano?.temConflito && home.conflitoDismissed)
  const conteudoOcupado = home.gerando || home.trocandoDia
  const carregandoDatas = datasProposta === null && Boolean(plano?.propostaId)

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
    <main className={itemAtivo ? 'home-page home-page--com-ativo' : 'home-page'}>
      {home.confirmDialog}
      <PageHeader
        title={formatarDataIsoPorExtenso(dataSelecionada)}
        description={dataSelecionada === dataHoje ? 'Plano de hoje' : 'Dia da proposta'}
        actions={
          plano ? (
            <span className={`badge status-plano-${plano.status.toLowerCase()}`}>
              {labelStatusPlano(plano.status)}
            </span>
          ) : null
        }
      />
      <OnboardingChecklist
        temPlano={plano != null}
        onAbrirQuickAdd={home.abrirQuickAdd}
        onGerarPlano={podeGerar ? () => void home.handleGerar() : undefined}
      />

      {itemAtivo && !carregando ? (
        <BlocoAtivo
          titulo={itemAtivo.tarefaResumo.titulo}
          duracaoMinutos={itemAtivo.bloco.duracaoMinutos}
          iniciadoEm={itemAtivo.execucao.iniciadoEm}
          ocupado={pendentes.has(itemAtivo.bloco.id) || !itemAtivo.execucao.id}
          onConcluir={() => void home.handleConcluir(itemAtivo)}
          onAbortar={() => void home.handleAbortar(itemAtivo)}
        />
      ) : null}

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
              conteudoOcupado ? 'plano-dia-conteudo plano-dia-conteudo--ocupado' : 'plano-dia-conteudo'
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
                onRemoverMenorPrioridade={
                  plano.temConflito && ehRascunho
                    ? () => void home.handleRemoverMenorPrioridade()
                    : undefined
                }
                onAumentarCapacidade={
                  plano.temConflito ? () => home.setAjustarCapacidadeAberto(true) : undefined
                }
                onManterAssim={plano.temConflito ? home.dispensarConflito : undefined}
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
                    const podeIniciar = status === 'NAO_INICIADO' || status === 'ABORTADO'
                    return (
                      <BlocoCard
                        key={item.bloco.id}
                        ordem={item.bloco.ordem}
                        titulo={item.tarefaResumo.titulo}
                        tarefaId={item.tarefaResumo.id}
                        duracaoMinutos={item.bloco.duracaoMinutos}
                        prioridade={item.tarefaResumo.prioridade}
                        projetoNome={item.tarefaResumo.projetoNome}
                        statusExecucao={status}
                        pendente={pendente}
                        acoes={
                          podeIniciar ? (
                            <button
                              type="button"
                              className="btn btn--primary btn--sm"
                              onClick={() => void home.handleIniciar(item)}
                              disabled={pendente}
                              aria-label={`Iniciar ${item.tarefaResumo.titulo}`}
                            >
                              <PlayIcon aria-hidden />
                              <span className="plano-bloco-acao-rotulo">Iniciar</span>
                            </button>
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
                  const ehNovo =
                    home.snapshotAnterior != null &&
                    !home.snapshotAnterior.tarefaIds.has(bloco.tarefa.id)
                  return (
                    <BlocoCard
                      key={bloco.id}
                      ordem={bloco.ordem}
                      titulo={bloco.tarefa.titulo}
                      tarefaId={bloco.tarefa.id}
                      duracaoMinutos={bloco.duracaoMinutos}
                      prioridade={bloco.tarefa.prioridade}
                      prazo={bloco.tarefa.prazo}
                      projetoNome={bloco.tarefa.projetoNome}
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
    </main>
  )
}
