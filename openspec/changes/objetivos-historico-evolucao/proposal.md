# Proposal

## Why

O usuário já vê quanto falta em cada objetivo (barra da #94), mas não vê **o que já alcançou** nem **como chegou lá**. A lista separa concluídos e arquivados em duas visões, e o card de um objetivo arquivado direto de ativo não mostra data nenhuma. O detalhe também não mostra a evolução no tempo. Esta change é a issue #95 (parte da #65) e fecha o histórico de objetivos.

## What Changes

**A. Objetivos encerrados**
- `GET /objetivos` passa a aceitar `status` **repetido** (`?status=CONCLUIDO&status=ARQUIVADO`). Um único valor continua funcionando como hoje.
  - Só status encerrados → ordem por **data de encerramento** decrescente. A data de encerramento é `concluidoEm` quando existe, senão `arquivadoEm`. Um objetivo concluído e depois arquivado fica na data em que foi concluído.
  - `ATIVO` junto com um status encerrado → 400 `title` "Combinação de status inválida".
  - Nova coluna gerada `objetivo.encerrado_em` para ordenar e indexar.
- Na página `/objetivos`, as visões viram **Ativos | Encerrados**. **BREAKING (URL):** `?status=CONCLUIDO` e `?status=ARQUIVADO` passam a abrir Encerrados. A URL nova é `?visao=encerrados`.
- O card de encerrado mostra as datas: "Concluído em X", "Arquivado em Y" ou as duas. Arquivar um concluído não tira o card da visão: ele é atualizado no lugar.

**B. Evolução de um objetivo**
- Nova coluna `tarefa.finalizada_em`: preenchida quando a tarefa vira `FINALIZADA` e limpa quando sai desse status, por qualquer caminho (PATCH, execução, cascata pai/filhos).
  - A migration preenche as tarefas já finalizadas a partir do evento de XP `TAREFA_FINALIZADA` quando ele existe. As demais ficam sem data.
- Novo `GET /objetivos/{id}/evolucao`: minutos concluídos **acumulados por dia** (BRT), na mesma base e com os mesmos limites do progresso. O último acumulado é igual a `progresso.minutosConcluidos`.
  - Tarefas sem data de finalização entram num total separado, "antes do histórico".
  - A curva é o retrato do conjunto atual, sem snapshot: reabrir uma tarefa, cancelá-la, pausar um hábito ou desvincular um item tira esses minutos da curva inteira.
- No detalhe, uma seção **Evolução** entre a barra de progresso e os itens:
  - gráfico em degraus com os minutos acumulados;
  - eixo até hoje para objetivo ativo, e até o mais tarde entre o encerramento e o último ponto para encerrado, com marcador no encerramento;
  - estados de carregando, erro e vazio, e tabela acessível com os pontos.

**Fora do escopo:**
- snapshot diário de percentual;
- `finalizadaEm` nas respostas de tarefa;
- filtro ou busca dentro de Encerrados;
- evolução no card da lista;
- mudanças em MCP (além dos tipos regenerados) e mobile.

**Dependência:** arquivar antes `objetivos-itens-vinculados-ui` (#92) e depois `objetivos-barra-progresso` (#94). Os requisitos de `ui-objetivo` modificados aqui partem do texto que essas duas changes deixam.

## Capabilities

### New Capabilities
- `objetivo-evolucao`: série de minutos concluídos acumulados por dia de um objetivo, com a data de finalização de tarefa que a alimenta.

### Modified Capabilities
- `objetivo`: a listagem aceita vários status e ordena os encerrados pela data de encerramento.
- `ui-objetivo`:
  - as visões Concluídos e Arquivados viram uma só (Encerrados), e os requisitos que citam essas visões passam a citar Encerrados;
  - o card de encerrado mostra as datas de conclusão e de arquivamento;
  - entra a seção de evolução no detalhe.

## Impact

- **Backend:**
  - Migrations: `tarefa.finalizada_em` com preenchimento a partir de `xp_evento`; `objetivo.encerrado_em` gerada, com índice `(usuario_id, encerrado_em)`.
  - `tarefa/entity/Tarefa.java` (`atualizar`).
  - `objetivo`: `ObjetivoController`, `ObjetivoService` (ordem e validação dos status), `ObjetivoSpecs`, `Objetivo`, novo `ObjetivoEvolucaoService` e DTO de resposta.
  - `execucao/repository/ExecucaoRepository.java`: soma por tarefa e dia.
  - Nova exceção para a combinação inválida.
- **Contrato:**
  - `status` em array;
  - `/objetivos/{id}/evolucao` e o DTO novo;
  - regenerar `contract/openapi.json` e os `schema.d.ts` do front e do MCP.
- **Proxy:** nada muda, porque o prefixo `/objetivos` já está no proxy.
- **Frontend web:**
  - `objetivo/pages/ObjetivosPage.tsx`, `ObjetivoCard.tsx`, `prazoObjetivo.ts`, `api/objetivoApi.ts`;
  - `pages/ObjetivoDetalhePage.tsx` e o novo `ObjetivoEvolucao.tsx`;
  - `styles/objetivos.css`;
  - os testes de cada um.
- **Cobertura:** os testes novos contam para os gates do backend e do frontend, e o limite só sobe.
