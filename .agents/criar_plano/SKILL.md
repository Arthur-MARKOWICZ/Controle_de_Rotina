---
name: criar_plano
desc: Cria ou reescreve planos de feature (slices) em plans/*.md no formato do projeto. Use ao pedir novo plano, escrever/reescrever plans/X.md, fatiar atividades B*/F*, ou alinhar um plano ao padrão de usuario/tarefa/capacidade/planner.
---

# Criar plano de feature (slice)

Não implemente código. O entregável é um (ou mais) arquivo(s) Markdown em `plans/` e, se necessário, atualização de `ordem_execucao/docs.md`.

Antes de escrever o plano final: leia planos irmãos em `plans/`, a visão (`assistente_planejamento_rotina_gamificado.md`), `Decisoes_arqutetura.md` e a fila em `ordem_execucao/docs.md`. Se o pedido for rascunho cru ou ideia solta, use o espírito de `.agents/revisao_plano` — critique e pergunte **antes** de fechar o Markdown completo.

---

## Quando usar

- “Cria o plano de X”
- “Escreve / reescreve `plans/….md`”
- “Parte as atividades do plano”
- “Próximo slice da fila”

Não usar para implementar o slice (aí é `seguir-plano` / TDD / java) nem só para criticar sem gerar arquivo (`revisao_plano`).

---

## Tamanho do slice

Um plano = **um domínio / um slice vertical**, não o produto inteiro.

- Escopo explícito: o que **entra** e o que fica **fora**.
- Dependências: cite planos anteriores (`Dependências: tarefa.md, …`).
- Se misturar dois núcleos distintos (ex.: cadastro de tarefa + motor do planner + XP), **quebre** em arquivos separados.
- Número de atividades pode ser alto (B1…B9) se cada uma for fina; o problema é escopo de produto largo, não quantidade de B*.

---

## Estrutura obrigatória do arquivo

Salvar em `plans/<nome>.md`. Espelhar o nível de detalhe de `plans/usuario.md`, `plans/tarefa.md`, `plans/capacidade.md`, `plans/planner.md`.

```markdown
# Feature — <Nome> (<N>º slice)

Escopo: …
Fora deste slice: …
Dependências: …   # se houver

---

## Conceito          # opcional; útil se houver regras de domínio não óbvias

---

## Entity / Entities

| Campo | Tipo | Observação |
|-------|------|------------|

# unicidade, soft delete, enums, invariantes

---

## Back-end

# endpoints com método, path, body/query, validações, status HTTP relevantes
# jobs/schedulers se houver
# auth: JWT / escopo do usuário autenticado (padrão do projeto)

---

## Front-end

# telas/fluxos mínimos; reutilizar rotas/sessão já existentes quando der

---

## Decisões fechadas (revisão)

1. …
# só o que já foi decidido (não lista de dúvidas)

---

## Testes automatizados (obrigatório)

# O que **não pode faltar** para o slice ser considerado feito.
# Ver seção "Testes automatizados" desta skill.

---

## Atividades

### Backend
| # | Atividade | Escopo |
|---|-----------|--------|

### Frontend
| # | Atividade | Escopo |
|---|-----------|--------|

### Ordem
# diagrama B* → F*
```

Omitir seções só se realmente não existirem (ex.: slice só back). **Nunca omitir** `## Testes automatizados`. Não inventar “Decisões fechadas” com itens ainda em aberto — nesse caso pare e pergunte.

---

## Testes automatizados (regra dura)

Todo plano **deve** ter a seção `## Testes automatizados` listando os testes que **não podem faltar** para o slice fechar. Não é inventário de todo teste possível: é o **mínimo obrigatório** (regressão das regras que, se quebrarem, o slice falha).

### Formato sugerido

```markdown
## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | … | B2 | … |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T2 | … | B5 | … |

### Job / scheduler (se houver)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T3 | … | B9 | … |

### Front (se o slice tiver UI com regra)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | … | F1 | … |

# Se o projeto ainda não tiver harness de teste front, declare isso e liste só o comportamento crítico a cobrir quando houver (ou marque “manual até existir harness”).
```

### O que sempre considerar (quando existir no slice)

- **Regras de domínio / motor:** casos felizes + extremos já citados no plano (capacidade 0, conflito, empate de score, status elegível/inelegível, etc.).
- **Auth e escopo:** recurso de outro usuário → 404 (ou padrão do repo); endpoint autenticado sem token → 401.
- **Contratos HTTP:** status combinados (`201`, `409`, validações de body).
- **Persistência:** unicidade, soft delete/restore, versionamento — se forem regra do slice.
- **Job:** dispara a regra certa e **respeita skip** (ex.: não sobrescrever aprovado).
- **Não listar:** testes triviais de getter/setter, smoke vazio “contextLoads” como único item, nem duplicar o mesmo cenário em unitário e IT sem ganho.

