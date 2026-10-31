# Feature — UI v2: Hoje fluida (3º slice do épico `ui-v2-epico.md`)

Escopo: tela **Hoje** (`/`) — carregamento sem cascata, ações otimistas com rollback, menos modais, bloco ativo em destaque, lista de blocos em formato de linha do tempo, extração da lógica de `HomePage.tsx` para um hook.  
Fora deste slice: regras do planner/execução/XP (backend intacto); Calendário; Capacidade (só o atalho “ajustar hoje” já existente é restilizado); novos endpoints (ex.: `DELETE` de bloco devolvendo o plano — **não** muda neste slice).  
Dependências: `ui-v2-fundacao-visual.md`, `ui-v2-shell-nav.md` (Nova tarefa global + `QuickAddContext`); `ui-home-agenda.md`, `ui-revisao-rascunho.md`, `ui-xp-feedback.md` (feitos — comportamento funcional preservado).

---

## Conceito

A Home é onde o usuário passa o dia. Hoje ela “trava” por três motivos medidos no código:

1. **Cascata de carga** — `carregarDia` faz 4 rodadas sequenciais (plano+capacidade → reservas+projetos → datas da proposta → execuções).
2. **Servidor-primeiro em tudo** — iniciar/concluir/abortar esperam o `POST` **e** chamam `listarExecucoes` de novo, embora a API já devolva `ExecucaoResponse`; remover bloco espera `DELETE` + `buscarPlano`.
3. **Re-render global a cada segundo** — o `setInterval` de 1s que alimenta o cronômetro vive em `HomePage`, re-renderizando a página inteira (lista, banners, formulário) enquanto há bloco em andamento.

### Modelo de atualização otimista (regra do slice)

Para cada mutação: (a) aplicar o estado esperado localmente e marcar o item como `pendente`; (b) chamar a API; (c) em sucesso, substituir pelo que a API devolveu (ou reconciliar com uma leitura silenciosa quando a API devolve `void`); (d) em erro, **restaurar o snapshot anterior** e mostrar toast de erro com ação “Tentar de novo”. Nada é guardado entre telas (sem cache).

| Ação | Otimista | Fonte da verdade depois |
|------|----------|-------------------------|
| Iniciar bloco | item → `EM_ANDAMENTO`, `iniciadoEm = agora` | `ExecucaoResponse` do `POST` |
| Concluir bloco | item → `CONCLUIDO`; XP otimista **não** (XP vem da resposta) | `ExecucaoResponse` + `xp` (toasts de XP como hoje) |
| Abortar bloco | após confirmação, item → `ABORTADO` | `ExecucaoResponse` |
| Remover bloco (rascunho) | bloco some da lista + toast “Removido · Desfazer” | `buscarPlano` silencioso (sem skeleton) após o `DELETE` |
| Desfazer remoção | `adicionarBloco(planoId, tarefaId)` | `PlanoResponse` devolvido |
| Adicionar tarefa ao rascunho | linha “adicionando…” no fim da lista | `PlanoResponse` devolvido |
| Aprovar | botão em estado ocupado; lista mantém-se visível | `PlanoResponse` + `listarExecucoes` (única leitura extra, necessária para ids de execução) |
| Gerar nova versão | lista atual fica visível esmaecida (`aria-busy`) | `PlanoResponse` (+ diff “novo/removido” como hoje) |

---

## Front-end

1. **Hook `home/usePlanoDoDia.ts`** — concentra estado e handlers hoje em `HomePage.tsx` (plano, capacidade, execuções, proposta, reservas, flags de ação). `HomePage` vira composição de componentes; nenhuma regra nova de domínio. Funções puras novas (aplicar otimista / rollback / mesclar resposta) em `home/otimista.ts` testáveis sem React.
2. **Carga em 2 rodadas**  
   - Rodada 1 (paralela): `buscarPlano`, `consultarCapacidadeResolvida`, e — **sem bloquear a renderização** — `listarReservas` + `listarProjetos` para a explicação de reservas (preenche depois; falha silenciosa como hoje).  
   - Rodada 2 (paralela, só o que depende do plano): `listarExecucoes` se `APROVADO`; `listarDatasDaProposta` se houver `propostaId`.  
   - O plano renderiza ao fim da rodada 1 + execuções (quando aprovado); datas da proposta e reservas entram quando chegarem, em slots com altura reservada (sem empurrar conteúdo).
