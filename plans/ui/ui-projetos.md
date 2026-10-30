# Feature — UI Projetos, vínculo, reserva na capacidade e contexto no plano

Issue: [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40) (UI/UX)

Escopo: telas/fluxos para projetos; associar/desvincular tarefas; filtrar por projeto; editar reservas na grade de capacidade; mostrar projeto no card do bloco; explicar reserva vs tempo livre; estados de loading/erro/empty alinhados ao design system.  
Fora deste slice: novos endpoints além dos de `projetos.md` / `github-link.md`; OAuth GitHub; calendário externo; redesign geral da shell.

Dependências: `projetos/projetos.md`; `ui-capacidade-visual.md`, `ui-lista-tarefas.md`, `ui-home-agenda.md` / `ui-revisao-rascunho.md`, `ui-estados-feedback.md`, `ui-layout-nav.md`. Idealmente após (ou junto de) atividades F* mínimas do plano de negócio. Link GitHub: `projetos/github-link.md` (campo/link nos forms se o slice GitHub já estiver feito; senão omitir até lá).

---

## Entities

Nenhuma nova no front. Consumir APIs de `/projetos`, `/tarefas`, `/capacidades`, `/planos`.

---

## Back-end

Sem endpoints novos. Garantir proxy `/projetos` (ver `API_PROXY.md`) se ainda não aplicado no slice de negócio.

---

## Front-end

1. **Nav** — item “Projetos” (desktop + bottom bar) → rota autenticada `/projetos`.
2. **Lista de projetos** — cards/linhas: nome, descrição curta, status; ações arquivar / reativar; empty com CTA criar; skeleton.
3. **Criar / editar projeto** — formulário (nome, descrição; `githubIssueUrl` se `github-link.md` feito).
4. **Tarefas** — no form e quick-add (se couber sem poluir): select de projeto `ATIVO`; botão **Desvincular** quando houver vínculo; na lista, filtro/chip por projeto + query string (`projetoId`) no espírito de `ui-lista-tarefas.md`.
5. **Capacidade** — na grade Seg–Dom, por dia: seção/linha “Reservas por projeto” (minutos); total reservado vs horas do template; erro 409 em toast/inline; após salvar, se houver proposta, CTA “Revisar proposta” (não auto-gera aprovação).
6. **Home / revisão / agenda** — no card do bloco: nome do projeto quando `projetoId` vier no payload (enrich do back; se bloco só tiver `tarefaId`, usar nome já na resposta do plano ou buscar sem N+1 no client — preferir enrich no DTO do plano neste ciclo de UI só se o back já expuser; senão chip só na lista de tarefas até o back enriquecer bloco — **decisão:** no slice de negócio F5 / aqui, exigir `projetoNome` no bloco ou na tarefa embutida do plano).
7. **Explicação** — texto curto na capacidade e no plano do dia: “Xh reservadas ao projeto Y; o restante segue a prioridade geral.” Sem jargão de motor.
8. Viewport estreita usável; tokens do design system; sem cards decorativos desnecessários — cards só onde há interação de lista/form.
9. A11y: foco, labels, confirmação ao arquivar se não houver desfazer trivial.

---

## Decisões fechadas (revisão)

1. UI dedicada em `plans/ui/`; domínio em `plans/negocio/projetos/`.
2. Reserva editada na **mesma experiência** da capacidade semanal (não tela isolada obrigatória).
3. Desvincular é ação explícita (botão), não só limpar select sem feedback.
4. Mudança de reserva sugere revisão de proposta; não aprova sozinha.
5. Link GitHub só se o plano `github-link.md` estiver disponível.
6. Issue #40 (UI).

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Lista renderiza projetos e arquivar chama API | F1 | CRUD visível |
| T2 | Filtro `projetoId` na URL da lista de tarefas | F2 | Aceite filtro |
| T3 | Desvincular envia PATCH com `projetoId: null` | F2 | Aceite desvínculo |
| T4 | Grade capacidade mostra soma reservas ≤ horas; simula 409 | F3 | Aceite reserva |
| T5 | Card do bloco exibe nome do projeto quando presente | F4 | Aceite home/agenda |

Se harness front ainda frágil: priorizar T2–T4 com Testing Library onde já houver padrão (`CapacidadePage`, lista de tarefas).

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Lista + form projetos + nav** | Rota `/projetos`, CRUD, arquivar/reativar; **T1**; checar proxy |
| **F2** | **Vínculo e filtro em tarefas** | Select, desvincular, query `projetoId`; **T2–T3** |
| **F3** | **Reservas na grade de capacidade** | UI Seg–Dom + erros + CTA proposta; **T4** |
| **F4** | **Projeto no plano + copy da reserva** | Chip no bloco; texto reserva vs livre; **T5** |
| **F5** | **GitHub na UI** (se `github-link.md` feito) | Campo + link externo nos forms/detalhe |

### Ordem

```text
F1 → F2 → F3 → F4 → F5
```

F5 só após B2 de `github-link.md`. F3 depende de reservas no back (B12+ de `projetos.md`).
)
