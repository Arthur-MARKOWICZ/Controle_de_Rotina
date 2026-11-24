# Design

## Context

A motivação está em `proposal.md`. O comportamento está em `specs/objetivo`, `specs/objetivo-evolucao` e `specs/ui-objetivo`.

Estado atual da branch `objetivos`, já com a #92 e a #94:

- **Listagem.** `ObjetivoService.listar` filtra um único `StatusObjetivo` (`ObjetivoSpecs.comStatus`). Sem `sort` do cliente, usa `ordemPadrao(status)`: `prazo` para ATIVO, `concluidoEm desc` para CONCLUIDO e `arquivadoEm desc` para ARQUIVADO. Arquivar um CONCLUIDO mantém `concluidoEm`.
- **Progresso.** `ObjetivoProgressoService` calcula a partir de `ObjetivoConjunto`, que vem de `ObjetivoVinculoService.resolverConjunto(s)`:
  - tarefas raiz deduplicadas (direto + via projeto) e hábitos com `vinculoCreatedAt`;
  - as regras de contagem (`contaNoProgresso`, `janela`, `diasEsperados`) são métodos privados.
- **Minutos.**
  - Tarefa: `ExecucaoRepository.somarDuracaoMinutosPorTarefasEStatus` soma `b.duracaoMinutos` das execuções `CONCLUIDO`, sem data.
  - Hábito: `listarCumprimentosHabitosNoIntervalo` devolve `CumprimentoHabitoLinha` com `data` (= `plano.data`) e `minutosDedicados`.
- **Finalização de tarefa.** Todos os caminhos chamam `Tarefa.atualizar(…, status, …, agora)`:
  - PATCH (`TarefaService.atualizar`);
  - cascata pai/filhos (`aplicarCascataDeStatus`, `finalizarPaiSeTodosFilhosFinalizados`, `reabrirPaiSeFinalizada`);
  - nova subtarefa reabre o pai (`TarefaService:149`);
  - execução que completa a duração (`ExecucaoService:615/634`).
- **XP.** `xp_evento` tem um `TAREFA_FINALIZADA` por tarefa, com `created_at`. Ele só é criado quando a finalização vem de execução e o bônus é maior que zero (`XpConcessaoService.concederAoConcluir`).
- **Banco.** Postgres via Testcontainers e `ddl-auto: validate`. Última migration: `V26`.
- **Front, lista.** `ObjetivosPage` lê `?status=` (padrão ATIVO) e recarrega via `recarregarEm` depois de qualquer ação.
- **Front, detalhe.** `ObjetivoDetalhePage` mantém `objetivo` e `itens` em fetches independentes. Há um gráfico de colunas em CSS em `historico/GraficoXpSemana.tsx`, com tabela `visually-hidden`. Não há biblioteca de gráficos.

## Goals / Non-Goals

**Goals:**
- Evolução e progresso não podem divergir. As regras de contagem ficam num só lugar e um teste garante que o último acumulado é igual a `minutosConcluidos`.
- `finalizada_em` é mantido num único ponto (a entidade), sem tocar em cada serviço.
- O cálculo da evolução é um passe em memória sobre o mesmo `ObjetivoConjunto`, com no máximo duas consultas de minutos: uma de tarefas e uma de hábitos.

**Non-Goals:**
- Cache ou materialização da série.
- Expor `encerradoEm` ou `finalizadaEm` nas respostas.
- Biblioteca de gráficos.

## Decisions

### 1. `objetivo.encerrado_em` como coluna gerada no Postgres

```sql
ALTER TABLE objetivo
  ADD COLUMN encerrado_em TIMESTAMPTZ GENERATED ALWAYS AS (COALESCE(concluido_em, arquivado_em)) STORED;
CREATE INDEX idx_objetivo_usuario_encerrado ON objetivo (usuario_id, encerrado_em DESC);
```

A entidade mapeia o campo como `@Column(insertable = false, updatable = false)` e o usa só para ordenar.

**Alternativas descartadas:**
- *ORDER BY COALESCE na Specification* (`query.orderBy(cb.coalesce(...))`): mistura ordenação no filtro e quebra quando o cliente manda `sort`.
- *`@Formula`*: não indexa.
- *Campo preenchido no service*: precisa ser lembrado em concluir, arquivar e reabrir, e pode ficar dessincronizado.

### 2. `status` como lista, validada no service

- O controller recebe `@RequestParam(required = false) List<StatusObjetivo> status`, e o service faz `distinct`.
- Lista vazia vira `[ATIVO]`.
- `ATIVO` junto com um status encerrado lança `CombinacaoStatusInvalidaException` (400, `title` "Combinação de status inválida"), no padrão das outras `ErrorResponseException`.
- `ObjetivoSpecs.comStatus(Collection)` usa `IN`.
- `ordemPadrao` decide pelo conjunto:
  - `{ATIVO}` ordena por `prazo asc nullsLast, createdAt asc`;
  - qualquer conjunto só de encerrados ordena por `encerradoEm desc, createdAt desc`.
