# Feature — UI: capacidade antes do primeiro plano e plano vazio (web + mobile)

Origem: auditoria Impeccable 2026-11-24 (P0 "primeiro plano nasce vazio"), `.impeccable/audit/2026-11-24T14-03-45Z__project.md`.

Escopo: (1) guarda "sem capacidade" antes de gerar plano, no web e no mobile, com o preset "Dias úteis 4h" em um toque; (2) "Aprovar" desabilitado e explicado quando o plano tem 0 blocos; (3) tratar o novo `409 Plano vazio`.  
Fora deste slice: tela completa de Capacidade no mobile; XP no mobile; redesenho do banner de conflito; mudar a IA "tarefas primeiro" (`ui-ia-nav-tarefas-primeiro.md` continua valendo); endpoints novos.  
Dependências: `capacidade-inicial-plano-vazio.md` (B2 para o 409), `ui-capacidade-visual.md`, `ui-v2-capacidade.md`, `ui-planner-sob-demanda.md`, `ui-auth-onboarding.md`, app mobile (`openspec/changes/app-mobile-android/`).

---

## Conceito

**"Sem capacidade configurada"** (mesma regra que `OnboardingChecklist.capacidadeConfigurada` já usa): nenhuma `CapacidadeSemanal` ativa com `horas > 0` **e** a capacidade resolvida do dia selecionado igual a 0.

- Contas novas não caem aqui (B1 do plano de negócio cria seg–sex 4h). A guarda cobre contas antigas e quem zerou tudo.
- Fim de semana com template configurado **não** dispara a guarda: o planner é multi-dia e aloca nos próximos dias úteis.

**Guarda:** se "sem capacidade configurada", o pedido de gerar (botão do estado vazio, item do onboarding, "Organizar meu dia" quando cai em `/plano`) **não** chama `POST /planos/gerar`. No lugar, abre o passo de capacidade:

- Título: "Quanto tempo você tem por dia?"
- Ação primária: **"Usar dias úteis 4h"**. Cria seg–sex 4h (`POST /capacidades/semanal` para dias sem registro, `PATCH` para dias existentes com 0) e, no sucesso, gera o plano automaticamente.
- Secundária: **"Só hoje"**, que reaproveita o stepper de `AjustarCapacidadeHoje` (override do dia) e depois gera.
- Link: "Configurar a semana" → `/capacidade` (web). No mobile não existe tela completa: o sheet tem stepper por dia.
- Fechar sem escolher: nada é gravado, nada é gerado.

**Plano com 0 blocos** (fim de semana, tarefas que não cabem etc.):

- O botão primário fica desabilitado com o texto "Nada para aprovar", e uma linha explica: "Nenhum bloco coube. Ajuste a capacidade ou adicione tarefas." Some o rótulo "Aprovar 0 blocos · 0min".
- Se a API ainda assim devolver `409 Plano vazio` (corrida ou cliente antigo): mostrar o `detail` como erro inline do plano, sem toast genérico.

---

## Front-end

### Web (`frontend/frontend-web`)

- `home/usePlanoDoDia.ts`: `handleGerar` usa a capacidade resolvida do dia, que já é carregada. Só quando ela é 0, chama `listarCapacidadesSemanais()` (que hoje não é carregada aqui) para decidir entre gerar e abrir o passo. O caminho normal não ganha requisição extra. Se essa leitura falhar, gera mesmo assim (comportamento atual) em vez de travar.
- Componente novo `home/CapacidadeInicialDialog.tsx` sobre o `ConfirmDialog`/`Drawer` existente: foco no título ao abrir, `Esc` fecha e o foco volta ao botão de origem.
- `home/OnboardingChecklist.tsx`: o item 3 ("Gerar primeiro plano") usa o mesmo `onGerarPlano`, portanto passa pela guarda. O texto do item 2 não muda.
- `pages/HomePage.tsx`: com `rotuloAprovar` e `plano.blocos.length === 0`, o botão fica desabilitado com `aria-describedby` apontando para a linha explicativa.
- Erros: `lerErroApi` já lê o ProblemDetail; mapear o title `Plano vazio` para o `InlineAlert` do plano.
- Sem prefixo de API novo (`/capacidades` e `/planos` já estão no proxy).

### Mobile (`mobile/shared`)

- `api/RotinaApi.kt`: `capacidadesSemanais()`, `criarCapacidadesSemanais(itens)`, `atualizarCapacidadeSemanal(id, horas)`, `capacidadeResolvida(dataDe, dataAte)`, com modelos em `Modelos.kt` seguindo `contract/openapi.json`.
- `estado/HojeViewModel.kt`:
  - `gerar()` verifica "capacidade configurada" e, se faltar, emite `EstadoHoje.SemPlano(pedindoCapacidade = true)` em vez de chamar a API;
  - `usarDiasUteis()` grava seg–sex 4h e gera;
  - `salvarSemana(horasPorDia)` grava o stepper e gera;
  - `aprovar()` não chama a API com 0 blocos.
- `ui/HojeTela.kt`:
  - `ModalBottomSheet` "Quanto tempo você tem por dia?" com botão "Usar dias úteis 4h" e 7 linhas (Seg…Dom) com stepper de horas (−/+ com alvos ≥ 48dp, `contentDescription` "Diminuir horas de segunda" etc.) e "Salvar e gerar";
  - "Aprovar plano" fica desabilitado com 0 blocos e mostra o texto explicativo;
  - O texto "Ajustes finos … estão na versão web" continua, mas não se aplica à capacidade.
