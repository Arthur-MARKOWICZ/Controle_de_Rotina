# Design

## Context

Motivação e escopo: ver `proposal.md`. Requisitos: `specs/ui-revisao-semanal/spec.md` e `specs/pendencia/spec.md`.

Estado atual relevante:

- **Histórico**: `GET /historico/semana` sem `semanaInicio` já resolve a semana corrente em BRT; o front tem `consultarHistoricoSemana()` em `src/historico/api/historicoApi.ts`.
- **Atrasadas**: o backend marca `PENDENTE`/`EM_PROGRESSO` com prazo vencido como `ATRASADA` (`TarefaService`, `atualizarStatusQuandoPrazoPassou`). Um `PATCH` com prazo futuro e sem `status` volta a tarefa para `PENDENTE`; prazo no passado é recusado (`PrazoNoPassadoException`).
- **Esperas**: o bloqueio é calculado em memória por `EsperaService.enriquecerTarefas` (espera própria, pai, projeto origem). Não existe coluna nem consulta SQL que diga "bloqueada", então `TarefaSpecs` não consegue filtrar por isso.
- **Decidir**: `src/decidir/api/decidirApi.ts` tem `listarDecidir`, `promoverDecidir` e `excluirDecidir`. O formulário de promoção (duração, prioridade, prazo) está **inline** em `DecidirPage.tsx`, junto com o estado da página.
- **Planner**: `POST /planos/gerar?data=` cria rascunhos para a janela inteira (até 14 dias, limitada pelo maior prazo) com um `propostaId` comum e pula dias já aprovados, exceto o dia inicial, que responde 409 (`PlanoJaAprovadoException`). `aceitarProposta`/`descartarProposta` e `listarDatasDaProposta` já existem em `planoApi.ts`.
- **Datas no front**: `dataHojeIso()` (BRT) em `src/planner/data.ts`; `inicioSemana()` e `adicionarDiasIso()` em `src/capacidade/semana.ts`.

## Goals / Non-Goals

**Goals:**

- Montar a revisão quase só com o que existe: uma página-orquestradora e quatro componentes de passo, cada um testável isoladamente.
- Fechar a única lacuna de dados (listar bloqueadas paginado) com uma mudança pequena e localizada no backend.
- Não duplicar o formulário de promoção do Decidir.

**Non-Goals:**

- Guardar no servidor que a revisão foi feita, nem retomar uma revisão interrompida depois de recarregar a página (o estado do passo vive só na memória da página).
- Mudar o motor de planejamento ou a janela da proposta para 7 dias exatos.
- Mexer no proxy (não há prefixo novo).

## Decisions

### 1. Filtro `bloqueada` resolvido em duas etapas no `TarefaService.listar`

Quando `bloqueada != null`: (a) buscar, sem paginação, as tarefas que atendem aos demais filtros; (b) passar a lista inteira para `esperaService.idsTarefasBloqueadas`; (c) somar ao `Specification` um `TarefaSpecs.comIds(ids)` (para `true`) ou `semIds(ids)` (para `false`) e paginar normalmente. Com `bloqueada == null`, o caminho atual não muda.

- *Alternativa*: filtrar só a página já carregada. Descartada: quebra `totalElements` e deixa páginas vazias (cenário "Paginação consistente").
- *Alternativa*: expressar o bloqueio em SQL (CTE sobre `espera` + status dos alvos). Descartada por ora: duplicaria a regra de resolução de alvo (tarefa `FINALIZADA`, projeto com todas finalizadas) fora do `EsperaService`, com risco de divergência.
- `bloqueada` entra em `TarefaFiltro` como `Boolean` (nulo = sem filtro). Um valor inválido vira 400 pelo binding padrão do Spring + `ProblemDetail` já existente.

### 2. Página `/revisao` como máquina de passos simples

`src/revisao/RevisaoPage.tsx` guarda `passo` (0–3 | `concluida`) e um contador da sessão (`tarefasTratadas`, `itensDecididos`, `desfechoPlano: 'APROVADO' | 'RECUSADO' | 'NAO_TRATADO'`). Cada passo é um componente que recebe `onContinuar`, `onPular`, `onVoltar` e callbacks `onTratou…` para alimentar o contador. Os passos buscam os próprios dados ao montar, então **Voltar** sempre mostra o estado atual do servidor.

```
RevisaoPage
  +-- PassoSemana        (consultarHistoricoSemana)
  +-- PassoPendencias    (listarTarefas status=ATRASADA / bloqueada=true,
  |                       atualizarTarefa, excluirEsperaTarefa)
  +-- PassoDecidir       (listarDecidir, PromoverDecidirForm, excluirDecidir)
  +-- PassoPlano         (gerarPlano, buscarPlano,
  |                       aceitarProposta, descartarProposta)
  +-- RevisaoConclusao
```