- Um `sort` enviado pelo cliente continua tendo precedência.
- Valor inválido continua caindo em "Parâmetro inválido" (conversão do Spring).

**Efeito colateral aceito:** a lista só de ARQUIVADO passa a ordenar um concluído e depois arquivado pela data de conclusão. A spec registra isso no cenário "Arquivado que já foi concluído ordena pela conclusão".

**No contrato:** o parâmetro vira `array` de enum. O MCP só regenera os tipos, porque nenhuma ferramenta dele usa `GET /objetivos`.

### 3. `finalizadaEm` mantido em `Tarefa.atualizar`

```
if (status != null && status != this.status) {
    finalizadaEm = (status == FINALIZADA) ? updatedAt : null;
}
this.status = status;
```

- Reenviar FINALIZADA para uma tarefa que já está FINALIZADA não muda nada.
- O construtor recebe `finalizadaEm = null`. Hoje nenhuma criação nasce FINALIZADA, e um teste trava isso.
- Como todos os caminhos de status passam por `atualizar`, nenhum serviço muda.
- A coluna não entra em `TarefaResponse`.

**Migration `V27__tarefa_finalizada_em.sql`:**

```sql
ALTER TABLE tarefa ADD COLUMN finalizada_em TIMESTAMPTZ;
UPDATE tarefa t SET finalizada_em = x.created_at
  FROM xp_evento x
 WHERE x.tipo = 'TAREFA_FINALIZADA' AND x.origem_id = t.id AND t.status = 'FINALIZADA';
```

Tarefas FINALIZADA sem evento ficam `NULL` e vão para "antes do histórico". A data de uma tarefa refinalizada é a da primeira finalização, e isso é aceito.

**Alternativa descartada:** preencher com `updated_at`. Seria uma data inventada quando a tarefa foi editada depois de finalizada.

### 4. Regras de contagem compartilhadas

As regras privadas de `ObjetivoProgressoService` saem para uma classe do pacote, `RegrasContagemObjetivo`, com métodos estáticos:
- `contaNoProgresso(Tarefa)`;
- `contaNoProgresso(Habito)`;
- `janela(objetivo, vinculoCreatedAt, hoje)`;
- `diasEsperados(habito, janela)`.

Os dois serviços passam a usar essa classe. O progresso não muda de comportamento: os testes atuais dele precisam continuar passando sem alteração.

### 5. `ObjetivoEvolucaoService`: contribuições por item, limite cronológico e agregação por dia

```
conjunto = vinculoService.resolverConjunto(objetivoId)   (após checar dono -> 404)
hoje     = LocalDate.now(clock em BRT)

TAREFAS (contaNoProgresso):
  linhas = execucaoRepository.listarMinutosConcluidosPorTarefas(raízes + filhos não deletados)
           -> (tarefaId, concluidoEm, planoData, minutos=b.duracaoMinutos)
  por raiz: eventos = execuções (dia = BRT(concluidoEm) ou planoData se nulo) ordenados por dia
            se FINALIZADA e finalizadaEm != null: evento SALDO no dia BRT(finalizadaEm), depois das execuções do dia
  aplicar limite = duracaoMinutos: acum += min(min, limite - acum); SALDO = limite - acum
  se FINALIZADA e finalizadaEm == null: antesDoHistorico += limite - acum (após as execuções)

HÁBITOS (contaNoProgresso):
  janela/total iguais ao progresso; linhas de cumprimento com data na janela (dia = plano.data)
  aplicar limite = duracao x diasEsperados, em ordem de data

SÉRIE:
  somar por dia, descartar dias com 0, ordenar, acumulado = antesDoHistorico + soma corrida
```

Novo `ExecucaoRepository.listarMinutosConcluidosPorTarefas(tarefaIds, usuarioId, status)`: o mesmo join de `somarDuracaoMinutosPorTarefasEStatus`, mais `Plano` para `p.data`, sem `GROUP BY`. O mapeamento filho → raiz é o mesmo de `carregarFeitoTarefas`.

**Por que o dia do hábito é a data do plano e não `concluido_em`:** a issue cita `execucao.concluido_em`, mas a janela do hábito no progresso filtra por `plano.data`. Usar outra data para posicionar poderia jogar minutos para fora da janela ou para o dia errado na borda da meia-noite, e a curva deixaria de fechar com o progresso. Para tarefas, a issue é seguida (`concluido_em`), com `plano.data` como fallback.

