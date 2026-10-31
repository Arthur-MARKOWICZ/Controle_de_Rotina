# Feature — UI v2: telas secundárias (6º slice do épico `ui-v2-epico.md`)

Escopo: aplicar o visual v2 e os padrões de interação do épico em **Calendário**, **Histórico**, **Perfil**, **Login/Cadastro** e na **criação de projeto** (drawer sobre `/projetos`).  
Fora deste slice: hub `/projetos/:id`, paginação e cards de projeto (continuam em `ui-projetos-experiencia.md`, #46); regras de domínio; endpoints.  
Dependências: `ui-v2-fundacao-visual.md`, `ui-v2-shell-nav.md` (`PageHeader`), `ui-v2-tarefas-drawer.md` (primitive `Drawer`); `ui-historico-grafico.md`, `ui-auth-onboarding.md`, `ui-projetos.md` (feitos); `calendario/calendario.md` (UI de calendário existente).

---

## Conceito

Essas telas têm pouca interação nova; o ganho é **consistência**: mesmo cabeçalho, mesmos estados de carregamento, mesmos controles. Onde ainda há “Carregando…” em texto solto ou páginas de formulário com visual de login, troca-se pelos padrões do épico.

---

## Front-end

### Calendário (`/calendario`)

1. `PageHeader` com título + controles à direita: segmented **Semana/Mês**, navegação ‹ Hoje › com rótulo do período.
2. “Carregando calendário…” → skeleton da grade na primeira carga; trocas de período mantêm a grade esmaecida (`aria-busy`) + guarda de corrida.
3. Grade: dia atual destacado (`--accent-soft` + número em `--accent`); blocos como pílulas com cor de prioridade (ponto + texto, não só cor); prazos com ícone `Flag`; sobrecarga com ícone `WarningCircle` + rótulo acessível (já existe o `aria-label`).
4. Mobile (< 640px): visão semana vira lista por dia (sem grade de 7 colunas espremida).
5. Clicar num bloco/prazo abre a tarefa no drawer (`/tarefas/:id`).

### Histórico (`/historico`)

6. `PageHeader` (já aplicado no slice 2) + segmented Dia/Semana e navegação de período no mesmo padrão do Calendário.
7. Cards de métrica (tarefas, blocos, tempo, XP) em grid 2×2/4×1 com número grande, rótulo e variação vs período anterior com seta (`ArrowUp`/`ArrowDown`) + texto (“+12% vs semana passada”), cor **e** sinal.
8. `GraficoXpSemana` restilizado com tokens (barras `--accent`, dia atual `--cta`), eixo legível, tabela/descrição acessível já existente mantida.

### Perfil (`/perfil`)

9. Sai do layout `auth-page`/`auth-panel`: página normal do shell com `PageHeader`, seções em cards (Conta, Aparência — tema, Progresso — XP detalhado).

### Login / Cadastro

10. Layout em duas colunas no desktop (≥ 1024px): à esquerda painel de marca (ícone, nome, uma frase do valor do produto — “Diga o que precisa fazer; o plano do dia sai pronto.”) sobre `--accent`; à direita o formulário. Mobile: só o formulário com a marca no topo.
11. Campos com `autocomplete` corretos (`email`, `current-password`, `new-password`), mostrar/ocultar senha (já existe `CampoSenha`) com ícone `Eye`/`EyeSlash`, erros inline por campo e foco no primeiro campo com erro ao enviar. Colar senha permitido.
12. Botão de envio com estado ocupado e sem bloquear edição dos campos após erro.

### Projetos — criação em drawer

13. `/projetos/nova` vira rota filha de `/projetos` que abre o `Drawer` (mesmo padrão de tarefas); ao criar, navega para o hub `/projetos/:id` (decisão já fechada em #46). `/projetos/:id` continua página (hub).

---

## Decisões fechadas (revisão)

1. Calendário e Histórico compartilham o mesmo controle de período (segmented + ‹ Hoje ›) — componente `layout/PeriodoNav.tsx`.
2. Variações no Histórico sempre com sinal/seta + texto, nunca só cor.
3. Perfil deixa o visual de autenticação.
4. Login/Cadastro com painel de marca só no desktop.
5. Projeto: só a **criação** vai para drawer; hub e edição seguem `ui-projetos-experiencia.md`.
6. Sem endpoint novo.

---

## Testes automatizados

Harness: testes existentes de `CalendarioPage`, `HistoricoPage`, `PerfilPage`, `LoginPage`, `CadastroPage`, `ProjetosPage`/`ProjetoFormPage` (adaptados).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `PeriodoNav`: ‹/›/Hoje mudam o período e expõem rótulo acessível; trocas rápidas aplicam só a última resposta (Calendário) | F1 | Componente compartilhado + corrida |
| T2 | Calendário: clicar num bloco navega para `/tarefas/:id` | F1 | Ponte com o drawer |
| T3 | Histórico: variação negativa mostra seta para baixo **e** texto com sinal | F2 | Não depender de cor |
| T4 | Perfil não monta `main.auth-page` e mantém troca de tema e logout | F3 | Regressão |
| T5 | Login: erro de credencial foca o primeiro campo inválido; inputs têm `autocomplete` corretos | F4 | A11y de autenticação |
| T6 | `/projetos/nova` abre drawer sobre a lista; criar navega para `/projetos/:id` | F5 | Padrão de criação |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Calendário v2** | `PeriodoNav`, skeleton/esmaecido, grade, lista mobile, clique → drawer; **T1, T2** |
| **F2** | **Histórico v2** | `PeriodoNav`, cards de métrica com variação, gráfico com tokens; **T3** |
| **F3** | **Perfil v2** | Layout do shell, seções em cards; **T4** |
| **F4** | **Login/Cadastro v2** | Duas colunas, autocomplete, erros inline/foco; **T5** |
| **F5** | **Drawer de criação de projeto** | Rota filha `/projetos/nova` com `Drawer`; **T6** |

### Ordem

```text
F1 → F2
F3, F4 independentes
F5 após ui-v2-tarefas-drawer (F1)
```
