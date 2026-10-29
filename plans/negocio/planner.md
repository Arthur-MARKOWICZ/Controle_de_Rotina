# Feature — Planner + aprovação (quarto slice)

Escopo: motor determinístico do **dia D** — score, blocos, conflitos, persistência versionada, job diário, ajuste (add/remove) e aprovação do rascunho.  
Fora deste slice: distribuição multi-dia, replanejamento pós-execução, XP, histórico, IA, notificações, calendário completo.

Dependências: `tarefa.md`, `capacidade.md`.

---

## Conceito

1. Para a data `D`, o motor lê tarefas elegíveis + capacidade **resolvida** do dia, converte horas → minutos **só no motor**, ranqueia por score e monta blocos até esgotar a capacidade.
2. Cada geração cria uma **nova versão** do plano do dia (`RASCUNHO`), sem mutar versões anteriores.
3. Capacidade `VAZIO` ou `0` → versão com **zero blocos** + mensagem de que não há tempo no dia (não é erro 404).
4. O usuário **ajusta** o rascunho (remover/adicionar tarefa) e **aprova**. Home pós-login mostra o rascunho corrente pedindo aprovação (ou o aprovado, se não houver rascunho mais novo — ver regras abaixo).

---

## Entities

### Plano

| Campo       | Tipo                 | Observação                                                |
|-------------|----------------------|-----------------------------------------------------------|
| id          | UUID                 | PK                                                        |
| usuarioId   | UUID                 | FK; dono (JWT)                                            |
| data        | LocalDate            | dia D                                                     |
| versao      | int                  | começa em 1; incrementa a cada geração                    |
| status      | enum `StatusPlano`   | `RASCUNHO`, `APROVADO`                                    |
| temConflito | boolean              | true se houve corte por capacidade ou ajuste estourou     |
| mensagem    | String (nullable)    | ex.: sem tempo no dia; detalhe de conflito                |
| geradoEm    | Instant              |                                                           |
| createdAt   | Instant              |                                                           |
| updatedAt   | Instant              |                                                           |

**Corrente do dia:** maior `versao` para `(usuarioId, data)`.

**Unicidade:** `(usuarioId, data, versao)` único.

### BlocoPlano

| Campo           | Tipo     | Observação                                      |
|-----------------|----------|-------------------------------------------------|
| id              | UUID     | PK                                              |
| planoId         | UUID     | FK                                              |
| tarefaId        | UUID     | FK; tarefa do usuário                           |
| duracaoMinutos  | int      | `>= 1`; no add manual = duração total da tarefa |
| ordem           | int      | 1..n na sequência do dia                        |
| createdAt       | Instant  |                                                 |
| updatedAt       | Instant  |                                                 |

Sem soft delete de bloco: remoção no ajuste apaga o bloco (ou remove da coleção do plano rascunho).

---

## Regras do motor (dia D)

### Entradas

- Capacidade resolvida de `D` (`SEMANAL` | `OVERRIDE` | `VAZIO`) → `capacidadeMinutos = horas * 60` (`VAZIO`/`0` → 0).
- Tarefas elegíveis do usuário: `deletedAt == null` e status **≠** `FINALIZADA` e **≠** `CANCELADA`  
  (entra: `PENDENTE`, `EM_PROGRESSO`, `ATRASADA`).

### Conversão (somente no motor)

- `duracaoMinutosTarefa = duracaoHoras * 60`
- Capacidade do dia em minutos como acima.  
  Tarefa/capacidade nos outros slices **permanecem em horas int**.

### Score V1

```text
score = prioridade + urgenciaPrazo + bonusAtrasada + fatorDuracao
```

Maior score entra primeiro. Empate: prazo mais cedo; depois `createdAt` mais antigo.

**Prioridade**

| Prioridade | Pontos |
|------------|--------|
| `ALTA`     | 100    |
| `MEDIA`    | 50     |
| `BAIXA`    | 20     |

**Urgência do prazo** (relativo ao início de `D` em `America/Sao_Paulo`)

