# Feature — Experiência de /projetos (lista + form + hub leve)

Issue: [#46](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/46)

Escopo: polir UI/UX das rotas `/projetos`, `/projetos/nova` e `/projetos/:id` — form alinhado ao domínio (não ao login), cards acionáveis, paginação, empty de arquivados, hub leve no detalhe com CTAs para tarefas filtradas e capacidade.  
Fora deste slice: shell / bottom bar / Home / calendário; regras do planner; OAuth GitHub; campo `githubIssueUrl` (plano `projetos/github-link.md` ainda não entregue); contagem de tarefas no card (API de projeto não devolve); endpoints novos; tela isolada de reservas (continua em Capacidade).

Dependências: `ui-projetos.md` (feito); APIs existentes de `/projetos` e filtro `projetoId` em `/tarefas`; `ui-estados-feedback.md` (toast, confirm, skeleton); padrão de paginação de `TarefasPage`.

---

## Conceito

`ui-projetos.md` entregou o CRUD mínimo e os cruzamentos (vínculo, reserva, chip). Este slice transforma `/projetos` de “lista admin” em **ponto de partida do esforço**: da lista ou do detalhe o usuário chega às tarefas do projeto e à reserva de tempo, sem redesenhar o app.

---

## Entities

Nenhuma nova. Consumir `ProjetoResponse` / `PageProjeto` já existentes (`projetoApi.ts`).

---

## Back-end

Sem endpoints novos e sem mudança de contrato.  
Paginação já existe em `GET /projetos` (`page`, `size`, `totalPages`, `totalElements`).  
Filtro `GET /tarefas?projetoId=` já existe.

Sem proxy novo (prefixo `/projetos` já registrado).

---

## Front-end

1. **Form fora do visual de login** — `/projetos/nova` e o form em `/projetos/:id` usam layout da área Projetos (`projetos-page` / classes em `projetos.css`), **não** `auth-page` / `auth-panel` / `auth-form`. Tokens e `.field` / `.btn` do design system.
2. **Loading** — Skeleton no carregamento do detalhe/form (reusar variante existente, ex. `tarefas-lista`, ou acrescentar variante fina `projeto-form` só se o salto visual justificar; não texto solto “Carregando projeto…”).
3. **Toast de sucesso** — ao criar e ao salvar: toast + navegação (criar → lista ou detalhe; salvar → permanece no hub ou volta à lista — **decisão:** criar redireciona para `/projetos/:id` do criado; salvar permanece no hub com toast).
4. **Card acionável (lista)** — além de Arquivar/Reativar:
   - ação explícita **Editar** (ou abrir detalhe) — não só o título clicável;
   - link **Ver tarefas** → `/tarefas?projetoId={id}`;
   - link **Reservar tempo** → `/capacidade` (sem hash obrigatório; reservas já vivem na grade).
5. **Header da lista** — CTA secundário ou texto-link para Capacidade (o subtítulo promete reserva; precisa de caminho óbvio).
6. **Empty arquivados** — mensagem + CTA para voltar à visão Ativos (chip / limpar `?status=ARQUIVADO`).
7. **Paginação** — espelhar `TarefasPage`: `page` na query string (`useSearchParams`), controles Anterior / Próxima quando `totalPages > 1`; `size` 20. Visão `status` + `page` na URL.
8. **Hub leve em** `/projetos/:id` — mesma rota, página enriquecida (não só form):
   - cabeçalho: nome, badge de status, descrição;
   - atalhos: Ver tarefas (`/tarefas?projetoId=`), Reservar tempo (`/capacidade`);
   - ações de ciclo: Arquivar / Reativar (confirm no arquivar, como na lista);
   - seção de edição: nome + descrição (PATCH);
   - sem listar tarefas inline (evita N+1 / segunda lista); link basta;
   - sem UI de reservas aqui.
9. **Título da aba** — detalhe pode permanecer “Editar projeto” ou passar a “Projeto · …” se o hub deixar de ser só form; alinhar `documentTitle.ts`.
10. Viewport estreita: ações do card empilháveis; sem tabela.

---

## Decisões fechadas (revisão)

1. Escopo só rotas `/projetos*` + links de saída; sem mexer em Home/nav global.
2. **Hub leve incluso** neste slice (não opcional): `/projetos/:id` = resumo + atalhos + form, não form isolado.
3. **GitHub fora** até `github-link.md` / campo na API existirem.
4. **Sem contagem** de tarefas no card (contrato atual não traz; não inventar endpoint).
5. Paginação = **Anterior/Próxima +** `page` **na URL**, padrão da lista de tarefas — não “carregar mais”.
6. CTA Capacidade = `/capacidade` puro (sem âncora nova neste slice).
7. Criar projeto → navegar para o **hub** `/projetos/:id` com toast; salvar no hub → toast e permanece.
8. Sem backend novo; sem cache.
9. Issue #46; não reabre o escopo de #40 / `ui-projetos.md` (já feitos).

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Harness front já existe (`ProjetosPage.test.tsx`, Vitest + Testing Library).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Form create/edit **não** monta `main.auth-page` (usa layout projetos) | F1 | Critério aceite #46 |
| T2 | Toast de sucesso ao criar e ao salvar (mock `useToast` / provider) | F1 | Critério aceite |
| T3 | Card expõe link/botão Editar e link Ver tarefas com `projetoId` na URL | F2 | Critério aceite |
| T4 | Há caminho para `/capacidade` a partir da lista (header e/ou card) | F2 | Critério aceite |
| T5 | Empty arquivados oferece voltar a Ativos | F2 | Empty útil |
| T6 | Com `totalPages > 1`, muda `page` na URL e chama API com a página | F3 | Não “sumir” projetos |
| T7 | Hub `/projetos/:id` mostra atalho Ver tarefas e ações arquivar/reativar | F4 | Hub ≠ só form |
| T8 | Suites existentes de `ProjetosPage` (lista/arquivar/visão) continuam verdes | F2–F3 | Regressão |

Loading com Skeleton: assert `aria-busy` / role status no detalhe (pode ir em F1 ou F4).

---

## Atividades

### Frontend

| #      | Atividade                    | Escopo                                                                                                                                               |
| ------ | ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **F1** | **Form no layout Projetos**  | Tirar `auth-`* de `ProjetoFormPage`; CSS em `projetos.css`; Skeleton no load; toast criar/salvar; redirect pós-criar para `/projetos/:id`; **T1–T2** |
| **F2** | **Cards e descoberta**       | Editar explícito; Ver tarefas; Reservar tempo; CTA Capacidade no header; empty arquivados → Ativos; **T3–T5**, **T8** parcial                        |
| **F3** | **Paginação da lista**       | `page` (+ `status`) na URL; Anterior/Próxima; recarregar com `listarProjetos({ page })`; **T6**                                                      |
| **F4** | **Hub leve** `/projetos/:id` | Cabeçalho + atalhos + arquivar/reativar + form; `documentTitle` se necessário; testes de `ProjetoFormPage` / hub; **T7**                             |

### Ordem

```text
F1 → F4
F1 → F2 → F3
```

F1 desbloqueia o visual do form; F4 enriquece a mesma rota de edição. F2/F3 são independentes de F4 após a lista estável — podem seguir em paralelo a F4 depois de F1 se o form compartilhado já estiver estável.

Preferência serial segura:

```text
F1 → F2 → F3 → F4
```
