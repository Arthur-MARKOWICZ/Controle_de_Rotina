# Feature — Contrato único de erro (ProblemDetail)

Issue: [#14](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/14)

Escopo: unificar respostas de erro da API em ProblemDetail (`title`, `detail`, `status`); remover `ResponseStatusException` dos services/controllers citados; validação declarativa em capacidade; 401 alinhado; uniques conhecidas → 409.  
Fora deste slice: detecção de reuso de refresh (#16), lock/versão de XP (#17), mudança de regras do planner.

Dependências: slices MVP de domínio já feitos (usuario, tarefa, capacidade, planner, execução, xp).

---

## Conceito

1. Todo erro HTTP previsível da API usa o mesmo envelope ProblemDetail (RFC 7807 / Spring).
2. Regras de negócio continuam em exceções de domínio (`ErrorResponseException` / hierarquia já existente); services não lançam `ResponseStatusException`.
3. Validação de body (`@Valid`) e violações de constraint entram no mesmo formato, com campos inválidos listados.
4. Uniques conhecidas do banco viram 409 com a exceção de domínio correspondente — não 500 genérico.

---

## Back-end

### Formato

- `type` (opcional/URI padrão Spring), `title`, `detail`, `status`.
- Validação: incluir lista de campos (ex. `errors` / `properties` no ProblemDetail) com propriedade + mensagem.
- 401 de autenticação: mesmo envelope (não JSON artesanal `{"detail":…}`).

### Trocas obrigatórias

1. **Services** `TarefaService`, `CapacidadeSemanalService`, `CapacidadeOverrideService`, `CapacidadeResolvidaService`: substituir `ResponseStatusException` por exceções de domínio ou validação declarativa.
2. **Controllers** de capacidade semanal e override: body tipado + `@Valid`; sem `JsonNode` / `ObjectMapper` no controller. Se create aceitar item único ou lista, um tipo request compartilhado (wrapper ou lista validada).
3. **Handler global**: `MethodArgumentNotValidException`, `ConstraintViolationException` → ProblemDetail; `DataIntegrityViolationException` das uniques conhecidas (`uk_usuario_email`, `uk_plano_usuario_data_versao`, `uk_execucao_bloco_plano`, `uk_xp_evento_tipo_origem`, uniques de capacidade) → 409 com título/detail de domínio.
4. **SecurityConfig** (entry point 401, e access denied se existir): escrever ProblemDetail.
5. **TarefaController** (ordenação inválida etc.): sem `ResponseStatusException`.

### Front-end

- Ajustar leitura de erro (se o client assume só `detail` solto) para `title`/`detail` do ProblemDetail — mínimo para não quebrar toasts existentes (`ui-estados-feedback`).
- Sem prefixo novo de API.

---

## Decisões fechadas (revisão)

1. Contrato único: ProblemDetail com `title`, `detail`, `status`.
2. Zero `ResponseStatusException` restante em service ou controller.
3. Capacidade: `@Valid` + DTO; sem parse manual de JSON no controller.
4. Uniques conhecidas → 409 (não 500).
5. 401 autenticado no mesmo formato.
6. Issue #14; não misturar com #17 (retry/lock) além do mapeamento 409 das uniques.

---

## Testes automatizados

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Body inválido (`@Valid`) → ProblemDetail com status 400 e campos | B3 | Contrato de validação |
| T2 | Recurso não encontrado (exceção de domínio) → ProblemDetail 404 | B1 | Paridade com domínio atual |
| T3 | Unique conhecida (e-mail / execução / versão de plano) → 409 ProblemDetail, não 500 | B4 | Aceite da issue |
| T4 | Sem token em rota autenticada → 401 ProblemDetail (`title`/`detail`/`status`) | B5 | Aceite 401 |
| T5 | Capacidade semanal/override com body tipado válido e inválido (sem JsonNode) | B2 | Controllers limpos |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T6 | Toast/erro de API ainda exibe mensagem útil a partir de ProblemDetail | F1 | Não regressar UX |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Exceções de domínio faltantes** | Substituir usos de `ResponseStatusException` nos services citados por exceções alinhadas ao padrão do repo; **T2** |
| **B2** | **DTOs + controllers capacidade** | Requests tipados `@Valid` compartilhados; remover JsonNode; **T5** |
| **B3** | **Handler validação** | `MethodArgumentNotValid` / `ConstraintViolation` → ProblemDetail; **T1** |
| **B4** | **Uniques → 409** | Traduzir `DataIntegrityViolationException` das constraints conhecidas; **T3** |
| **B5** | **401 Security + limpeza controllers** | Entry point ProblemDetail; zerar `ResponseStatusException` restantes; **T4** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Client de erro** | Ler ProblemDetail nos toasts/estados; **T6** |

### Ordem

```text
B1 → B2 → B3 → B4 → B5 → F1
```