| Situação                         | Pontos |
|----------------------------------|--------|
| Prazo antes do início de D       | 70     |
| Vence durante D                  | 60     |
| Vence em D+1                     | 40     |
| Vence em D+2 … D+3               | 25     |
| Vence em D+4 … D+7               | 10     |
| Depois de D+7                    | 0      |

**Bônus atrasada:** `ATRASADA` → +40; demais → 0.

**Duração** (preferência leve por tarefas mais curtas)

| Duração (min) | Pontos |
|---------------|--------|
| ≤ 60          | 15     |
| 61–120        | 10     |
| 121–240       | 5      |
| > 240         | 0      |

### Alocação e conflito

1. Ordenar elegíveis por score (e desempates).
2. Enquanto houver capacidade restante e tarefas na fila:
   - Se `duracaoMinutosTarefa <= restante` → bloco com duração total; consome minutos.
   - Se `duracaoMinutosTarefa > restante` e `restante > 0` → bloco com `restante` minutos; `temConflito = true`; mensagem indicando tarefa parcialmente alocada / não coube inteira; capacidade zera.
   - Se `restante == 0` → para; tarefas não alocadas ficam de fora desta versão (conflito já marcado se houve corte).
3. Capacidade 0 desde o início → plano vazio, `temConflito = false`, mensagem: sem tempo neste dia / nada planejado.

Uma mesma tarefa aparece **no máximo uma vez** por versão (um bloco).

---

## Versionamento e job

### Geração (job ou endpoint)

- Cria **nova** linha `Plano` com `versao = max(versao em data) + 1` (ou 1 se primeira), status `RASCUNHO`, blocos novos.
- **Não** altera versões anteriores.

### Job 06:00 America/Sao_Paulo

- Para cada usuário, **em sequência** (um após o outro):
  - Se já existe algum plano com `data = hoje` e `status = APROVADO` → **pular** esse usuário.
  - Senão → gerar nova versão `RASCUNHO` para hoje (mesmo se já existir rascunho anterior: sobe a versão).

### Home / “plano corrente”

1. Se existe versão corrente (`max versao`) em `RASCUNHO` → mostrar essa (pede aprovação).
2. Senão, se existe `APROVADO` (a de maior versão entre aprovados, ou a corrente se aprovada) → mostrar aprovado.
3. Senão → ainda não há plano (ou só após job/endpoint).

Na prática MVP: **sempre exibir a maior `versao` do dia**; se `RASCUNHO`, CTA aprovar/ajustar; se `APROVADO`, só consulta (ajuste fechado até nova geração — e geração automática só ocorre se não houver aprovado).

---

## Ajuste e aprovação

Só na **versão corrente** com `status = RASCUNHO`.

### Remover

- Remove o `BlocoPlano` da tarefa indicada.
- Recalcular `temConflito` se fizer sentido no MVP: se após remoção a soma dos blocos ≤ capacidade, pode limpar conflito “por estouro”; conflito por geração parcial pode permanecer se ainda houver bloco cortado — MVP aceitável: recalcular `temConflito = somaBlocos > capacidadeMinutos` **ou** manter flag se qualquer bloco foi criado truncado (campo opcional `truncado` no bloco — se não quiser complexidade, `temConflito` só por `soma > capacidade` no ajuste e pela geração quando truncar).

Decisão fechada MVP:  
- Geração: `temConflito = true` se alguma tarefa foi alocada com duração &lt; duração total.  
- Ajuste add: se `soma(duracaoMinutos) > capacidadeMinutos` → `temConflito = true` + mensagem; **mesmo assim persiste** o bloco.  
- Ajuste remove: se soma voltar ≤ capacidade e não houver bloco truncado na versão, `temConflito = false`.

### Adicionar

- Body: `tarefaId` (tarefa elegível do usuário, ainda sem bloco neste plano).
- `duracaoMinutos` do bloco = **duração total** da tarefa (`duracaoHoras * 60`).
- Se soma ultrapassar capacidade → persiste + `temConflito` + feedback ao usuário.
- Tarefa inexistente / outro usuário / não elegível / já no plano → 404 ou 409 conforme o caso.

### Aprovar