3. **Guarda de corrida** — toda carga do dia usa um id de requisição; resposta de requisição antiga é descartada (troca rápida de dia da proposta, gerar duas vezes).
4. **Troca de dia da proposta** — segmented control com datas legíveis (`ter 04/11`, não `11-04`); conteúdo atual fica visível com `aria-busy` + opacidade reduzida até o novo dia chegar (sem skeleton de tela inteira). Skeleton só na **primeira** carga.
5. **Cronômetro isolado** — o `setInterval` sai de `HomePage` e vai para dentro de `BlocoAtivo` (estado local); a página não re-renderiza a cada segundo.
6. **Bloco ativo em destaque** — card no topo com título, tempo restante grande (tabular-nums), barra de progresso do tempo decorrido, botões **Concluir** (primário, `Check`) e **Abortar** (ghost/danger, `Stop`). Quando estoura o tempo, mostra “+N min” em `--warning` (não vermelho piscando).
7. **Linha do tempo de blocos** — lista ordenada com marcador de ordem/estado (círculo numerado → ícone `Check` quando concluído, `Play` quando em andamento), título, chip de projeto, prioridade (ponto colorido **+** rótulo), duração, prazo quando rascunho; concluídos esmaecidos; ação primária do bloco à direita (Iniciar). Justificativa do rascunho como texto secundário, não bloco separado.
8. **Resumo do dia no cabeçalho** — `PageHeader` com data por extenso + badge de status do plano; abaixo, resumo compacto: “3 de 6 blocos · 2h10 de 4h” com barra de capacidade (`CapacidadeBar` restilizada); conflito como banner com ícone `WarningCircle` e as ações já existentes (remover menor prioridade / aumentar capacidade / manter assim).
9. **Aprovar sem modal** — o botão passa a carregar o resumo (“Aprovar 5 blocos · 3h20”) e aprova direto; a informação que o modal mostrava já está visível no resumo. **Continuam com confirmação**: abortar execução, aceitar proposta (substitui plano aprovado) e descartar proposta.
10. **Erros de ação viram toast** — `acaoErro` (InlineAlert no topo do card) é substituído por toast de erro com “Tentar de novo”; `InlineAlert` permanece só para erro de **carregamento** do dia.
11. **Dia concluído** — card de celebração discreto: ícone `Lightning` em `--cta`, “+N XP hoje”, CTA secundário “Ver histórico”. Sem confete/animação longa.
12. **Empty state** — sem plano: ilustração por ícone grande (`SunHorizon`), texto curto, CTA primário “Gerar plano de hoje” + secundário “Ajustar capacidade de hoje”; `OnboardingChecklist` restilizado (passos com check), mesma lógica de `onboardingStorage`.
13. **Adicionar tarefa ao rascunho** — `AdicionarTarefaForm` vira combobox com busca por título (lista já carregada de elegíveis; filtro local, sem nova requisição por tecla), no lugar do `<select>` puro.
14. **Responsivo** — 375px: ações do bloco viram ícone+rótulo empilhados; bloco ativo fica fixo acima da bottom bar enquanto em andamento (sem cobrir foco: `scroll-padding-bottom`).

---

## Decisões fechadas (revisão)

