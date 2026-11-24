# Feature — Capacidade inicial e plano vazio não aprovável

Origem: auditoria Impeccable 2026-11-24 (P0 "primeiro plano nasce vazio"), `.impeccable/audit/2026-11-24T14-03-45Z__project.md`.

Escopo: (1) conta nova nasce com capacidade semanal padrão seg–sex 4h; (2) `POST /planos/{id}/aprovar` recusa plano sem blocos com `409 Plano vazio`.  
Fora deste slice: backfill de contas existentes (cobertas pela guarda de UI em `ui-primeiro-plano-capacidade.md`); mudar o motor de alocação; mudar `POST /planos/propostas/{propostaId}/aceitar` chamado direto; telas (ver plano de UI).  
Dependências: `usuario.md`, `capacidade.md`, `planner.md`, `planner-janela-multidia.md`, `api-problem-detail.md`.

---

## Conceito

Hoje `capacidade.md` (regra 3) diz: dia sem registro no template = dia vazio. Um usuário recém-cadastrado não tem nenhum registro, então toda geração devolve 0 blocos, conflito vermelho e um botão "Aprovar 0 blocos" que funciona. O motor está correto; o problema é o estado inicial e a aprovação de um plano que não serve para nada.

1. **Padrão no cadastro:** no mesmo `@Transactional` de `UsuarioService.cadastrar`, criar 5 `CapacidadeSemanal` ativas (`SEGUNDA`…`SEXTA`, `horas = 4`). Sábado e domingo **sem registro**, o que mantém a regra 3 de `capacidade.md` ("dia sem registro = vazio"). É o mesmo valor do preset "Dias úteis 4h" da tela de Capacidade.
2. A regra 3 de `capacidade.md` **continua valendo**. O que muda é só o ponto de partida de contas novas; o usuário edita ou exclui livremente depois.
3. **Efeito colateral esperado:** `PlannerGeracaoDiariaJob` (6h BRT) já roda para todos os usuários. Contas novas passam a receber, toda manhã, um rascunho com blocos em vez de um rascunho vazio. É o mesmo comportamento de quem já configurou capacidade; o job não muda.
4. **Plano vazio não é aprovável:** aprovar um rascunho com `blocos.size() == 0` → `409`, title `Plano vazio`. A checagem vale também quando `/aprovar` delega para `propostaService.aceitar` (rascunho de proposta): o que conta são os blocos **do plano informado**.

---

## Entity / Entities

Sem entity nova e sem migration. Usa `CapacidadeSemanal` (`capacidade.md`) e `Plano`/`Bloco` (`planner.md`).

---

## Back-end

### Cadastro (`POST /usuarios`)

- `UsuarioService.cadastrar`: depois de `saveAndFlush(usuario)`, chamar um método novo `CapacidadeSemanalService.criarPadraoInicial(usuarioId)`.
- `criarPadraoInicial`:
  - grava diretamente as 5 linhas via repository;
  - **não** reaproveita `criar(...)`, porque esse método dispara `propostaGeracaoService.gerarSeDiaSemanaAprovado`, desnecessário para um usuário sem planos;
  - é idempotente: se já existir capacidade ativa para o dia, pula aquele dia (não lança `CapacidadeSemanalConflitoException`).
- A resposta de `POST /usuarios` não muda (`201`, `UsuarioResponse`). O contrato OpenAPI não muda.

### Aprovar (`POST /planos/{id}/aprovar`)

- `PlanoAprovacaoService.aprovar`: depois de `garantirRascunhoCorrente(plano)` e **antes** do desvio para proposta, se o plano não tem blocos → `PlanoVazioException`.
- `PlanoVazioException extends ErrorResponseException`: `409`, title `Plano vazio`, detail `Não há blocos para aprovar. Ajuste a capacidade ou adicione tarefas e gere uma nova versão.`
- Estado intacto: o plano continua `RASCUNHO` e a versão não muda.
- Ordem das checagens: `404` (não é do usuário) → `409 Plano não aprovável` (não é rascunho corrente) → `409 Plano vazio`.

### MCP

- A tool de aprovar já repassa o ProblemDetail. Só confirmar que o title `Plano vazio` chega ao agente (teste do cliente com 409).

