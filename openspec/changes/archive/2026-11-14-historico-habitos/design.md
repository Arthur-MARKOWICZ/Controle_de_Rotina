# Design

## Context

- Um cumprimento de hábito é uma `execucao` `CONCLUIDO` ligada a um `bloco_plano` com `habito_id`, cujo `plano.data` é o dia civil. O plano pode ser `APROVADO` ou `HABITO_AVULSO`. No mesmo dia pode haver mais de um bloco do hábito (bloco do aprovado + avulso adicional).
- `bloco_plano.duracao_minutos` guarda a duração do hábito no momento em que o bloco foi criado. É a meta do dia.
- O concluir direto (`Execucao.concluirDireto` / `concluirDiretoNova`) grava `iniciadoEm = concluidoEm`. `ExecucaoService.finalizarHabitoAposConclusao` calcula `XpMotor.minutosEfetivos` com esses instantes e obtém 0, então hoje o concluir direto de hábito credita 0 XP.
- Se o bloco já está `EM_ANDAMENTO`, `concluirHabitoDiretoNoBloco` conclui pelo timer (`concluirBlocoHabito`).
- Já existem no projeto: `IntervaloCapacidadeInvalidoException` ("Intervalo inválido") com a validação `CalendarioAgregador.validarIntervalo` (`dataDe`/`dataAte`), `SessaoDuracaoDialog` no front, e o padrão de body opcional `SessaoTarefaRequest` no concluir direto de tarefa.

## Goals / Non-Goals

**Goals:**
- Gravar os minutos informados sem perder os instantes reais da conclusão.
- Calcular o histórico na leitura a partir de dados já existentes, sem tabela de agregação.

**Non-Goals:**
- Mostrar dias agendados sem cumprimento (faltas). Isso exigiria guardar a agenda do hábito ao longo do tempo.
- Paginação do histórico: o intervalo máximo de 366 dias limita a resposta a 366 itens.

## Decisions

### D1. Coluna `execucao.minutos_informados` (nullable)
Migração `V23__execucao_minutos_informados.sql`: `INTEGER NULL`, `CHECK (minutos_informados IS NULL OR minutos_informados BETWEEN 1 AND 1440)`. Fica preenchida só no concluir direto de hábito. Timer, tarefas e execuções antigas ficam `NULL`.
- *Alternativa rejeitada*: recuar `iniciadoEm` para `concluidoEm − minutos`. Inventaria um horário de início que não aconteceu, impediria saber a origem do tempo e contaminaria qualquer leitura futura dos instantes.
- *Execuções antigas de concluir direto* (`iniciadoEm = concluidoEm`, `minutos_informados NULL`): no histórico, tempo = 0 e origem `TIMER`. Não há backfill: é impossível saber o tempo real. Ver Risks.

### D2. Body opcional no concluir direto de hábito
Novo record `ConcluirHabitoDiretoRequest(Integer minutos)`, `@RequestBody(required = false)` no `HabitoController.concluirDireto`. O `data` continua como query param, para não quebrar o contrato atual. A validação (1..1440) fica no serviço e lança a nova `MinutosInformadosInvalidosException` (400, title "Minutos informados inválidos", propriedade `minutosMaximo`), antes de qualquer escrita. Sem `minutos`, o serviço usa `bloco.getDuracaoMinutos()`.
- Os minutos chegam a `ExecucaoService.concluirHabitoDiretoNoBloco` como parâmetro. Nos ramos `NAO_INICIADO`/`ABORTADO`/nova execução eles são gravados com `Execucao.concluirDireto(agora, minutos)`. No ramo `EM_ANDAMENTO` são ignorados e vale o timer, como diz a spec.
- O limite de 1440 é um teto de sanidade (um dia), não a duração do hábito. Assim o usuário pode registrar que fez mais do que a meta.

### D3. XP pelos minutos informados
Nova sobrecarga `XpMotor.minutosEfetivos(int duracaoBloco, int minutosDecorridos)`, com a mesma regra (crédito cheio a partir de 80%, teto na duração). A versão por instantes passa a delegar para ela. `finalizarHabitoAposConclusao` usa os minutos informados. Assim o timer e o concluir direto compartilham uma única regra.