- *Alternativa*: um passo por sub-rota (`/revisao/1`…). Descartada: o histórico do navegador passaria a ser o controle do wizard, o que complica pular e voltar sem ganho real; uma rota só basta para o critério da issue.

### 3. Extrair `PromoverDecidirForm` do `DecidirPage`

Mover os campos e o submit de promoção para `src/decidir/PromoverDecidirForm.tsx` (props: item, `onPromovido`, `onCancelar`) e usá-lo tanto no `DecidirPage` quanto no `PassoDecidir`. O comportamento do `/decidir` não muda; os testes atuais do `DecidirPage` são a rede de segurança da extração.

- *Alternativa*: no passo 3, promover só com o título e mandar para `/decidir` para completar. Descartada: a spec pede o fluxo de promoção existente e sair da revisão quebra o ritual.

### 4. Ações do passo 2 usando endpoints existentes

| Ação | Chamada |
|---|---|
| Reagendar | `PATCH /tarefas/{id}` com `prazo` (data escolhida, fim do dia em BRT) |
| Descartar | `PATCH /tarefas/{id}` com `status: CANCELADA`, após `useConfirmDialog` |
| Remover espera | `DELETE /tarefas/{id}/esperas/{esperaId}`, só para esperas cuja origem é a própria tarefa |

Não há ação "tirar prazo": o `PATCH` de tarefa trata `prazo: null` como "não alterar" (só `projetoId` e `tagIds` têm marcador de presença), e abrir isso fugiria do escopo da issue. Reagendar envia só `prazo`; o backend já volta `ATRASADA` → `PENDENTE` quando o prazo vai para o futuro.

O descarte usa `CANCELADA`, e não o soft delete, para manter a tarefa no histórico e fora do planner. Bloqueio herdado (pai/projeto) só mostra de onde vem, com link para a ficha.

### 5. Passo 4: próxima segunda e desfecho

`proximaSegunda(hojeIso)` = `adicionarDiasIso(inicioSemana(hoje), 7)`. Se hoje for domingo, isso é amanhã (cenário da spec). Fluxo:

```
[Gerar plano] --> gerarPlano(proximaSegunda)
     |                 |
     |   409 --> buscarPlano(segunda): APROVADO? --> aviso + link /plano
     |                                    senao   --> mensagem do servidor
     v
buscarPlano dos 14 dias em paralelo --> filtra por propostaId --> lista dias/blocos/conflitos
     |
     +-- [Aprovar plano]      --> aceitarProposta(propostaId)   --> APROVADO
     +-- [Nao aprovar agora]  --> descartarProposta(propostaId) --> RECUSADO
     +-- [Pular]              ------------------------------------> NAO_TRATADO
```

Os dias vêm de 14 `buscarPlano` em paralelo, filtrados pelo `propostaId`, em vez de `listarDatasDaProposta`: aquele helper já busca cada dia só para descobrir as datas, e o passo precisaria buscar de novo para mostrar os blocos. O 409 de `gerar` é ambíguo (plano já aprovado ou conflito de versão), por isso o passo confirma o status da segunda antes de mostrar o aviso.

Botões ficam desabilitados com `aria-busy` durante a requisição (cenário de clique duplo). Proposta já resolvida responde erro de proposta inválida: mostra a mensagem e volta ao estado "Gerar plano".

- *Alternativa*: reaproveitar a `HomePage` inteira dentro do passo. Descartada: a `HomePage` é focada em um dia, com execução de blocos; o passo precisa só de uma visão resumida por dia. Os componentes visuais pequenos (`ConflitoBanner`, rótulos de bloco) podem ser reaproveitados onde couber.

### 6. Navegação

Novo item **Revisão da semana** em `ITENS` de `MenuMais.tsx` e um link discreto em `HomePage.tsx`, perto do cabeçalho do plano. Rota registrada em `App.tsx` dentro do bloco autenticado e título em `documentTitle.ts`.

## Risks / Trade-offs

- [Filtro `bloqueada` carrega todas as tarefas do filtro base na memória] → Uso pessoal com poucas centenas de tarefas; o filtro base já restringe por usuário e não deletadas. Se crescer, migrar para a alternativa SQL sem mudar a API.
- [Rascunhos antigos para os mesmos dias ficam órfãos quando a revisão gera de novo] → É o comportamento atual de `/plano`; o descarte explícito no passo 4 limpa a proposta recusada. Não piora.
- [Revisão longa vira burocracia] → Todos os passos têm Pular; o passo 2 mostra só as listas, sem formulário aberto por padrão.
- [Recarregar a página perde o progresso do wizard] → Aceito: as ações já foram salvas no servidor, e recomeçar do passo 1 custa segundos.

## Migration Plan

Sem migração de banco. O filtro novo é opcional e retrocompatível. Deploy normal de backend + frontend; rollback = reverter o PR.