---

## Decisões fechadas (revisão)

1. Padrão de contas novas: seg–sex 4h, fim de semana sem registro (o mesmo preset da UI).
2. Sem backfill: contas antigas com 0h são tratadas pela guarda de UI (`ui-primeiro-plano-capacidade.md`).
3. Aprovar plano com 0 blocos → `409 Plano vazio`, também quando o plano é rascunho de proposta.
4. `/planos/propostas/{id}/aceitar` chamado direto fica como está neste slice.
5. Sem flag nova no usuário e sem migration.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Regra 1 + 3 + 3 do `AGENTS.md`, por unidade.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `criarPadraoInicial` cria 5 linhas (seg–sex, 4h, ativas) e nenhuma para sáb/dom | B1 | Caminho feliz da raiz do P0 |
| T2 | `criarPadraoInicial` chamado duas vezes não duplica nem lança | B1 | Idempotência (anomalia: chamada duplicada) |
| T3 | `criarPadraoInicial` com SEGUNDA já ativa cria só as 4 que faltam e não altera a existente | B1 | Anomalia: estado prévio preservado |
| T4 | `aprovar` com 0 blocos lança `PlanoVazioException`; status continua `RASCUNHO` | B2 | Regra nova |
| T5 | `aprovar` de rascunho de proposta com 0 blocos lança `PlanoVazioException` **sem** chamar `propostaService.aceitar` | B2 | Desvio de proposta não pode pular a regra |
| T6 | `aprovar` de plano não-rascunho com 0 blocos lança `PlanoNaoAprovavelException` (não `PlanoVazio`) | B2 | Ordem das checagens |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T7 | `POST /usuarios` → `GET /capacidades/semanal` do novo usuário lista seg–sex 4h | B1 | Contrato ponta a ponta |
| T8 | `POST /usuarios` + `POST /planos/gerar` (data numa terça, `Clock` fixo) com uma tarefa de 60 min → plano com ≥ 1 bloco | B1 | Prova que o P0 sumiu |
| T9 | `POST /usuarios` com e-mail duplicado → `409` e **nenhuma** capacidade extra criada | B1 | Anomalia: rollback da transação |
| T10 | `POST /planos/{id}/aprovar` com 0 blocos → `409`, `$.title = "Plano vazio"`; `GET /planos/{id}` segue `RASCUNHO` | B2 | Erro esperado + estado intacto |
| T11 | Aprovar plano vazio de outro usuário → `404` (não vaza `Plano vazio`) | B2 | Escopo do usuário |
| T12 | Aprovar plano com blocos segue `200` / `APROVADO` (regressão) | B2 | Caminho feliz não quebrou |

### MCP

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T13 | Tool de aprovar recebe 409 `Plano vazio` e devolve o title/detail ao agente | B3 | O agente não pode reportar sucesso |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Capacidade padrão no cadastro** | `CapacidadeSemanalService.criarPadraoInicial` + chamada em `UsuarioService.cadastrar`; ajustar ITs que cadastram via `POST /usuarios` e depois fazem `POST /capacidades/semanal` em dia útil (hoje dariam `409 CapacidadeSemanalConflito`: `CapacidadeSemanalIT`, `CapacidadeOverrideIT`, `ProjetoIT`, `ApiProblemDetailIT`, conferir os demais), trocando para `PATCH` ou para dia de fim de semana; atualizar a regra 3 de `capacidade.md` com a nota "contas novas nascem com seg–sex 4h". Inclui T1–T3, T7–T9. |
| **B2** | **409 Plano vazio no aprovar** | `PlanoVazioException` + checagem em `PlanoAprovacaoService.aprovar` antes do desvio de proposta. Contrato OpenAPI inalterado (só um novo title de ProblemDetail). Inclui T4–T6, T10–T12. |
| **B3** | **MCP: erro de plano vazio** | Teste do cliente MCP para 409 `Plano vazio`; sem código novo se o repasse genérico já cobrir. Inclui T13. |

### Ordem

```text
B1 ─┐
B2 ─┴→ B3
      ↘ ui-primeiro-plano-capacidade (F1 depende de B2; F2 pode começar já)
```

B1 e B2 são independentes.