1. Otimismo segue a tabela do Conceito; XP **nunca** é otimista.
2. Remover bloco sem confirmação, com “Desfazer” no toast (re-adiciona via `adicionarBloco`; a posição pode mudar — aceito).
3. Aprovar plano sem modal; abortar, aceitar proposta e descartar proposta mantêm confirmação.
4. Carregamento inicial em no máximo 2 rodadas; reservas não bloqueiam a renderização.
5. Skeleton só na primeira carga; recargas posteriores mantêm conteúdo visível com `aria-busy`.
6. Erros de ação → toast com “Tentar de novo”; erro de carga → `InlineAlert`.
7. Cronômetro vive em `BlocoAtivo`.
8. Sem endpoint novo; `DELETE` de bloco continua `void` e é reconciliado com `buscarPlano` silencioso.

---

## Testes automatizados

Harness: Vitest + Testing Library (`HomePage.test.tsx` já existe e será adaptado).

### Unitários (sem React)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `otimista.ts`: aplicar iniciar/concluir/abortar/remover gera o estado esperado e o rollback devolve exatamente o snapshot | F1 | Base de todas as ações otimistas |
| T2 | Mesclar `ExecucaoResponse` no item certo (por `bloco.id`) sem tocar nos demais | F1 | Evita “piscar” o item errado |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T3 | Carga inicial: `buscarPlano` e `consultarCapacidadeResolvida` chamados juntos; falha de `listarReservas` não impede o plano de aparecer | F2 | Fim da cascata |
| T4 | Resposta antiga de troca de dia é descartada (duas trocas rápidas com promessas resolvidas fora de ordem) | F2 | Corrida |
| T5 | Iniciar: item mostra “Em andamento” **antes** de a promessa do `POST` resolver; `listarExecucoes` **não** é chamado de novo | F3 | Critério “não travado” |
| T6 | Iniciar com erro: item volta a “Não iniciado” e aparece toast de erro com “Tentar de novo” | F3 | Rollback |
| T7 | Concluir: toasts de XP vêm da resposta (regressão de `ui-xp-feedback`); sem `listarExecucoes` extra | F3 | XP não pode regredir |
| T8 | Abortar ainda pede confirmação; cancelar não chama a API | F3 | Ação irreversível |
| T9 | Remover bloco some na hora; “Desfazer” chama `adicionarBloco` com a tarefa removida; erro no `DELETE` restaura o bloco | F4 | Otimismo + undo |
| T10 | Aprovar chama `aprovarPlano` sem abrir dialog; aceitar/descartar proposta ainda confirmam | F4 | Decisão 3 |
| T11 | `BlocoAtivo` com fake timers decrementa o tempo e mostra “+N min” ao estourar | F5 | Cronômetro isolado |
| T12 | Combobox de adicionar filtra elegíveis por texto sem nova requisição | F6 | Sem chamada por tecla |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Extrair `usePlanoDoDia` + `otimista.ts`** | Mover estado/handlers sem mudar comportamento; funções puras de otimismo; testes atuais da Home verdes; **T1, T2** |
| **F2** | **Carga em 2 rodadas + guarda de corrida** | Reorganizar `carregarDia`; slots com altura reservada; dia da proposta sem skeleton total; **T3, T4** |
| **F3** | **Execução otimista** | Iniciar/concluir/abortar otimistas com rollback; erros de ação → toast; **T5–T8** |
| **F4** | **Rascunho otimista + aprovar sem modal** | Remover com desfazer + reconciliação silenciosa; adicionar com linha pendente; aprovar direto; **T9, T10** |
| **F5** | **Bloco ativo + linha do tempo + resumo** | `BlocoAtivo` com cronômetro local; `BlocoCard` em timeline; cabeçalho com `PageHeader` + resumo/capacidade; banner de conflito; dia concluído; empty/onboarding; **T11** |
| **F6** | **Combobox de adicionar + responsivo** | `AdicionarTarefaForm` com busca local; ajustes 375px/bloco ativo fixo; **T12** |

### Ordem

```text
F1 → F2 → F3 → F4
F1 → F5 → F6
```

F1 primeiro (refator sem mudança de comportamento, protegido pelos testes atuais). F5 é visual e pode correr em paralelo a F2–F4 depois de F1.