- `RASCUNHO` corrente → `APROVADO`.
- Já aprovado / não corrente / outro status → erro claro.

---

## Back-end

Todos autenticados (exceto o job interno). Escopo JWT; outro usuário / id inexistente → **404**.

1. **POST /planos/gerar**  
   - Body/query: `data` (dia D).  
   - Se já existe `APROVADO` para `(usuario, data)` → **409** (alinhado ao job: não gera por cima).  
   - Senão → nova versão `RASCUNHO` + blocos; 201 + plano.

2. **GET /planos**  
   - Query: `data` (obrigatória).  
   - Default: **versão corrente** (maior `versao`).  
   - Opcional: `versao` para histórico.  
   - Inclui blocos ordenados + dados mínimos da tarefa (id, título, etc.).

3. **GET /planos/{id}**  
   - Plano do usuário com blocos.

4. **POST /planos/{id}/blocos**  
   - Só `RASCUNHO` corrente (senão 409).  
   - Body: `tarefaId`.  
   - Add com regras acima.

5. **DELETE /planos/{id}/blocos/{blocoId}**  
   - Só `RASCUNHO` corrente.

6. **POST /planos/{id}/aprovar**  
   - Só `RASCUNHO` corrente → `APROVADO`.

### Job

Scheduler **06:00** `America/Sao_Paulo`: regra “só gera se não houver `APROVADO` para hoje”; um usuário por vez.

---

## Front-end

1. **Home pós-login** — plano do **dia corrente** (versão corrente): lista de cards/blocos, capacidade usada vs. disponível, banner de conflito/mensagem (ex. dia sem tempo), CTA **Aprovar** se `RASCUNHO`.
2. **Ajustar plano** — na mesma experiência ou rota filha: remover bloco; adicionar tarefa (picker de elegíveis que ainda não estão no plano); feedback visual de conflito.
3. **Detalhe** — click no card → rota de detalhe da **tarefa** já existente (`tarefa.md`), não CRUD novo.

Rotas autenticadas; reutilizar sessão do slice usuário.

---

## Decisões fechadas (revisão)

1. Slice inclui **geração + ajuste + aprovação** (não há `aprovacao-plano.md` separado no MVP).
2. Horizonte: **apenas o dia D**.
3. Saída em **blocos**; tarefa grande no dia curto → bloco parcial + `temConflito`.
4. Capacidade 0/vazia → plano vazio + mensagem (não é falha de “sem plano”).
5. Persistência **opção B**: versões imutáveis na geração; corrente = maior `versao`.
6. Job 06:00 BRT: sequencial; **só gera se não houver `APROVADO`** naquele dia.
7. Endpoint manual de gerar: mesma restrição (409 se já aprovado).
8. Ajuste: só remove/adiciona tarefa; duração do add = tarefa inteira; estouro → conflito visível e persistido.
9. Unidades canônicas nos outros slices = horas; **minutos só no motor/blocos**.
10. Score V1 numérico conforme tabelas deste doc; duração curta ganha mais pontos.
11. Elegíveis: não deletadas; exclui `FINALIZADA` e `CANCELADA`; `ATRASADA` com +40.
12. Home: versão corrente; rascunho pede aprovação.
13. Fora: multi-dia, XP, execução de timer, IA.
14. Este é o **quarto slice**.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Score V1: prioridade, urgência por faixas de prazo, bônus `ATRASADA`, fator duração; desempate prazo/`createdAt` | B2 | Núcleo do ranking; regressão muda quem entra no dia |
| T2 | Elegibilidade: exclui soft-delete, `FINALIZADA`, `CANCELADA`; inclui `PENDENTE` / `EM_PROGRESSO` / `ATRASADA` | B2 | Tarefa errada no plano quebra a confiança no motor |
| T3 | Alocação: cabe inteira; parcial + `temConflito`; capacidade 0/vazia → zero blocos + mensagem | B2 | Conflito e dia sem tempo são requisitos explícitos |
| T4 | Conversão h→min só no motor (`duracaoHoras * 60`, capacidade idem) | B2 | Evita divergência com slices em horas |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | Persistência: criar plano+blocos, unicidade `(usuario, data, versao)`, corrente = max versão | B1/B2 | Base da opção B |
| T6 | `GET /planos?data=`: versão corrente, blocos ordenados; sem token → 401 | B3 | Contrato da home |
| T7 | `GET /planos/{id}`: dono ok; outro usuário / id inexistente → 404 | B4 | Escopo JWT |
| T8 | `POST /planos/gerar`: 201 + `RASCUNHO`; segunda geração sobe versão; se já `APROVADO` → 409 | B5 | Geração e trava pós-aprovação |
| T9 | `POST .../blocos`: add com duração total; estouro → persiste + `temConflito`; tarefa já no plano / inelegível → 409/404 | B6 | Ajuste com conflito |
| T10 | `DELETE .../blocos/{blocoId}`: só `RASCUNHO` corrente; aprovado → 409 | B7 | Não mutar aprovado |
| T11 | `POST .../aprovar`: rascunho corrente → `APROVADO`; repetir / não corrente → erro claro | B8 | Porta de entrada da execução |