- Cores: usar `MaterialTheme.colorScheme` (sem hex novo).

---

## Decisões fechadas (revisão)

1. A guarda usa a regra de `capacidadeConfigurada` (template com algum dia > 0) somada à resolvida do dia; fim de semana com template não bloqueia.
2. A guarda substitui a chamada de gerar; não gera plano vazio "para mostrar".
3. "Usar dias úteis 4h" gera o plano logo depois de salvar (um toque até o plano). Isso é uma exceção consciente a `ui-capacidade-visual.md` decisão 5 ("não auto-gerar ao mudar capacidade"), restrita a esse passo, que nasceu de um pedido explícito de gerar.
4. Aprovar com 0 blocos fica desabilitado no web e no mobile, e o 409 do back é a rede de segurança.
5. Mobile ganha só o sheet de capacidade dentro do Hoje; a tela completa fica fora.

---

## Testes automatizados

Testes que **não podem faltar**. Regra 1 + 3 + 3 por unidade (`usePlanoDoDia`, `CapacidadeInicialDialog`, `HojeViewModel`, `RotinaApi`).

### Front web (Vitest + Testing Library; mocks com `vi.mocked(...)`)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Sem capacidade → clicar "Gerar plano de hoje" abre o diálogo e **não** chama `gerarPlano` | F2 | Raiz do P0 na UI |
| T2 | "Usar dias úteis 4h" chama `POST` para os dias sem registro e `PATCH` para o dia existente com 0, e então `gerarPlano` uma vez | F2 | Caminho feliz completo |
| T3 | Template com dias > 0 numa data de sábado (resolvida 0) → gera direto, sem diálogo | F2 | Anomalia: não bloquear multi-dia |
| T4 | Duplo clique em "Usar dias úteis 4h" → um único lote de gravação e uma única geração | F2 | Anomalia: chamada duplicada |
| T5 | Fechar o diálogo (Esc) → nenhuma gravação, nenhuma geração, foco volta ao botão | F2 | Anomalia: estado intacto + a11y |
| T6 | Falha 500 ao salvar capacidade → mensagem de erro no diálogo, sem geração | F2 | Erro esperado |
| T7 | Falha 409 `CapacidadeSemanalConflito` (corrida com outra aba) → relê a semana e mostra "A capacidade mudou, tente de novo" | F2 | Erro esperado |
| T8 | Item 3 do onboarding passa pela mesma guarda | F2 | Regressão do caminho do 1º acesso |
| T9 | Plano rascunho com 0 blocos → botão "Nada para aprovar" desabilitado + texto ligado por `aria-describedby` | F1 | Regra visual nova |
| T10 | `aprovar` devolve 409 `Plano vazio` → `InlineAlert` com o `detail`; o plano segue rascunho na tela | F1 | Erro esperado (title) |
| T11 | Plano com blocos segue "Aprovar N blocos · Xmin" habilitado | F1 | Regressão |

### Mobile (`:shared:jvmTest`, fakes em `estado/Fakes.kt`)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T12 | `RotinaApi` capacidade: 2xx, 400, 401→refresh, 404, 409, 500/timeout, HTML do proxy/JSON inválido | F3 | Matriz padrão de cliente de API (`AGENTS.md`) |
| T13 | `gerar()` sem capacidade → `pedindoCapacidade = true` e nenhuma chamada a `gerarPlano` | F4 | Raiz do P0 no app |
| T14 | `usarDiasUteis()` grava seg–sex 4h e gera uma vez; chamada concorrente não duplica | F4 | Caminho feliz + anomalia |
| T15 | Template configurado + dia de domingo → `gerar()` chama a API direto | F4 | Anomalia: multi-dia |
| T16 | Falha ao gravar → `Erro` com mensagem, sem geração | F4 | Erro esperado |
| T17 | `aprovar()` com 0 blocos não chama a API; o 409 `Plano vazio` do back vira mensagem | F4 | Regra nova + erro esperado |

Visual (manual, registrar no PR): web 375px e desktop, claro e escuro; mobile com fonte 200%.

---

## Atividades

### Frontend web

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Aprovar desabilitado com 0 blocos** | `HomePage` + mapeamento do 409 `Plano vazio`. Inclui T9–T11. Depende de B2 só para o teste com o 409 real. |
| **F2** | **Guarda + diálogo de capacidade inicial** | `CapacidadeInicialDialog`, `usePlanoDoDia.handleGerar`, onboarding item 3. Inclui T1–T8. |

### Mobile

| # | Atividade | Escopo |
|---|-----------|--------|
| **F3** | **Cliente de capacidade no `RotinaApi`** | 4 funções + modelos. Inclui T12. |
| **F4** | **Sheet de capacidade e aprovar vazio no Hoje** | `HojeViewModel` + `HojeTela`. Inclui T13–T17. |

### Ordem

```text
(negócio) B2 → F1
F2 (independe do back)
F3 → F4
```

F1, F2 e F3 podem andar em paralelo. Cobertura: subir os limites da catraca de front e mobile ao fechar.
