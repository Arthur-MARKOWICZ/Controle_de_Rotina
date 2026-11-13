# Design

## Context

- `Execucao` guarda `status`, `iniciadoEm`, `concluidoEm`, `abortadoEm`; o bloco (`BlocoPlano`) guarda `duracaoMinutos` planejado e `tarefaId` **ou** `habitoId`.
- `reiniciar()` (depois de um abort) sobrescreve `iniciadoEm`, então o tempo real medido é só o da última tentativa.
- `concluirDireto*` grava `iniciadoEm = concluidoEm` — não há tempo real nesse caminho.
- Tags de tarefa vivem em `tarefa_tag` (`TarefaTagRepository.findByTarefaIdIn`); projeto em `Tarefa.projetoId`; projeto arquivado continua no banco (`StatusProjeto.ARQUIVADO`).
- `historico` já tem o padrão: controller fino → `HistoricoService` (`@Transactional(readOnly = true)`, `Clock` injetado) → função pura (`HistoricoAgregador`) testada sem Spring; `PeriodoBRT` resolve dia/semana em BRT.
- Front: `vite.config.ts` faz proxy de subpaths **exatos** de `/historico` (a rota `/historico` é SPA); `nginx.conf` já usa `location ^~ /historico/`.

## Goals / Non-Goals

**Goals:**
- Um endpoint de leitura, uma consulta ao banco para as amostras, cálculo em função pura testável com dados em memória.
- Cartão isolado na página: erro/carregamento dele não afeta os resumos existentes.

**Non-Goals:**
- Persistir o fator ou cachear o resultado (é leitura barata sobre 90 dias).
- Expor parâmetros de janela, mínimo ou corte de outlier na API.
- Alterar `MotorAlocacao` ou qualquer fluxo de execução.

## Decisions

1. **Endpoint em `HistoricoController` (`GET /historico/calibracao`)**, novo método em `HistoricoService` (ou `CalibracaoService` no mesmo package se o service ficar grande). Não cria prefixo; atende o critério da issue.

2. **Consulta única de amostras** em `ExecucaoRepository` (JPQL com join teta, igual às consultas atuais):
   `SELECT e.iniciadoEm, e.concluidoEm, b.duracaoMinutos, t.id, t.projetoId FROM Execucao e, BlocoPlano b, Tarefa t WHERE e.blocoPlanoId = b.id AND b.tarefaId = t.id AND e.usuarioId = :usuarioId AND e.status = CONCLUIDO AND e.concluidoEm >= :inicio AND e.concluidoEm < :fim AND e.iniciadoEm IS NOT NULL`
   O join com `Tarefa` exclui hábitos naturalmente. Retorno mapeado para um record (`AmostraExecucao`) via construtor JPQL ou `Object[]` convertido no service. Depois: `TarefaTagRepository.findByTarefaIdIn(tarefaIds)` + `TagRepository.findAllById` e `ProjetoRepository.findAllById` — 4 queries fixas, sem N+1.
   *Alternativa:* uma query só com `LEFT JOIN` em tag/projeto — duplica linhas por tag e complica a contagem; descartada.

3. **Tarefa soft-deletada entra.** A execução aconteceu; o tempo é real. Não filtrar `deletedAt` mantém a consulta simples. Os vínculos de tag dela continuam no banco, então aparece nos recortes também.

4. **Calculador puro `CalibracaoCalculadora`** (package `historico`), recebendo a lista de amostras já com `tagIds` e `projetoId` e devolvendo os recortes. Constantes: `MINIMO_AMOSTRAS = 5`, `RAZAO_MINIMA = 0.25`, `RAZAO_MAXIMA = 4.0`, `JANELA_DIAS = 90`. Minutos reais = `Duration.between(iniciadoEm, concluidoEm).toSeconds() / 60.0` (sem arredondar antes da razão). Descarte: `concluidoEm <= iniciadoEm` ou razão individual fora de `[0,25 ; 4,0]`.
   *Por que razão ponderada (Σreal ÷ Σplanejado) e não média das razões:* blocos curtos têm razões voláteis (5 min → 10 min = 2×) e dominariam a média; a ponderada responde "quanto tempo a mais no total".
   *Por que corte fixo [0,25 ; 4] e não IQR:* com 5–20 amostras o IQR é instável; o corte fixo é explicável ao usuário e cobre os dois casos da issue (clique acidental, sessão esquecida). O limite é simétrico em escala log (÷4 / ×4).

5. **Janela** via `PeriodoBRT`: hoje BRT pelo `Clock`, início = `hoje − 89` às 00:00 BRT, fim = `hoje + 1` às 00:00 BRT. Adicionar helper `PeriodoBRT.intervaloUltimosDias(hoje, 90)` reaproveita o padrão e é testável na virada de dia.

6. **DTOs** (records em `historico/dto`):
   - `CalibracaoResponse(JanelaResponse janela, int minimoAmostras, RecorteCalibracaoResponse geral, List<RecorteTagResponse> porTag, List<RecorteProjetoResponse> porProjeto)`
   - Métricas comuns: `int amostras, boolean suficiente, BigDecimal razao /* null */, BigDecimal desvioMedioMinutos /* null */`. `BigDecimal` com `setScale(2|1, HALF_UP)` evita `1.2500000001` no JSON.
   - Tag: `tagId, nome, cor` + métricas; Projeto: `projetoId, nome` + métricas.

7. **Front:** `consultarCalibracao()` em `historicoApi.ts` seguindo o padrão (`apiFetch` + `lerErroApi` + `readApiJson`). Componente `CartaoCalibracao.tsx` em `src/historico/` com seu próprio `useEffect` de carga (montado uma vez na página, fora do efeito que depende de `visao`/período). Formatação (`formatarRazao`, frase por faixa) em `formatacao.ts` para testar sem render. Estilo em `styles/historico.css` reaproveitando os tokens/padrão de cartão existentes; skeleton e `InlineAlert` já existentes.

8. **Proxy:** adicionar `'/historico/calibracao'` à lista de `vite.config.ts` e às linhas de `/historico/*` em `API_PROXY.md`. `nginx.conf` só tem o comentário de prefixos — sem mudança funcional.

## Risks / Trade-offs

- [Poucas amostras geram ruído] → mínimo de 5 por recorte, "sem dados" em vez de número, N sempre exibido.
- [Sessão esquecida que não passa de 4×] (ex.: 30 min esquecido por 1h50) → ainda distorce; aceito na fase 1, o corte é constante única e fácil de ajustar depois.
- [Abort + reiniciar mede só a última tentativa] → subestima o real nesses casos; documentado, raro, e não há dado para fazer melhor sem mudar o modelo.
- [Tags atuais, não da época] → renomear/reatribuir tag muda o histórico do recorte; aceitável para uma leitura de tendência.
- [Desempenho] → 90 dias de execuções de um usuário são centenas de linhas, e o índice `idx_execucao_usuario_status_concluido (usuario_id, status, concluido_em)` (V8) já cobre o filtro. Sem migração.

## Migration Plan

Sem migração de dados. Deploy normal backend + frontend (rebuild da imagem do front por causa do bundle; `nginx.conf` inalterado). Rollback = reverter o deploy; nada é persistido.