### D4. Histórico calculado na leitura
- Query nova em `ExecucaoRepository`: para `habitoId`, `usuarioId`, status `CONCLUIDO` e `plano.data` no intervalo, retorna `plano.data`, `bloco.duracaoMinutos`, `iniciadoEm`, `concluidoEm` e `minutosInformados`. Uma única consulta, sem N+1.
- `HabitoHistoricoService` agrupa por data:
  - minutos da execução = `minutosInformados` se não nulo, senão `round((concluidoEm − iniciadoEm) / 60s)` (bruto, sem o crédito de 80%);
  - `minutosDedicados` = soma; `metaMinutos` = maior `duracaoMinutos` do dia; `suficiente` = `minutosDedicados >= metaMinutos`;
  - `origem` = `TIMER` (todas sem informado), `INFORMADO` (todas com informado) ou `MISTO`.
- A posse é verificada com `habitoRepository.findByIdAndUsuarioId`, que inclui excluídos, como o `GET /habitos/{id}`. Se não achar, lança `HabitoNaoEncontradoException` (404).
- Intervalo: se `dataDe` e `dataAte` estão ausentes, usa `hoje − 83 .. hoje` em BRT via `Clock`. Se só um vem, é 400. A validação reaproveita a regra de `CalendarioAgregador.validarIntervalo`, extraída para um validador compartilhado com limite como parâmetro (366 aqui; o calendário mantém o seu).
- Endpoint `GET /habitos/{id}/historico` fica no `HabitoController`, sob o prefixo `/habitos`, sem mudança de proxy.
- DTO: `HabitoHistoricoDiaResponse(LocalDate data, int minutosDedicados, int metaMinutos, boolean suficiente, OrigemTempo origem)`, com a resposta em `List<...>`.

### D5. Front
- `habitoApi.ts`: `concluirHabitoDireto(habitoId, data?, minutos?)` passa a enviar body JSON quando `minutos` vem. Novo `buscarHistoricoHabito(habitoId, dataDe?, dataAte?)`.
- Diálogo: um `MinutosHabitoDialog` próprio, em vez de generalizar `SessaoDuracaoDialog`. O de tarefa tem semântica de "restante" com teto na tarefa e presets filtrados por ele. O de hábito tem campo pré-preenchido com a duração e teto de 1440. Generalizar misturaria duas regras de validação.
- Os três pontos de concluir direto (`HabitoCard`, `HabitoDoDiaLista`, bloco de hábito em `HomePage`) abrem o diálogo antes de chamar a API.
- `HabitoHistoricoSecao` é renderizada no `HabitoDrawer` só em modo `editar`, carregada de forma independente do formulário. Assim um erro dela não bloqueia a edição.

## Risks / Trade-offs

- [Concluir direto antigo aparece com 0 min e "Faltaram N min"] → Mitigação: no front, linha com `origem` `TIMER` e `minutosDedicados` 0 mostra "Tempo não registrado" em vez de "Faltaram". Documentado no teste do componente.
- [Mudança de XP para mobile/MCP: concluir direto sem body passa de 0 XP para a duração cheia] → É a correção de um bug. Fica registrada na proposta como BREAKING de comportamento. Nenhum cliente depende de receber 0.
- [Timer esquecido ligado gera muitos minutos dedicados] → Aceito: o histórico mostra o tempo bruto. Não afeta XP, que tem teto na duração.
- [Faixa de 80–99%: XP cheio e "insuficiente"] → Intencional. A UI mostra "Faltaram N min" para deixar claro que são métricas diferentes.

## Migration Plan

1. Deploy do backend com `V23` (coluna nullable, sem backfill; compatível com o front antigo, que não manda body).
2. Deploy do front com o diálogo e a seção de histórico.
3. Rollback: reverter o front primeiro. O backend antigo ignora o body. A coluna nullable pode ficar no banco.