### Job / scheduler

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T12 | Job: gera rascunho quando **não** há `APROVADO` no dia; **pula** usuário com dia aprovado | B9 | Evita sobrescrita silenciosa da aprovação |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T13 | Home mostra mensagem de dia sem tempo / banner de conflito quando a API indicar | F1 | Comportamento visível do requisito de capacidade vazia/conflito |
| T14 | CTA aprovar só em `RASCUNHO`; após sucesso UI reflete `APROVADO` | F2 | Evita aprovar estado errado |

Se ainda não houver harness de teste front no repo: T13–T14 ficam como checklist manual até existir; **T1–T12 são obrigatórios** no back.

---

## Atividades

Regra: **no máximo um endpoint novo por atividade** de back. Motor e persistência base sem HTTP nas primeiras. Entregar os T* da seção acima na atividade indicada.

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Entities + Flyway** | `Plano`, `BlocoPlano`, enum `StatusPlano`, migration SQL, repositories; **sem** endpoints; base de T5 |
| **B2** | **Motor** | Score V1, elegíveis, conversão h→min, alocação, conflito, capacidade vazia; **T1–T5** (persistência via service/IT sem HTTP de API) |
| **B3** | **GET /planos** | Query `data` (+ `versao` opcional); versão corrente; blocos + dados mínimos da tarefa; auth/escopo JWT; **T6** |
| **B4** | **GET /planos/{id}** | Detalhe por id com blocos; 404 se outro usuário / inexistente; **T7** |
| **B5** | **POST /planos/gerar** | Body/query `data`; usa o motor; 409 se já houver `APROVADO`; 201 + plano; **T8** |
| **B6** | **POST /planos/{id}/blocos** | Add tarefa no rascunho corrente; duração total; conflito se estourar; **T9** |
| **B7** | **DELETE /planos/{id}/blocos/{blocoId}** | Remove bloco só em `RASCUNHO` corrente; **T10** |
| **B8** | **POST /planos/{id}/aprovar** | `RASCUNHO` corrente → `APROVADO`; **T11** |
| **B9** | **Job 06:00** | Scheduler `America/Sao_Paulo`; skip se aprovado; um usuário após o outro; **sem** endpoint novo; **T12** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Home (leitura)** | Dia corrente via `GET /planos`; cards, capacidade, mensagem/conflito, estado rascunho vs aprovado; **T13** |
| **F2** | **Aprovar** | CTA que chama `POST .../aprovar`; **T14** |
| **F3** | **Remover bloco** | Ação na UI → `DELETE .../blocos/{blocoId}` |
| **F4** | **Adicionar tarefa** | Picker + `POST .../blocos`; feedback de conflito |
| **F5** | **Detalhe da tarefa** | Click no card → rota de detalhe já existente (`tarefa.md`) |

### Ordem

```text
B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8 → B9
              ↘
               F1 → F2 → F3 → F4 → F5
```

`F1` pode começar após **B3** (B4 opcional para deep-link). `F2`–`F4` após o endpoint correspondente (`B8`, `B7`, `B6`).