**Resposta:**
- `ObjetivoEvolucaoResponse(int antesDoHistorico, List<PontoEvolucaoResponse> pontos)`;
- `PontoEvolucaoResponse(LocalDate data, int minutosNoDia, int minutosAcumulados)`;
- nenhum campo é nulo.
- Rota: `GET /objetivos/{id}/evolucao` no `ObjetivoController`, já coberta pelo proxy.

**Invariante testada:** para cada cenário do teste de serviço, `último acumulado == progressoService.calcular(...).minutosConcluidos`. Os dois serviços recebem o mesmo `Clock` fixo.

### 6. Front: visão `encerrados` na URL e lista de status no cliente

- `listarObjetivos({ status?: StatusObjetivo[] })` adiciona um `status` por valor no `URLSearchParams`.
- `ObjetivosPage` troca `lerStatusDaUrl` por `lerVisaoDaUrl`:
  - `?visao=encerrados` → Encerrados;
  - `?status=CONCLUIDO|ARQUIVADO` (legado) → Encerrados, reescrito com `replace` para `?visao=encerrados` mantendo `page`;
  - qualquer outro valor → Ativos.
- Encerrados chama a API com `['CONCLUIDO', 'ARQUIVADO']`.
- Depois de arquivar, o `recarregarEm` que já existe relê a lista. Assim o card arquivado continua na visão, com as datas novas, sem lógica extra.
- `prazoObjetivo.ts` ganha `rotuloArquivamento(arquivadoEm, hojeIso)`, no formato de `rotuloConclusao`. O card de encerrado mostra os dois rótulos que existirem.

### 7. Front: `ObjetivoEvolucao` de apresentação e série pura

- `api/objetivoApi.ts` ganha `obterEvolucaoObjetivo(id)`.
- **Página.** `ObjetivoDetalhePage` faz o fetch da evolução em paralelo com os itens, no mesmo padrão (estado próprio, `AbortController` e `recarregar` após salvar no drawer de edição). A seção não depende de `progresso`: ela some quando a resposta não tem nada.
- **Série pura.** `evolucaoObjetivo.ts::montarSerie(resposta, { status, concluidoEm, arquivadoEm, hojeIso })` devolve:
  - os degraus (x = dia, y = acumulado);
  - o fim do eixo;
  - o marcador de encerramento.

  É uma função pura testada com datas fixas, incluindo a virada de mês.
- **Componente.** `ObjetivoEvolucao` desenha um SVG `viewBox` responsivo (largura 100%) com uma linha em degraus (`path` com `H`/`V`), rótulos da primeira e da última data e um marcador tracejado com texto. Ele também traz uma `<table className="visually-hidden">`, como em `GraficoXpSemana`. As durações usam `formatarCapacidadeMinutos`.
- **Por que SVG e não colunas CSS:** acumulado é uma grandeza contínua no tempo, os degraus mostram melhor os dias parados, e o eixo pode cobrir meses. Colunas CSS com um dia por coluna não escalam para esse período.

## Risks / Trade-offs

- **[A curva reescreve o passado quando o conjunto muda]**: desvincular, pausar ou cancelar remove minutos já mostrados. → Decisão da issue (sem snapshot). A spec registra o efeito para ninguém tratar como bug.
- **[Tarefas antigas sem data concentram minutos em "antes do histórico"]**: em usuários com muitas finalizações manuais antigas, o degrau inicial fica grande. → É temporário, porque toda finalização nova tem data. A UI deixa claro que o valor é anterior ao histórico.
- **[Coluna gerada e `ddl-auto: validate`]**: o Hibernate valida o tipo, mas não a expressão. → O IT de listagem cobre a ordem real no Postgres (Testcontainers). O mapeamento é somente leitura.
- **[Divergência futura entre progresso e evolução]**: alguém muda uma regra num serviço e esquece o outro. → Regras compartilhadas (decisão 4), mais o teste de invariante.
- **[Custo em objetivos grandes]**: a consulta de minutos sem `GROUP BY` traz uma linha por execução. → Para um usuário só, o volume é baixo. Se crescer, basta agrupar por `(tarefaId, dia)` no SQL.
- **[Links de visão compartilhados com `?status=`]**: precisam continuar abrindo. → Leitura do legado com `replace` (decisão 6).

## Migration Plan

1. Arquivar `objetivos-itens-vinculados-ui` e depois `objetivos-barra-progresso`, para que a spec principal de `ui-objetivo` já tenha o detalhe e a barra.
2. Migrations `V27__tarefa_finalizada_em.sql` (coluna e preenchimento) e `V28__objetivo_encerrado_em.sql` (coluna gerada e índice). As duas só adicionam colunas.
3. Backend e contrato no mesmo PR que o front, porque o front passa a mandar `status` repetido e chamar `/evolucao`.
4. **Rollback:** o código antigo ignora as colunas novas, então reverter o deploy basta. Remover as colunas fica para uma migration posterior, se um dia for necessário.