### Ligação com atividades

- Na coluna **Atividade**, use o B*/F* que **introduz** o comportamento.
- No escopo da atividade B*, pode citar “inclui T*” em uma frase curta; a lista canônica fica na seção de testes (evita esquecer o mínimo ao fatiar endpoints).

### Anti-padrões de testes no plano

- Seção ausente ou só “escrever testes”.
- Lista genérica (“testar o CRUD”) sem cenários.
- Só happy path quando o plano define conflito, 409, capacidade vazia, etc.

---

## Atividades: granularidade (regra dura)

Objetivo: cada atividade caber em **uma sessão** de implementação, com diff revisável.

### Backend

1. **B1** (quando houver persistência nova): **somente** entities/enums + migration Flyway + repositories. **Sem** endpoints e sem motor de negócio completo.
2. Se houver algoritmo/serviço puro (score, cálculo, resolução): atividade **própria** (ex. **B2 Motor**), preferencialmente testável **sem** HTTP.
3. **No máximo um endpoint novo por atividade.**  
   - `GET` lista e `GET` por id = atividades separadas se ambos forem novos.  
   - `POST` / `PATCH` / `DELETE` / ações (`/aprovar`, `/restore`, …) = uma cada.
4. Job/scheduler = atividade própria, **sem** endpoint novo (a menos que o slice exponha trigger HTTP — aí o trigger é outra atividade).
5. A seção **Testes automatizados** define o mínimo do slice; a atividade que introduz a regra **entrega** o T* correspondente (não deixar todos os testes só numa B* final).

### Frontend

1. Uma atividade ≈ uma intenção de UI (leitura da home, um CTA que chama um endpoint, um fluxo de formulário).
2. Não juntar aprovar + add + remove + detalhe na mesma F* se cada um depende de um endpoint/fluxo distinto.
3. Preferir reutilizar telas/rotas de slices anteriores (ex.: detalhe de tarefa) em atividade fina de navegação, sem recriar CRUD.

### Ordem

Documentar dependências reais, por exemplo:

```text
B1 → B2 → B3 → B4 → …
       ↘
        F1 → F2 → …
```

Indicar a partir de qual B* o front pode começar.

---

## Conteúdo de qualidade

- Tipos alinhados ao projeto (`UUID`, `Instant`, `LocalDate`, enums nomeados).
- Regras de negócio **fechadas** no texto (não “decidir na implementação”).
- API: paths, métodos, erros relevantes (`401`/`404`/`409`), paginação/filtros se existirem.
- Soft delete / restore / unicidade: copiar o espírito dos planos já feitos no repo.
- Sem cache (regra do projeto).
- Front descreve fluxo, não pixel-perfect.

Se pesos, status ou edge cases estiverem abertos: **pergunte** (lista curta) e só então grave em “Decisões fechadas”.

---

## Fluxo de trabalho sugerido

1. Confirmar posição na fila (`ordem_execucao/docs.md`) e dependências.
2. Rascunhar escopo + fora de escopo + entities + endpoints em bullet (ou receber rascunho do usuário).
3. Criticar / perguntar (inconsistências, API, segurança, extremos) — não redesenhar tudo de uma vez.
4. Com respostas, escrever o `plans/<nome>.md` completo.
5. Atualizar `ordem_execucao/docs.md`: status, ordem, dependências, “próximo passo”; se um slice absorver outro (ex. aprovação dentro do planner), remover ou anotar o plano absorvido.
6. Não commitar (salvo pedido explícito do usuário).

---

## Anti-padrões

- Plano que é só “fazer o CRUD” sem campos, regras nem endpoints.
- Uma B* com entities + motor + três endpoints.
- Plano **sem** `## Testes automatizados` ou com texto vago (“cobrir com testes”).
- “Me ajude a decidir X” deixado aberto dentro do plano final.
- Misturar execução/XP/histórico no mesmo arquivo sem necessidade.
- Inventar padrões de API diferentes dos slices anteriores sem decisão explícita.
- Implementar código “já que estamos aqui”.

---

## Referências no repo

| Exemplo | Por quê |
|---------|---------|
| `plans/usuario.md` | Slice auth mínimo, decisões + B/F enxutos |
| `plans/tarefa.md` | CRUD + job + regras de status |
| `plans/capacidade.md` | Duas entities + consulta resolvida |
| `plans/planner.md` | Atividades finas + seção de testes obrigatórios |
| `ordem_execucao/docs.md` | Fila e dependências |
| `.agents/revisao_plano/SKILL.md` | Só crítica/perguntas, sem implementar |
